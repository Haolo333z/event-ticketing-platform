# RULES — Quy tắc làm việc và viết code

> Phiên bản: 0.4

Áp dụng cho người và AI cùng làm dự án. Đọc PRD.md, ARCHITECTURE.md, DESIGN.md, TASKS.md và cập nhật PROGRESS.md trước/sau phần việc có ý nghĩa.

## 1. Phạm vi

- Làm đúng task đang chọn trong TASKS.md; không tự nhảy task.
- Giữ MVP trong 10 tuần. Thay đổi stack, tiền/inventory, event schema hoặc bảo mật phải được ghi vào tài liệu trước.
- Nếu tài liệu mâu thuẫn, dừng phần phụ thuộc, ghi mâu thuẫn và thống nhất tài liệu trước khi code.
- Không tuyên bố đã chạy/kiểm chứng nếu chưa có bằng chứng; ghi giả định và giới hạn.

## 2. Bất biến booking/inventory

- PostgreSQL là nguồn sự thật cho booking, inventory, giá và quyền sở hữu vé.
- Redis lock hoặc frontend check không được là lớp duy nhất ngăn oversell.
- Mọi cập nhật inventory nguyên tử, có điều kiện và nằm trong transaction nghiệp vụ.
- Booking nhiều ghế all-or-nothing.
- Ghế HELD dù đã qua hold_expires_at vẫn không được booking mới nhận cho đến khi expiry transaction nhả và commit.
- Lệnh tạo booking không ghi đè hold cũ, không chạy cleanup expiry inline. Worker chưa commit thì API có thể trả conflict có thể thử lại.
- Expiry worker/consumer khóa booking, kiểm tra state/deadline bằng DB clock, khóa inventory theo ID ổn định, rồi cập nhật booking, inventory, history và outbox trong transaction.
- Nhiều hàng inventory khóa theo thứ tự tăng dần trên khóa bất biến. Không suy thứ tự khóa từ danh sách IN.
- Retry deadlock/serialization hữu hạn ở ranh giới transaction.

## 3. Idempotency, trạng thái và payment

- Mọi lệnh tạo booking có idempotency key. Cùng account/operation/key và cùng payload trả kết quả cũ; payload khác bị từ chối.
- Reserve key bằng unique INSERT trong cùng transaction booking; request trùng chờ kết quả commit để replay. Idempotency result và booking/inventory commit nhất quán; lỗi retryable không thành kết quả terminal; không tạo STARTED bền nếu chưa có lease/recovery.
- Lock timeout rollback transaction trước khi trả lỗi; không quy mọi timeout thành request trùng Idempotency-Key. Cấu hình thời gian chờ phải đo trên máy demo.
- Consumer, expiry worker, retry và replay idempotent; kiểm tra state/owner trước khi đổi inventory.
- Confirm phát N vé cho GA quantity N, một vé cho ghế; unique(booking_item_id,ticket_ordinal) và unique(code). BookingConfirmed là thông báo, không phát vé lại. Một payment có tối đa một nghĩa vụ bù trừ, retry cập nhật cùng record.
- Không so event.aggregateVersion với bookings.aggregate_version để bỏ qua PaymentResultRecorded; consumer dedup theo inbox eventId và đọc payment result bền. Chỉ projection nhận đủ event toàn booking mới dùng last_applied_version riêng để phát hiện gap.
- State machine duy nhất: PENDING_PAYMENT -> CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED; EXPIRED -> REFUND_REQUIRED.
- Late success chỉ yêu cầu bù trừ; không confirm lại, nhận lại inventory hoặc phát vé.
- Giữ expired_at/history sau khi state thành REFUND_REQUIRED.
- Payment đúng hạn dùng payments.recorded_at do DB ghi, không dùng thời gian client hoặc lúc Kafka consumer chạy. Worker kiểm tra result bền trước khi expire; khi EXPIRED đã commit thì không nhận lại ghế.
- Không phát vé nếu booking/payment không hợp lệ.
- DB + Kafka là at-least-once với deduplication, không gọi là exactly-once.

## 4. Demo auth và bảo mật

- Dùng mock bearer token seed sẵn, ánh xạ sang principal customer/admin đã seed; chỉ bật trong profile local/test.
- Token demo là fixture công khai, không phải credential production. Mock auth tắt ngoài local/test; nếu bị bật trong profile khác, ứng dụng fail startup.
- Server lấy user ID/role từ principal đã xác thực. Không nhận user ID/role trong body, query hoặc header tùy ý làm danh tính.
- Test nghiệp vụ gắn principal giả trực tiếp; chỉ test tích hợp auth kiểm tra mock token.
- Không ghi secret/password/token/dữ liệu thanh toán vào git, log hoặc ảnh.
- Không lưu dữ liệu thẻ; kiểm tra quyền admin ở server-side.
- Validate input, giới hạn request, không trả stack trace; dữ liệu cá nhân tối thiểu.

## 5. Quy tắc code/dữ liệu

- Dùng migration có phiên bản; không sửa schema thủ công trong môi trường đã chia sẻ.
- Validation ở API boundary và domain/use case; controller không chứa logic giao dịch.
- Tiền dùng BigDecimal hoặc số nguyên VND, không dùng float/double.
- Lưu UTC; quyết định expiry dùng DB time.
- Transaction ngắn; không gọi mạng ngoài khi giữ khóa.
- Ngoại lệ điều phối publisher T6: session advisory lock riêng (ADR0007), ngoài transaction nghiệp vụ; không giữ khóa booking/inventory khi gọi Kafka.
- Tách DTO khỏi entity; dùng unique/FK/check constraint cho bất biến DB.
- Event gồm eventId, eventType, occurredAt, aggregateId, aggregateVersion, correlationId, schemaVersion và payload tối thiểu.
- Cấu hình môi trường rõ ràng, dependency/version có chủ đích.

## 6. Không được tự ý làm

- Không đổi stack/database/API/event/state/hold policy mà không cập nhật tài liệu trước.
- Không xóa dữ liệu/file/migration/test để làm build xanh khi chưa hiểu tác động.
- Không thêm thanh toán thật, gửi email/SMS thật hoặc deploy public trong MVP nếu chưa có yêu cầu.
- Không nuốt exception rồi trả thành công; không retry vô hạn/replay không giới hạn.
- Không thêm microservice/công nghệ chỉ để kiến trúc trông phức tạp.
- Không khẳng định tuyệt đối không oversell từ một lần chạy demo; báo cáo bằng chứng và giới hạn.

## 7. Hoàn tất task

1. Đọc task và file liên quan.
2. Nêu kế hoạch ngắn và file dự kiến sửa.
3. Thực hiện thay đổi trong phạm vi task.
4. Kiểm tra acceptance criteria và ghi đúng kết quả.
5. Cập nhật PROGRESS.md: hoàn thành, kiểm tra, lỗi, bước tiếp theo.
6. Nếu thiết kế đổi, cập nhật PRD/Architecture trước khi code phụ thuộc.


- Mọi task phải đối chiếu [DATA_MODEL](DATA_MODEL.md), [API_CONTRACT](API_CONTRACT.md), [EVENT_CATALOG](EVENT_CATALOG.md) và [TEST_PLAN](TEST_PLAN.md) nếu liên quan. Không tạo file/thư mục phiên bản trùng; migration đã chạy chỉ sửa bằng migration mới.
- CI chạy build/test và quét secret/dependency khi đã có code. Commit nhỏ, mô tả task ID, không commit dữ liệu thật; PROGRESS chỉ đánh dấu Done khi acceptance/test có bằng chứng.
