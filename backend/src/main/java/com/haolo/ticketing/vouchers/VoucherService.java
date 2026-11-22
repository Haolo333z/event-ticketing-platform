package com.haolo.ticketing.vouchers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.organizer.EventSecurityService;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.vouchers.VoucherModels.*;

@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    private final JdbcTemplate jdbc;
    private final EventSecurityService securityService;

    public VoucherService(JdbcTemplate jdbc, EventSecurityService securityService) {
        this.jdbc = jdbc;
        this.securityService = securityService;
    }

    public VoucherValidationResult validateAndCalculate(UUID accountId, String code, UUID eventId, UUID ticketTypeId, long orderAmountVnd) {
        if (code == null || code.isBlank()) {
            return new VoucherValidationResult(false, null, "", "NONE", 0, 0, orderAmountVnd, "Mã giảm giá không được để trống");
        }
        String cleanCode = code.trim().toUpperCase();

        var rows = jdbc.query(
            "SELECT id, organizer_id, event_id, ticket_type_id, code, discount_type, discount_value, " +
            "min_order_amount_vnd, max_discount_amount_vnd, max_uses, used_count, valid_from, valid_to, is_active, created_at " +
            "FROM ticketing.vouchers WHERE code = ?",
            this::mapVoucherDto,
            cleanCode
        );

        if (rows.isEmpty()) {
            return new VoucherValidationResult(false, null, cleanCode, "NONE", 0, 0, orderAmountVnd, "Mã giảm giá không tồn tại");
        }

        VoucherDto voucher = rows.getFirst();
        Instant now = Instant.now();

        if (!voucher.isActive()) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Mã giảm giá đã bị tạm ngừng áp dụng");
        }
        if (now.isBefore(voucher.validFrom())) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Mã giảm giá chưa đến đợt áp dụng");
        }
        if (now.isAfter(voucher.validTo())) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Mã giảm giá đã hết hạn sử dụng");
        }
        if (voucher.usedCount() >= voucher.maxUses()) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Mã giảm giá đã hết lượt sử dụng");
        }
        if (voucher.eventId() != null && eventId != null && !voucher.eventId().equals(eventId)) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Mã giảm giá không áp dụng cho sự kiện này");
        }
        if (voucher.ticketTypeId() != null && ticketTypeId != null && !voucher.ticketTypeId().equals(ticketTypeId)) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Mã giảm giá không áp dụng cho hạng vé này");
        }
        if (orderAmountVnd < voucher.minOrderAmountVnd()) {
            return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd,
                "Đơn hàng chưa đạt giá trị tối thiểu " + String.format("%,d", voucher.minOrderAmountVnd()) + "đ");
        }

        if (accountId != null) {
            Integer usedAlready = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.voucher_redemptions WHERE voucher_id = ? AND account_id = ?",
                Integer.class,
                voucher.id(),
                accountId
            );
            if (usedAlready != null && usedAlready > 0) {
                return new VoucherValidationResult(false, voucher.id(), cleanCode, voucher.discountType(), voucher.discountValue(), 0, orderAmountVnd, "Bạn đã sử dụng mã giảm giá này cho một đơn hàng trước đó");
            }
        }

        long discountAmount = computeDiscount(voucher, orderAmountVnd);
        long finalAmount = Math.max(0, orderAmountVnd - discountAmount);

        return new VoucherValidationResult(
            true,
            voucher.id(),
            cleanCode,
            voucher.discountType(),
            voucher.discountValue(),
            discountAmount,
            finalAmount,
            "Áp dụng mã giảm giá thành công: giảm " + String.format("%,d", discountAmount) + "đ"
        );
    }

    @Transactional
    public ApplyVoucherResponse applyVoucherToBooking(UUID accountId, UUID bookingId, String code) {
        if (accountId == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (bookingId == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã đơn hàng");
        if (code == null || code.isBlank()) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã giảm giá");

        // 1. Lock booking
        var bookings = jdbc.queryForList(
            "SELECT id, account_id, event_id, ticket_type_id, status, total_amount_vnd, voucher_id, discount_amount_vnd " +
            "FROM ticketing.bookings WHERE id = ? FOR UPDATE",
            bookingId
        );
        if (bookings.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy đơn hàng");
        }
        var booking = bookings.getFirst();
        UUID owner = (UUID) booking.get("account_id");
        if (!accountId.equals(owner)) {
            throw new ApiException(403, "FORBIDDEN", "Không có quyền thao tác trên đơn hàng của người khác");
        }
        String status = (String) booking.get("status");
        if (!"PENDING_PAYMENT".equals(status)) {
            throw new ApiException(400, "INVALID_STATE", "Chỉ có thể áp dụng mã giảm giá khi đơn hàng đang chờ thanh toán");
        }
        if (booking.get("voucher_id") != null) {
            throw new ApiException(409, "VOUCHER_ALREADY_APPLIED", "Đơn hàng này đã được áp dụng mã giảm giá");
        }

        long currentTotal = ((Number) booking.get("total_amount_vnd")).longValue();
        UUID eventId = (UUID) booking.get("event_id");
        UUID ticketTypeId = (UUID) booking.get("ticket_type_id");

        // 2. Lock voucher
        String cleanCode = code.trim().toUpperCase();
        var vouchers = jdbc.query(
            "SELECT id, organizer_id, event_id, ticket_type_id, code, discount_type, discount_value, " +
            "min_order_amount_vnd, max_discount_amount_vnd, max_uses, used_count, valid_from, valid_to, is_active, created_at " +
            "FROM ticketing.vouchers WHERE code = ? FOR UPDATE",
            this::mapVoucherDto,
            cleanCode
        );
        if (vouchers.isEmpty()) {
            throw new ApiException(404, "VOUCHER_NOT_FOUND", "Mã giảm giá không tồn tại");
        }
        VoucherDto voucher = vouchers.getFirst();

        VoucherValidationResult check = validateAndCalculate(accountId, cleanCode, eventId, ticketTypeId, currentTotal);
        if (!check.valid()) {
            throw new ApiException(400, "INVALID_VOUCHER", check.message());
        }

        long discountAmount = check.discountAmountVnd();
        long newTotal = check.finalAmountVnd();

        // 3. Atomically update voucher usage & booking
        jdbc.update("UPDATE ticketing.vouchers SET used_count = used_count + 1 WHERE id = ?", voucher.id());

        jdbc.update(
            "UPDATE ticketing.bookings SET voucher_id = ?, discount_amount_vnd = ?, total_amount_vnd = ? WHERE id = ?",
            voucher.id(),
            discountAmount,
            newTotal,
            bookingId
        );

        try {
            jdbc.update(
                "INSERT INTO ticketing.voucher_redemptions (id, voucher_id, booking_id, account_id, discount_amount_vnd, redeemed_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, clock_timestamp())",
                voucher.id(),
                bookingId,
                accountId,
                discountAmount
            );
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "VOUCHER_ALREADY_USED", "Bạn đã dùng mã giảm giá này cho một đơn hàng khác");
        }

        log.info("Applied voucher {} to booking {}: original={}, discount={}, final={}",
            cleanCode, bookingId, currentTotal, discountAmount, newTotal);

        return new ApplyVoucherResponse(
            bookingId,
            voucher.id(),
            cleanCode,
            currentTotal,
            discountAmount,
            newTotal,
            check.message()
        );
    }

    @Transactional
    public void releaseVoucherForBooking(UUID bookingId) {
        if (bookingId == null) return;
        var rows = jdbc.queryForList(
            "SELECT voucher_id, discount_amount_vnd, total_amount_vnd FROM ticketing.bookings WHERE id = ?",
            bookingId
        );
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        UUID voucherId = (UUID) row.get("voucher_id");
        if (voucherId == null) return;

        long discountAmount = ((Number) row.get("discount_amount_vnd")).longValue();
        long totalAmount = ((Number) row.get("total_amount_vnd")).longValue();

        // Restore voucher usage count
        jdbc.update("UPDATE ticketing.vouchers SET used_count = GREATEST(0, used_count - 1) WHERE id = ?", voucherId);

        // Delete redemption record
        jdbc.update("DELETE FROM ticketing.voucher_redemptions WHERE booking_id = ?", bookingId);

        // Reset booking voucher fields
        jdbc.update(
            "UPDATE ticketing.bookings SET voucher_id = NULL, discount_amount_vnd = 0, total_amount_vnd = total_amount_vnd + ? WHERE id = ?",
            discountAmount,
            bookingId
        );

        log.info("Released voucher {} from expired/cancelled booking {}: restored discount {}", voucherId, bookingId, discountAmount);
    }

    public List<VoucherDto> listOrganizerVouchers(DemoPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");

        if (principal.hasRole("ADMIN")) {
            return jdbc.query(
                "SELECT id, organizer_id, event_id, ticket_type_id, code, discount_type, discount_value, " +
                "min_order_amount_vnd, max_discount_amount_vnd, max_uses, used_count, valid_from, valid_to, is_active, created_at " +
                "FROM ticketing.vouchers ORDER BY created_at DESC",
                this::mapVoucherDto
            );
        }

        UUID profileId = securityService.getOrganizerProfileId(principal.accountId());
        if (profileId == null) return Collections.emptyList();

        return jdbc.query(
            "SELECT id, organizer_id, event_id, ticket_type_id, code, discount_type, discount_value, " +
            "min_order_amount_vnd, max_discount_amount_vnd, max_uses, used_count, valid_from, valid_to, is_active, created_at " +
            "FROM ticketing.vouchers WHERE organizer_id = ? OR organizer_id IS NULL ORDER BY created_at DESC",
            this::mapVoucherDto,
            profileId
        );
    }

    @Transactional
    public VoucherDto createVoucher(DemoPrincipal principal, CreateVoucherRequest req) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (req == null) throw new ApiException(400, "VALIDATION_ERROR", "Dữ liệu không được để trống");
        if (req.code() == null || req.code().trim().length() < 3) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã giảm giá phải có ít nhất 3 ký tự");
        }
        String discountType = req.discountType() != null ? req.discountType().trim().toUpperCase() : "PERCENTAGE";
        if (!Set.of("PERCENTAGE", "FIXED_VND").contains(discountType)) {
            throw new ApiException(400, "VALIDATION_ERROR", "Loại giảm giá không hợp lệ (hỗ trợ PERCENTAGE hoặc FIXED_VND)");
        }
        if (req.discountValue() <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "Giá trị giảm giá phải lớn hơn 0");
        }
        if ("PERCENTAGE".equals(discountType) && req.discountValue() > 100) {
            throw new ApiException(400, "VALIDATION_ERROR", "Phần trăm giảm giá không được vượt quá 100%");
        }
        if (req.maxUses() <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "Số lượt dùng tối đa phải lớn hơn 0");
        }
        if (req.validFrom() == null || req.validTo() == null || !req.validFrom().isBefore(req.validTo())) {
            throw new ApiException(400, "VALIDATION_ERROR", "Thời gian áp dụng không hợp lệ (từ ngày phải trước đến ngày)");
        }

        UUID organizerId = null;
        if (!principal.hasRole("ADMIN")) {
            organizerId = securityService.getOrganizerProfileId(principal.accountId());
            if (organizerId == null) {
                throw new ApiException(403, "FORBIDDEN", "Chưa kích hoạt hồ sơ Nhà tổ chức");
            }
        }

        UUID newId = UUID.randomUUID();
        String code = req.code().trim().toUpperCase();

        try {
            jdbc.update(
                "INSERT INTO ticketing.vouchers (id, organizer_id, event_id, ticket_type_id, code, discount_type, " +
                "discount_value, min_order_amount_vnd, max_discount_amount_vnd, max_uses, used_count, valid_from, valid_to, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, true)",
                newId,
                organizerId,
                req.eventId(),
                req.ticketTypeId(),
                code,
                discountType,
                req.discountValue(),
                req.minOrderAmountVnd(),
                req.maxDiscountAmountVnd(),
                req.maxUses(),
                Timestamp.from(req.validFrom()),
                Timestamp.from(req.validTo())
            );
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "DUPLICATE_CODE", "Mã giảm giá " + code + " đã tồn tại trên hệ thống");
        }

        return getVoucherById(newId);
    }

    public VoucherDto getVoucherById(UUID id) {
        var list = jdbc.query(
            "SELECT id, organizer_id, event_id, ticket_type_id, code, discount_type, discount_value, " +
            "min_order_amount_vnd, max_discount_amount_vnd, max_uses, used_count, valid_from, valid_to, is_active, created_at " +
            "FROM ticketing.vouchers WHERE id = ?",
            this::mapVoucherDto,
            id
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    private long computeDiscount(VoucherDto voucher, long orderAmount) {
        if ("PERCENTAGE".equalsIgnoreCase(voucher.discountType())) {
            long discount = Math.multiplyExact(orderAmount, voucher.discountValue()) / 100L;
            if (voucher.maxDiscountAmountVnd() != null && voucher.maxDiscountAmountVnd() > 0) {
                discount = Math.min(discount, voucher.maxDiscountAmountVnd());
            }
            return Math.min(discount, orderAmount);
        } else {
            return Math.min(voucher.discountValue(), orderAmount);
        }
    }

    private VoucherDto mapVoucherDto(ResultSet rs, int rowNum) throws SQLException {
        Timestamp validFrom = rs.getTimestamp("valid_from");
        Timestamp validTo = rs.getTimestamp("valid_to");
        Timestamp createdAt = rs.getTimestamp("created_at");
        long maxDiscount = rs.getLong("max_discount_amount_vnd");
        Long maxDiscountObj = rs.wasNull() ? null : maxDiscount;

        return new VoucherDto(
            rs.getObject("id", UUID.class),
            rs.getObject("organizer_id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getObject("ticket_type_id", UUID.class),
            rs.getString("code"),
            rs.getString("discount_type"),
            rs.getLong("discount_value"),
            rs.getLong("min_order_amount_vnd"),
            maxDiscountObj,
            rs.getInt("max_uses"),
            rs.getInt("used_count"),
            validFrom != null ? validFrom.toInstant() : null,
            validTo != null ? validTo.toInstant() : null,
            rs.getBoolean("is_active"),
            createdAt != null ? createdAt.toInstant() : null
        );
    }
}
