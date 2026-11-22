package com.haolo.ticketing.payments;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.payments.RefundProgressModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class RefundProgressService {

    public record BookingInfo(UUID accountId, String status, long totalAmountVnd) {}
    public record RefundAttemptInfo(UUID id, long amountVnd, String reason, String status, java.sql.Timestamp createdAt, java.sql.Timestamp settledAt) {}

    private final JdbcTemplate jdbc;

    public RefundProgressService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RefundProgressResponse getRefundProgress(DemoPrincipal principal, UUID bookingId) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        if (bookingId == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã đơn hàng không hợp lệ");
        }

        // Anti-IDOR Check: ensure booking belongs to user or user is ADMIN
        var bookings = jdbc.query(
            "SELECT account_id, status, total_amount_vnd FROM ticketing.bookings WHERE id = ?",
            (rs, rowNum) -> new BookingInfo(
                rs.getObject("account_id", UUID.class),
                rs.getString("status"),
                rs.getLong("total_amount_vnd")
            ),
            bookingId
        );

        if (bookings.isEmpty()) {
            throw new ApiException(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn hàng");
        }

        BookingInfo booking = bookings.getFirst();
        UUID ownerId = booking.accountId();
        String bookingStatus = booking.status();
        long totalAmount = booking.totalAmountVnd();

        if (!principal.hasRole("ADMIN") && !principal.accountId().equals(ownerId)) {
            throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền xem thông tin hoàn tiền của đơn hàng này");
        }

        // Query refund_attempts
        var refundRows = jdbc.query(
            "SELECT id, amount_vnd, reason, status, created_at, settled_at FROM ticketing.refund_attempts WHERE booking_id = ? ORDER BY created_at DESC LIMIT 1",
            (rs, rowNum) -> new RefundAttemptInfo(
                rs.getObject("id", UUID.class),
                rs.getLong("amount_vnd"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("settled_at")
            ),
            bookingId
        );

        String refundStatus = "PENDING";
        long refundAmount = totalAmount;
        String reason = "Hoàn tiền do đơn hàng hết hạn hoặc huỷ giữ chỗ";
        String createdAtStr = "Gần đây";
        String settledAtStr = null;

        if (!refundRows.isEmpty()) {
            RefundAttemptInfo r = refundRows.getFirst();
            refundAmount = r.amountVnd();
            reason = r.reason() != null ? r.reason() : reason;
            refundStatus = r.status();
            createdAtStr = r.createdAt() != null ? r.createdAt().toString() : createdAtStr;
            settledAtStr = r.settledAt() != null ? r.settledAt().toString() : null;
        }

        List<RefundStep> steps = new ArrayList<>();

        // Step 1: Tiếp nhận yêu cầu hoàn tiền
        steps.add(new RefundStep(
            1,
            "Tiếp nhận yêu cầu",
            "COMPLETED",
            createdAtStr,
            "Hệ thống tự động ghi nhận nghĩa vụ hoàn tiền cho giao dịch của bạn."
        ));

        // Step 2: Đối soát & Thẩm định tài chính
        if ("SETTLED".equalsIgnoreCase(refundStatus)) {
            steps.add(new RefundStep(
                2,
                "Đối soát & Phê duyệt",
                "COMPLETED",
                createdAtStr,
                "Bộ phận tài chính sàn đã hoàn tất đối soát và phê duyệt lệnh hoàn tiền."
            ));
            steps.add(new RefundStep(
                3,
                "Tiền đã hoàn về tài khoản",
                "COMPLETED",
                settledAtStr != null ? settledAtStr : "Đã hoàn tất",
                String.format("Số tiền %,d đ đã được hoàn trả về tài khoản / thẻ thanh toán ban đầu.", refundAmount)
            ));
        } else if ("APPROVED".equalsIgnoreCase(refundStatus)) {
            steps.add(new RefundStep(
                2,
                "Đối soát & Phê duyệt",
                "COMPLETED",
                createdAtStr,
                "Lệnh hoàn tiền đã được phê duyệt. Đang chuyển lệnh sang Cổng thanh toán."
            ));
            steps.add(new RefundStep(
                3,
                "Tiền về tài khoản / thẻ",
                "IN_PROGRESS",
                "Đang giải ngân",
                "Cổng thanh toán đang xử lý chuyển trả tiền (ước tính 1-3 ngày làm việc)."
            ));
        } else if ("REJECTED".equalsIgnoreCase(refundStatus)) {
            steps.add(new RefundStep(
                2,
                "Từ chối hoàn tiền",
                "REJECTED",
                createdAtStr,
                "Yêu cầu hoàn tiền bị từ chối do không thỏa mãn điều kiện đối soát."
            ));
            steps.add(new RefundStep(
                3,
                "Hoàn tiền",
                "PENDING",
                "--",
                "Giao dịch hoàn tiền không được thực hiện."
            ));
        } else {
            // REQUESTED / PENDING
            steps.add(new RefundStep(
                2,
                "Đang đối soát & Phê duyệt",
                "IN_PROGRESS",
                "Đang xử lý",
                "Ban quản trị đang tiến hành đối soát tài chính định kỳ với ngân hàng."
            ));
            steps.add(new RefundStep(
                3,
                "Tiền về tài khoản / thẻ",
                "PENDING",
                "Chờ duyệt",
                "Số tiền sẽ được tự động hoàn về thẻ/tài khoản ngay sau khi phê duyệt."
            ));
        }

        return new RefundProgressResponse(
            bookingId,
            bookingStatus,
            refundAmount,
            reason,
            refundStatus,
            steps
        );
    }
}
