package com.haolo.ticketing.invoices;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.invoices.EInvoiceModels.*;
import com.haolo.ticketing.shared.ApiException;

@Service
public class EInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(EInvoiceService.class);

    private final JdbcTemplate jdbc;

    public EInvoiceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public EInvoiceDto issueEInvoice(DemoPrincipal principal, UUID bookingId, CreateEInvoiceRequest req) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (bookingId == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã đơn hàng");
        if (req == null) throw new ApiException(400, "VALIDATION_ERROR", "Dữ liệu hóa đơn không được để trống");

        if (req.taxCode() == null || req.taxCode().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã số thuế không được để trống");
        }
        String cleanTaxCode = req.taxCode().trim().replaceAll("\\s+", "");
        if (!cleanTaxCode.matches("^[0-9]{10}(-[0-9]{3})?$") && !cleanTaxCode.matches("^[0-9]{13}$")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Mã số thuế không đúng định dạng (10 chữ số hoặc 13 chữ số)");
        }
        if (req.companyName() == null || req.companyName().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tên cơ quan/công ty không được để trống");
        }
        if (req.companyAddress() == null || req.companyAddress().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Địa chỉ doanh nghiệp không được để trống");
        }
        if (req.recipientEmail() == null || req.recipientEmail().isBlank() || !req.recipientEmail().contains("@")) {
            throw new ApiException(400, "VALIDATION_ERROR", "Email nhận hóa đơn không hợp lệ");
        }

        // 1. Verify booking
        var bookings = jdbc.queryForList(
            "SELECT id, account_id, status, total_amount_vnd FROM ticketing.bookings WHERE id = ?",
            bookingId
        );
        if (bookings.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Không tìm thấy đơn hàng");
        }
        var booking = bookings.getFirst();
        UUID ownerId = (UUID) booking.get("account_id");
        if (!principal.accountId().equals(ownerId) && !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Không có quyền xuất hóa đơn cho đơn hàng của người khác");
        }
        String status = (String) booking.get("status");
        if (!"CONFIRMED".equalsIgnoreCase(status)) {
            throw new ApiException(400, "BOOKING_NOT_CONFIRMED", "Chỉ có thể xuất hóa đơn VAT cho đơn hàng đã thanh toán thành công (CONFIRMED)");
        }

        // 2. Check if already issued
        var existing = jdbc.query(
            "SELECT id, booking_id, account_id, tax_code, company_name, company_address, recipient_email, " +
            "invoice_lookup_code, total_amount_vnd, vat_rate, vat_amount_vnd, status, issued_at " +
            "FROM ticketing.e_invoices WHERE booking_id = ?",
            this::mapEInvoiceDto,
            bookingId
        );
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }

        long totalAmount = ((Number) booking.get("total_amount_vnd")).longValue();
        double vatRate = 10.00;
        // In Vietnam VAT law, gross amount includes VAT: VAT = total * 10 / 110
        long vatAmount = Math.round(totalAmount * 10.0 / 110.0);

        UUID invoiceId = UUID.randomUUID();
        String lookupCode = "HDDT-" + Year.now().getValue() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            jdbc.update(
                "INSERT INTO ticketing.e_invoices (id, booking_id, account_id, tax_code, company_name, " +
                "company_address, recipient_email, invoice_lookup_code, total_amount_vnd, vat_rate, vat_amount_vnd, status, issued_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', clock_timestamp())",
                invoiceId,
                bookingId,
                ownerId,
                cleanTaxCode,
                req.companyName().trim(),
                req.companyAddress().trim(),
                req.recipientEmail().trim().toLowerCase(),
                lookupCode,
                totalAmount,
                vatRate,
                vatAmount
            );
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "INVOICE_ALREADY_EXISTS", "Hóa đơn VAT cho đơn hàng này đã được phát hành");
        }

        // Record in email notifications
        jdbc.update(
            "INSERT INTO ticketing.email_notifications (id, booking_id, recipient_email, subject, template_type, payload_json, status, sent_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'E_INVOICE_ISSUED', ?::jsonb, 'SENT', clock_timestamp())",
            bookingId,
            req.recipientEmail().trim().toLowerCase(),
            "Hóa đơn điện tử VAT cho đơn hàng #" + bookingId.toString().substring(0, 8),
            "{\"invoiceLookupCode\":\"" + lookupCode + "\",\"totalAmountVnd\":" + totalAmount + ",\"vatAmountVnd\":" + vatAmount + "}"
        );

        log.info("Issued VAT e-invoice {} for booking {}: lookupCode={}, total={}, vat={}",
            invoiceId, bookingId, lookupCode, totalAmount, vatAmount);

        return getEInvoiceById(invoiceId);
    }

    public EInvoiceDto getEInvoiceByBooking(DemoPrincipal principal, UUID bookingId) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        if (bookingId == null) throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã đơn hàng");

        var list = jdbc.query(
            "SELECT id, booking_id, account_id, tax_code, company_name, company_address, recipient_email, " +
            "invoice_lookup_code, total_amount_vnd, vat_rate, vat_amount_vnd, status, issued_at " +
            "FROM ticketing.e_invoices WHERE booking_id = ?",
            this::mapEInvoiceDto,
            bookingId
        );
        if (list.isEmpty()) {
            throw new ApiException(404, "NOT_FOUND", "Chưa có hóa đơn VAT cho đơn hàng này");
        }
        EInvoiceDto invoice = list.getFirst();
        if (!principal.accountId().equals(invoice.accountId()) && !principal.hasRole("ADMIN")) {
            throw new ApiException(403, "FORBIDDEN", "Không có quyền xem hóa đơn của người khác");
        }
        return invoice;
    }

    public EInvoiceLookupResponse lookupEInvoice(String lookupCode) {
        if (lookupCode == null || lookupCode.isBlank()) {
            return new EInvoiceLookupResponse(false, null, "Mã tra cứu không được để trống");
        }
        var list = jdbc.query(
            "SELECT id, booking_id, account_id, tax_code, company_name, company_address, recipient_email, " +
            "invoice_lookup_code, total_amount_vnd, vat_rate, vat_amount_vnd, status, issued_at " +
            "FROM ticketing.e_invoices WHERE invoice_lookup_code = ?",
            this::mapEInvoiceDto,
            lookupCode.trim().toUpperCase()
        );
        if (list.isEmpty()) {
            return new EInvoiceLookupResponse(false, null, "Không tìm thấy hóa đơn điện tử với mã tra cứu: " + lookupCode);
        }
        return new EInvoiceLookupResponse(true, list.getFirst(), "Tra cứu hóa đơn điện tử thành công");
    }

    public EInvoiceDto getEInvoiceById(UUID id) {
        var list = jdbc.query(
            "SELECT id, booking_id, account_id, tax_code, company_name, company_address, recipient_email, " +
            "invoice_lookup_code, total_amount_vnd, vat_rate, vat_amount_vnd, status, issued_at " +
            "FROM ticketing.e_invoices WHERE id = ?",
            this::mapEInvoiceDto,
            id
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    private EInvoiceDto mapEInvoiceDto(ResultSet rs, int rowNum) throws SQLException {
        Timestamp issuedAt = rs.getTimestamp("issued_at");
        return new EInvoiceDto(
            rs.getObject("id", UUID.class),
            rs.getObject("booking_id", UUID.class),
            rs.getObject("account_id", UUID.class),
            rs.getString("tax_code"),
            rs.getString("company_name"),
            rs.getString("company_address"),
            rs.getString("recipient_email"),
            rs.getString("invoice_lookup_code"),
            rs.getLong("total_amount_vnd"),
            rs.getDouble("vat_rate"),
            rs.getLong("vat_amount_vnd"),
            rs.getString("status"),
            issuedAt != null ? issuedAt.toInstant() : null
        );
    }
}
