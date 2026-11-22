package com.haolo.ticketing.ai;

import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haolo.ticketing.ai.AiModels.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.realtime.BestSeatsService;
import com.haolo.ticketing.realtime.BestSeatsService.BestSeatsResult;
import com.haolo.ticketing.realtime.BestSeatsService.SeatPick;
import com.haolo.ticketing.shared.ApiException;

@Service
public class AiTicketingService {
    private static final Pattern QTY_PATTERN = Pattern.compile("(\\d+)\\s*(ghế|ghe|vé|ve|chỗ|cho)");
    private static final Pattern BUDGET_TRIEU = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(triệu|trieu|tr)");
    private static final Pattern BUDGET_NGHIN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(k|nghìn|nghin)");

    private final JdbcTemplate jdbc;
    private final BestSeatsService bestSeatsService;
    private final GeminiAiAdapter geminiAdapter;
    private final LocalFallbackAiAdapter fallbackAdapter;

    public AiTicketingService(
        JdbcTemplate jdbc,
        BestSeatsService bestSeatsService,
        GeminiAiAdapter geminiAdapter,
        LocalFallbackAiAdapter fallbackAdapter
    ) {
        this.jdbc = jdbc;
        this.bestSeatsService = bestSeatsService;
        this.geminiAdapter = geminiAdapter;
        this.fallbackAdapter = fallbackAdapter;
    }

    public Map<String, Object> status() {
        boolean external = geminiAdapter.isAvailable();
        return Map.of(
            "activeAdapter", external ? geminiAdapter.adapterName() : fallbackAdapter.adapterName(),
            "fallbackAvailable", true,
            "capabilities", List.of("CUSTOMER_CONCIERGE", "ORGANIZER_COPILOT", "ADMIN_OPS_DIAGNOSTICS")
        );
    }

    /** 1. AI Ticketing Concierge for Customers (Real SQL Function Calling + Anti-IDOR order lookup). */
    @Transactional
    public ConciergeChatResponse chatConcierge(DemoPrincipal principal, ConciergeChatRequest req) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Vui lòng nhập nội dung câu hỏi cho trợ lý AI");
        }
        String message = req.message().trim();
        if (message.length() > 1000) {
            throw new ApiException(400, "VALIDATION_ERROR", "Tin nhắn tối đa 1000 ký tự");
        }

        UUID userId = principal != null ? principal.accountId() : null;
        UUID convId = ensureConversation(req.conversationId(), userId, "CUSTOMER_CONCIERGE");
        saveMessage(convId, "USER", message, "USER_INPUT");

        String lower = message.toLowerCase(Locale.ROOT);
        boolean wantsRefundPolicy = lower.contains("chính sách hoàn") || lower.contains("chinh sach hoan")
            || lower.contains("chính sách huỷ") || lower.contains("chinh sach huy") || lower.contains("quy định hoàn")
            || lower.contains("quy dinh hoan") || lower.contains("hoàn vé") || lower.contains("hoan ve")
            || lower.contains("đổi trả") || lower.contains("doi tra") || lower.contains("hủy vé") || lower.contains("huy ve")
            || lower.contains("refund policy");

        boolean wantsVatPolicy = lower.contains("vat") || lower.contains("hóa đơn") || lower.contains("hoa don")
            || lower.contains("hóa đơn đỏ") || lower.contains("mã số thuế") || lower.contains("ma so thue")
            || lower.contains("mst") || lower.contains("thuế gtgt");

        boolean wantsGuide = lower.contains("cách mua") || lower.contains("cach mua") || lower.contains("cách đặt")
            || lower.contains("cach dat") || lower.contains("quy trình") || lower.contains("thanh toán như thế nào")
            || lower.contains("thanh toan nhu the nao");

        boolean wantsOrderLookup = !wantsRefundPolicy && !wantsVatPolicy && (
            lower.contains("đơn hàng của tôi") || lower.contains("don hang cua toi") || lower.contains("kiểm tra đơn")
            || lower.contains("kiem tra don") || lower.contains("tra cứu đơn") || lower.contains("tra cuu don")
            || lower.contains("tiến độ hoàn") || lower.contains("tien do hoan") || lower.contains("đơn")
            || lower.contains("don hang") || lower.contains("hoàn tiền") || lower.contains("hoan tien")
            || lower.contains("hết hạn") || lower.contains("het han") || lower.contains("trạng thái")
        );

        boolean wantsHelpOrGreeting = !wantsRefundPolicy && !wantsVatPolicy && !wantsGuide && !wantsOrderLookup && (
            lower.contains("giúp gì") || lower.contains("giup gi") || lower.contains("làm được gì") || lower.contains("lam duoc gi")
            || lower.contains("chức năng") || lower.contains("hướng dẫn") || lower.matches("^(xin chào|chào|hello|hi|hey|alo)[!?.\\s]*$")
        );

        List<Map<String, Object>> bookingSummaries = new ArrayList<>();
        List<SeatSuggestionCard> suggestions = new ArrayList<>();
        StringBuilder draft = new StringBuilder();

        if (wantsRefundPolicy) {
            draft.append("📄 **Chính sách Hoàn & Hủy vé của EventFlow Enterprise:**\n")
                .append("1. ⏱️ **Đơn hàng quá hạn 10 phút giữ chỗ:** Nếu thanh toán đến muộn sau khi hết hạn, hệ thống tự động đưa đơn vào trạng thái `REFUND_REQUIRED` và hoàn tiền 100% tự động qua Outbox Worker.\n")
                .append("2. 🎟️ **Vé đã xác nhận (CONFIRMED):**\n")
                .append("   • **Gửi tặng bạn bè (Ticket Transfer):** Miễn phí và nguyên tử 100% ngay tại mục \"Vé của tôi\" (thời hạn nhận vé 48 giờ).\n")
                .append("   • **Chợ vé chính chủ Fan-to-Fan Resale:** Đăng bán lại an toàn với giá cam kết ≤ giá gốc. Vé cũ tự động bị thu hồi và hệ thống phát hành vé mới kèm QR ký số HMAC-SHA256 cho người mua.\n")
                .append("3. 📞 **Yêu cầu hỗ trợ đặc biệt:** Liên hệ hotline 1900 6868 hoặc email support@eventflow.vn để Ban tổ chức xử lý theo chính sách từng sự kiện.");
        } else if (wantsVatPolicy) {
            draft.append("🧾 **Chính sách & Hướng dẫn Xuất Hóa Đơn Điện Tử VAT (10%):**\n")
                .append("1. ⚖️ **Hóa đơn điện tử hợp pháp:** Hỗ trợ xuất hóa đơn VAT cho toàn bộ đơn đặt vé đã thanh toán thành công (`CONFIRMED`).\n")
                .append("2. ⚡ **Các cách xuất hóa đơn VAT:**\n")
                .append("   • **Tại bước Checkout:** Tích chọn \"Yêu cầu xuất hóa đơn điện tử VAT (10%)\" và điền MST, tên công ty.\n")
                .append("   • **Tại mục \"Vé của tôi\" (/ve-cua-toi):** Chọn đơn hàng và bấm \"Xuất hóa đơn VAT\" bất cứ lúc nào.\n")
                .append("   • **Lưu sẵn trong Tài khoản cá nhân (/tai-khoan):** Điền sẵn MST mặc định ở tab \"Hóa đơn VAT\", hệ thống sẽ tự động điền cho mọi lần đặt vé sau.\n")
                .append("3. 📧 **Định dạng nhận:** File hóa đơn chuẩn (.pdf & .xml) sẽ được khởi tạo kèm mã tra cứu hóa đơn điện tử chính thức.");
        } else if (wantsGuide) {
            draft.append("🚀 **Quy trình 4 bước đặt vé nhanh chóng trên EventFlow:**\n")
                .append("1. 🔍 **Chọn sự kiện:** Duyệt danh sách sự kiện nổi bật trên Trang chủ hoặc dùng thanh tìm kiếm.\n")
                .append("2. 💺 **Chọn hạng vé:** Chọn số lượng vé GA hoặc chọn trực tiếp vị trí ghế ưng ý trên Sơ đồ ghế 3D (tối đa 6 vé/đơn).\n")
                .append("3. ⏱️ **Khóa chỗ 10 phút:** Bấm \"Giữ vé / Giữ ghế\", hệ thống kích hoạt giao dịch ACID chống bán lố vé (Zero Oversell).\n")
                .append("4. 💳 **Thanh toán:** Quét mã VNPAY Sandbox hoặc thanh toán mô phỏng. Vé điện tử kèm mã QR ký số HMAC-SHA256 sẽ hiển thị ngay tức thì!");
        } else if (wantsHelpOrGreeting) {
            draft.append("Chào bạn! Mình là EventFlow AI Concierge, mình có thể hỗ trợ trực tiếp 3 việc sau trên dữ liệu thật của hệ thống:\n")
                .append("1. 💺 Tìm & gợi ý cụm ghế liền kề đẹp nhất theo ngân sách (VD: \"Tìm 2 ghế liền kề dưới 1 triệu\").\n")
                .append("2. ⚡ Giữ chỗ tức thì bằng giao dịch ACID chống bán lố vé ngay trong khung chat.\n")
                .append("3. 📦 Tra cứu tình trạng đơn hàng, hạn giữ chỗ 10 phút & tiến độ hoàn tiền của bạn (VD: \"Kiểm tra đơn hàng của tôi\").\n")
                .append("Bạn hãy bấm một gợi ý nhanh bên trên hoặc gõ nhu cầu vé của bạn nhé!");
        } else if (wantsOrderLookup) {
            if (userId == null) {
                draft.append("Để tra cứu đơn hàng và tiến độ hoàn tiền (bảo vệ Anti-IDOR), bạn vui lòng đăng nhập tài khoản khách hàng trước nhé!");
            } else {
                bookingSummaries = lookupPersonalBookingsAndRefunds(userId);
                if (bookingSummaries.isEmpty()) {
                    draft.append("Tài khoản của bạn hiện chưa có đơn đặt vé nào trong hệ thống. Bạn có muốn mình gợi ý sự kiện đang mở bán và cụm ghế liền kề tốt nhất không?");
                } else {
                    draft.append("Mình đã tra cứu trực tiếp ").append(bookingSummaries.size()).append(" đơn hàng gần nhất của bạn:\n");
                    for (var b : bookingSummaries) {
                        String status = String.valueOf(b.get("status"));
                        String shortId = String.valueOf(b.get("bookingId")).substring(0, 8);
                        draft.append("• Đơn #").append(shortId).append(" (").append(b.get("eventTitle")).append("): ")
                            .append(explainBookingStatusVi(status, b.get("refundStatus"))).append("\n");
                    }
                }
            }
        } else {
            int qty = parseRequestedQuantity(lower);
            Long maxBudget = parseMaxBudgetVnd(lower);
            suggestions = searchAvailableSuggestions(lower, qty, maxBudget);

            if (suggestions.isEmpty()) {
                draft.append("Hiện chưa tìm thấy cụm ").append(qty).append(" vé/ghế trống phù hợp");
                if (maxBudget != null) {
                    draft.append(" trong ngân sách ").append(formatVnd(maxBudget));
                }
                draft.append(". Bạn có thể nới rộng ngân sách hoặc đăng ký Smart Waitlist để hệ thống tự báo khi có vé nhả nhé!");
            } else {
                draft.append("Mình đã quét kho vé PostgreSQL theo thời gian thực và tìm được ")
                    .append(suggestions.size()).append(" gợi ý phù hợp nhất cho ").append(qty).append(" vé");
                if (maxBudget != null) {
                    draft.append(" (ngân sách tối đa ").append(formatVnd(maxBudget)).append(")");
                }
                draft.append(". Bạn có thể bấm \"Giữ ngay\" trực tiếp bên dưới để khóa chỗ ACID chống bán lố!");
            }
        }

        String context = "Suggestions=" + suggestions.size() + ", PersonalBookings=" + bookingSummaries.size() + "\n" + draft;
        ResolvedAiText resolved = resolveText(
            "Bạn là EventFlow AI Concierge — trợ lý săn vé thông minh cho hệ thống đặt vé sự kiện.",
            context, message, draft.toString().trim()
        );
        saveMessage(convId, "ASSISTANT", resolved.text(), resolved.adapter());

        return new ConciergeChatResponse(convId, resolved.text(), resolved.adapter(), suggestions, bookingSummaries);
    }

    /** 2. AI Copilot for Organizers (SEO description, agenda, ticket tiers & seat matrix config). */
    public OrganizerGenerateResponse generateOrganizerEvent(DemoPrincipal principal, OrganizerGenerateRequest req) {
        if (req == null || req.keywords() == null || req.keywords().isBlank()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Vui lòng nhập từ khóa chủ đề sự kiện");
        }
        String keywords = req.keywords().trim();
        String category = (req.category() != null && !req.category().isBlank()) ? req.category().toUpperCase(Locale.ROOT) : "CONCERT";
        int capacity = (req.targetCapacity() != null && req.targetCapacity() >= 10) ? Math.min(req.targetCapacity(), 1000) : 120;
        long basePrice = (req.basePriceVnd() != null && req.basePriceVnd() >= 50_000) ? req.basePriceVnd() : 350_000L;

        String title = switch (category) {
            case "CONFERENCE" -> "Hội Nghị Đỉnh Cao: " + keywords + " 2026";
            case "THEATER" -> "Đêm Nghệ Thuật Đặc Biệt — " + keywords;
            case "SPORTS" -> "Giải Đấu Đỉnh Cao: " + keywords + " Championship";
            default -> "Đại Nhạc Hội " + keywords + " Live Concert 2026";
        };

        String draftDesc = "Chào mừng đến với " + title + " — sự kiện quy mô " + capacity + " khách với hệ thống phân phối vé điện tử QR ký số và kiểm soát check-in tốc độ cao EventFlow. "
            + "Trải nghiệm không gian âm thanh ánh sáng chuẩn quốc tế, chọn ghế trực quan 3D và giữ chỗ tức thì cam kết 0% bán lố vé.";

        List<String> agenda = List.of(
            "18:00 – 19:00: Đón khách & Soát vé QR điện tử tại cổng (Staff Check-in)",
            "19:00 – 19:30: Khai mạc chương trình & Trình diễn mở màn",
            "19:30 – 21:15: Phần biểu diễn / Nội dung chính — " + keywords,
            "21:15 – 22:00: Giao lưu đặc biệt, bế mạc & Quà tặng khách VIP"
        );

        long earlyBird = Math.max(50_000L, Math.round(basePrice * 0.8 / 10_000.0) * 10_000L);
        long vipPrice = Math.round(basePrice * 1.8 / 10_000.0) * 10_000L;
        int vipCap = Math.max(10, capacity / 5);
        int earlyCap = Math.max(10, capacity / 4);
        int stdCap = Math.max(10, capacity - vipCap - earlyCap);

        List<Map<String, Object>> tiers = List.of(
            Map.of("name", "Early Bird (Ưu đãi sớm)", "mode", "GA", "priceVnd", earlyBird, "capacity", earlyCap),
            Map.of("name", "Standard (Tiêu chuẩn)", "mode", "SEATED", "priceVnd", basePrice, "capacity", stdCap),
            Map.of("name", "VIP Center (Hàng đầu trung tâm)", "mode", "SEATED", "priceVnd", vipPrice, "capacity", vipCap)
        );

        int seatsPerRow = capacity <= 40 ? 8 : (capacity <= 120 ? 10 : 12);
        int rowCount = Math.min(20, Math.max(2, (int) Math.ceil((double) capacity / seatsPerRow)));
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(String.valueOf((char) ('A' + i)));
        }
        Map<String, Object> matrix = Map.of(
            "rows", rows,
            "seatsPerRow", seatsPerRow,
            "totalGeneratedSeats", rowCount * seatsPerRow
        );

        ResolvedAiText resolved = resolveText(
            "Bạn là EventFlow Organizer Copilot — chuyên gia viết mô tả sự kiện chuẩn SEO ngắn gọn, cuốn hút.",
            "Title=" + title + ", Category=" + category + ", Capacity=" + capacity,
            keywords, draftDesc
        );
        return new OrganizerGenerateResponse(resolved.adapter(), title, resolved.text(), agenda, tiers, matrix);
    }

    /** 3. AI Ops Diagnostics for Admins (DLQ / Outbox failures & REFUND_REQUIRED arbitration analysis). */
    public AdminDiagnoseResponse diagnoseOps(AdminDiagnoseRequest req) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        String severity = "LOW";
        String rootCause;
        String recommendation;
        boolean safeToReplay = false;

        if (req != null && req.failureId() != null) {
            var rows = jdbc.queryForList(
                "SELECT id, consumer, event_id, aggregate_id, status, retry_count, last_error, created_at FROM ticketing.message_failures WHERE id = ?",
                req.failureId()
            );
            if (rows.isEmpty()) {
                throw ApiException.missing();
            }
            var f = rows.getFirst();
            evidence.put("failure", f);
            String status = String.valueOf(f.get("status"));
            String lastError = String.valueOf(f.get("last_error"));
            int retries = f.get("retry_count") != null ? ((Number) f.get("retry_count")).intValue() : 0;
            Integer replayCount = jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.replay_audits WHERE event_id = ?", Integer.class, f.get("event_id")
            );
            int replays = replayCount != null ? replayCount : 0;
            evidence.put("replayCount", replays);

            if ("DEAD".equals(status) && replays < 3) {
                severity = "HIGH";
                safeToReplay = true;
                rootCause = "Sự kiện Outbox/Consumer gặp lỗi tạm thời sau " + retries + " lần thử lại (" + lastError + ") và đã chuyển vào Dead Letter Queue.";
                recommendation = "An toàn để bấm \"Outbox Replay\" (đã dùng " + replays + "/3 lượt replay). Inbox deduplication đảm bảo không phát vé trùng.";
            } else if ("DEAD".equals(status)) {
                severity = "HIGH";
                safeToReplay = false;
                rootCause = "Sự kiện DEAD đã cạn hạn mức replay (3/3). Lỗi cuối: " + lastError;
                recommendation = "Không tự replay thêm; kiểm tra trực tiếp payload booking/payment trong PostgreSQL và xử lý thủ công.";
            } else {
                severity = "LOW";
                rootCause = "Sự kiện đã ở trạng thái " + status + ".";
                recommendation = "Không cần can thiệp thêm.";
            }
        } else if (req != null && req.bookingId() != null) {
            var bRows = jdbc.queryForList(
                "SELECT id, status, total_amount_vnd, expires_at, expired_at FROM ticketing.bookings WHERE id = ?",
                req.bookingId()
            );
            if (bRows.isEmpty()) {
                throw ApiException.missing();
            }
            var b = bRows.getFirst();
            evidence.put("booking", b);
            var refunds = jdbc.queryForList(
                "SELECT id, status, amount_vnd, reason, created_at FROM ticketing.refund_attempts WHERE booking_id = ? ORDER BY created_at DESC",
                req.bookingId()
            );
            evidence.put("refunds", refunds);
            String status = String.valueOf(b.get("status"));

            if ("REFUND_REQUIRED".equals(status)) {
                severity = "HIGH";
                safeToReplay = false;
                rootCause = "Thanh toán thành công (IPN/Payment) đến sau thời điểm hết hạn giữ chỗ 10 phút (" + b.get("expired_at") + "). Trọng tài giao dịch đã giữ nguyên bất biến Zero Oversell, không cướp lại vé.";
                recommendation = "Xác nhận phê duyệt hoàn tiền (APPROVE) trong tab Đơn hàng & Hoàn tiền để hoàn trả " + formatVnd(((Number) b.get("total_amount_vnd")).longValue()) + " cho khách.";
            } else if ("PENDING_PAYMENT".equals(status)) {
                severity = "MEDIUM";
                rootCause = "Đơn đang trong thời gian chờ thanh toán (hạn " + b.get("expires_at") + ").";
                recommendation = "Chờ khách hoàn tất thanh toán hoặc để Expiry Worker tự động nhả ghế sau khi hết hạn.";
            } else {
                severity = "LOW";
                rootCause = "Đơn hàng đang ở trạng thái ổn định: " + status + ".";
                recommendation = "Hệ thống nhất quán, không cần thao tác phục hồi.";
            }
        } else {
            // Platform-wide quick health diagnostics
            Integer deadFailures = jdbc.queryForObject("SELECT count(*) FROM ticketing.message_failures WHERE status = 'DEAD'", Integer.class);
            Integer pendingRefunds = jdbc.queryForObject("SELECT count(*) FROM ticketing.bookings WHERE status = 'REFUND_REQUIRED'", Integer.class);
            Integer stuckOutbox = jdbc.queryForObject("SELECT count(*) FROM ticketing.outbox_events WHERE published_at IS NULL", Integer.class);
            int dead = deadFailures != null ? deadFailures : 0;
            int ref = pendingRefunds != null ? pendingRefunds : 0;
            int outbox = stuckOutbox != null ? stuckOutbox : 0;
            evidence.put("deadFailures", dead);
            evidence.put("refundRequiredBookings", ref);
            evidence.put("unpublishedOutbox", outbox);

            if (dead > 0 || ref > 0) {
                severity = "HIGH";
                safeToReplay = dead > 0;
                rootCause = "Phát hiện " + dead + " sự kiện DLQ (DEAD), " + ref + " đơn cần duyệt hoàn tiền (REFUND_REQUIRED) và " + outbox + " outbox chưa xuất bản.";
                recommendation = (ref > 0 ? "Ưu tiên xử lý duyệt hoàn tiền cho " + ref + " đơn thanh toán muộn. " : "")
                    + (dead > 0 ? "Kiểm tra tab Khôi phục sự cố và bấm Outbox Replay cho sự kiện DEAD." : "");
            } else {
                severity = "LOW";
                rootCause = "Toàn bộ hệ thống vận hành ổn định: 0 lỗi DLQ, 0 đơn chờ hoàn tiền, " + outbox + " outbox đang chờ gửi.";
                recommendation = "Không phát hiện bất thường — bất biến Zero Oversell và Kafka Outbox đang ở trạng thái tối ưu.";
            }
        }

        String draftSummary = "[" + severity + "] " + rootCause + " Khuyến nghị: " + recommendation;
        ResolvedAiText resolved = resolveText(
            "Bạn là AI Ops Diagnostics cho kỹ sư vận hành EventFlow EDA.",
            evidence.toString(),
            "Chẩn đoán sự cố vận hành",
            draftSummary
        );
        return new AdminDiagnoseResponse(resolved.adapter(), severity, resolved.text(), rootCause, recommendation, safeToReplay, evidence);
    }

    private List<SeatSuggestionCard> searchAvailableSuggestions(String queryLower, int qty, Long maxBudget) {
        var events = jdbc.queryForList(
            "SELECT id, title, description, venue FROM ticketing.events WHERE status = 'PUBLISHED' ORDER BY starts_at, id LIMIT 25"
        );
        List<SeatSuggestionCard> cards = new ArrayList<>();
        for (var ev : events) {
            if (cards.size() >= 3) break;
            UUID eventId = (UUID) ev.get("id");
            String title = (String) ev.get("title");
            var types = jdbc.queryForList(
                "SELECT id, name, mode, price_vnd FROM ticketing.ticket_types WHERE event_id = ? AND active = true ORDER BY price_vnd",
                eventId
            );
            for (var tt : types) {
                if (cards.size() >= 3) break;
                UUID typeId = (UUID) tt.get("id");
                String typeName = (String) tt.get("name");
                String mode = (String) tt.get("mode");
                long unitPrice = ((Number) tt.get("price_vnd")).longValue();
                long total = unitPrice * qty;
                if (maxBudget != null && total > maxBudget) continue;

                if ("SEATED".equals(mode)) {
                    try {
                        BestSeatsResult best = bestSeatsService.findBestContiguousSeats(eventId, typeId, qty);
                        cards.add(new SeatSuggestionCard(
                            eventId, title, typeId, typeName, "SEATED", unitPrice, total, best.contiguous(),
                            best.seats().stream().map(SeatPick::id).toList(),
                            best.seats().stream().map(SeatPick::label).toList(),
                            qty, best.explanation()
                        ));
                    } catch (ApiException ignored) {
                        // Not enough seats in this ticket type
                    }
                } else {
                    Integer avail = jdbc.queryForObject(
                        "SELECT capacity - held_count - sold_count FROM ticketing.inventory_ga WHERE ticket_type_id = ?",
                        Integer.class, typeId
                    );
                    if (avail != null && avail >= qty) {
                        cards.add(new SeatSuggestionCard(
                            eventId, title, typeId, typeName, "GA", unitPrice, total, true,
                            List.of(), List.of(), qty,
                            "Còn " + avail + " vé GA trống — tổng giá " + formatVnd(total) + " cho " + qty + " vé."
                        ));
                    }
                }
            }
        }
        return cards;
    }

    private List<Map<String, Object>> lookupPersonalBookingsAndRefunds(UUID userId) {
        return jdbc.queryForList(
            "SELECT b.id AS \"bookingId\", e.title AS \"eventTitle\", b.status, b.total_amount_vnd AS \"totalAmountVnd\", " +
                "b.expires_at AS \"expiresAt\", " +
                "(SELECT r.status FROM ticketing.refund_attempts r WHERE r.booking_id = b.id ORDER BY r.created_at DESC LIMIT 1) AS \"refundStatus\" " +
                "FROM ticketing.bookings b JOIN ticketing.events e ON e.id = b.event_id " +
                "WHERE b.account_id = ? ORDER BY b.created_at DESC LIMIT 5",
            userId
        );
    }

    private UUID ensureConversation(UUID requestedId, UUID userId, String persona) {
        if (requestedId != null) {
            var rows = jdbc.queryForList("SELECT id, user_id FROM ticketing.ai_conversations WHERE id = ?", requestedId);
            if (!rows.isEmpty()) {
                UUID owner = (UUID) rows.getFirst().get("user_id");
                if (owner == null || owner.equals(userId)) {
                    return requestedId;
                }
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ticketing.ai_conversations(id, user_id, persona) VALUES (?, ?, ?)",
            id, userId, persona
        );
        return id;
    }

    private void saveMessage(UUID convId, String sender, String content, String adapter) {
        jdbc.update(
            "INSERT INTO ticketing.ai_messages(id, conversation_id, sender, content, adapter_used) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), convId, sender, content, adapter
        );
    }

    private record ResolvedAiText(String text, String adapter) {}

    private ResolvedAiText resolveText(String systemPrompt, String context, String userMsg, String fallbackDraft) {
        if (geminiAdapter.isAvailable()) {
            String enhanced = geminiAdapter.enhanceResponse(systemPrompt, context, userMsg, fallbackDraft);
            if (enhanced != null && !enhanced.isBlank()) {
                return new ResolvedAiText(enhanced, geminiAdapter.adapterName());
            }
        }
        return new ResolvedAiText(
            fallbackAdapter.enhanceResponse(systemPrompt, context, userMsg, fallbackDraft),
            fallbackAdapter.adapterName()
        );
    }

    static int parseRequestedQuantity(String lower) {
        Matcher m = QTY_PATTERN.matcher(lower);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            return Math.min(6, Math.max(1, n));
        }
        return 2;
    }

    static Long parseMaxBudgetVnd(String lower) {
        Matcher mTr = BUDGET_TRIEU.matcher(lower);
        if (mTr.find()) {
            double val = Double.parseDouble(mTr.group(1).replace(',', '.'));
            return Math.round(val * 1_000_000L);
        }
        Matcher mK = BUDGET_NGHIN.matcher(lower);
        if (mK.find()) {
            double val = Double.parseDouble(mK.group(1).replace(',', '.'));
            return Math.round(val * 1_000L);
        }
        return null;
    }

    private static String explainBookingStatusVi(String status, Object refundStatus) {
        return switch (status) {
            case "CONFIRMED" -> "Đã thanh toán & phát hành vé điện tử QR thành công.";
            case "PENDING_PAYMENT" -> "Đang giữ chỗ 10 phút, chờ hoàn tất thanh toán.";
            case "EXPIRED" -> "Đã hết hạn giữ chỗ 10 phút, ghế đã được thu hồi tự động.";
            case "CANCELLED" -> "Bạn đã chủ động hủy đơn và nhả ghế.";
            case "REFUND_REQUIRED" -> "Thanh toán về trễ sau khi hết hạn giữ chỗ — đang xử lý hoàn tiền"
                + (refundStatus != null ? " (trạng thái hoàn tiền: " + refundStatus + ")" : "") + ".";
            default -> "Trạng thái hiện tại: " + status;
        };
    }

    private static String formatVnd(long amount) {
        NumberFormat nf = NumberFormat.getInstance(Locale.of("vi", "VN"));
        return nf.format(amount) + "đ";
    }
}
