# PROGRESS — Nhật ký tiến độ

> Cập nhật sau mỗi task hoặc quyết định ảnh hưởng phạm vi. Ngày dùng múi giờ Việt Nam.

## Trạng thái hiện tại

- **Giai đoạn:** Chuẩn bị trước khi scaffold code
- **Tiến độ:** 13 file tài liệu cập nhật v0.4 sau rà soát; chưa triển khai ứng dụng
- **Task đang làm:** Chưa có
- **Task tiếp theo:** T1.1 — Chốt stack và môi trường
- **Blocker:** Chưa có

- **Task triển khai:** 0/45 hoàn thành

## Đã hoàn thành

- PRD.md: expiry tách biệt, điều kiện nhận lại inventory, demo auth local/test và late payment.
- ARCHITECTURE.md: Expiry Worker, lock order, race payment/expiry, outbox version, mock auth.
- DESIGN.md: persona demo, thông báo chờ nhả inventory và trạng thái EXPIRED/REFUND_REQUIRED.
- RULES.md: không chiếm hold quá hạn trực tiếp, auth demo có giới hạn, state transition thống nhất.
- TASKS.md: concurrency test GA/idempotency tuần 3; seat/expiry worker tuần 4.
- PROGRESS.md: cập nhật quyết định và bước tiếp theo.

## Quyết định đã thống nhất

- Dự án cá nhân 10 tuần, ưu tiên backend, demo local, không dùng .NET.
- Ứng viên stack: Java + Spring Boot, PostgreSQL, Kafka, React/TypeScript, Docker Compose; Redis chỉ cho use case tiện ích cụ thể. Version chốt tại T1.1.
- Modular monolith hướng sự kiện; payment mô phỏng.
- PostgreSQL là nguồn sự thật; consumer at-least-once và idempotent.
- Mock bearer token customer/admin seed sẵn, chỉ local/test; cổng demo bind localhost; không phải auth production.
- Booking mới không giải phóng/ghi đè hold hết hạn. Expiry Worker phải commit nhả inventory trước khi tài nguyên được nhận lại.
- Late success dẫn tới EXPIRED -> REFUND_REQUIRED; lưu history, không phát vé, không nhận lại inventory.
- GA quantity N tạo N vé theo ticket_ordinal trong transaction confirm; BookingConfirmed chỉ thông báo. Một payment chỉ có một nghĩa vụ bù trừ.
- Payment consumer dùng inbox và kết quả bền, không bỏ event theo bookings.aggregate_version; projection chỉ kiểm tra gap nếu nhận đầy đủ event của booking.

## Rủi ro / việc cần xác nhận

- Chốt version, TTL, batch, pool size và version tương thích ở T1.1/T1.4 trước scaffold; các số trong PRD là giả định ban đầu.
- Worker trễ có thể khiến inventory hết hạn chưa đặt lại được; API trả conflict có thể thử lại để giữ invariant.
- Lock order, rollback nhiều ghế, race worker/payment và aggregate version cần được chứng minh bằng integration/concurrency tests.
- Chưa có source code, schema cuối, OpenAPI hoàn chỉnh hoặc kết quả test. Chưa chạy test ứng dụng vì chưa có phần mềm được triển khai.
- Lock timeout cần cấu hình và đo; phải rollback trước khi trả lỗi, phân biệt chờ idempotency với tranh chấp inventory. Body limit/CORS và phiên bản stack chốt ở T1.1/T1.4, không áp giá trị tùy ý.

## Nhật ký

### 2026-11-12 — Hiệu chỉnh tài liệu v0.4

- **Hoàn thành tài liệu:** sửa ticket_ordinal cho vé GA, thêm payment_compensations, sửa quy tắc version theo consumer; đồng bộ API timeout, task và test tuần 3/5/6. Giữ test concurrency tuần 3–4 và state EXPIRED -> REFUND_REQUIRED.
- **Triển khai source:** 0; **SQL migration/test phần mềm:** 0; **nghiệm thu:** chưa thực hiện.
- **Kiểm tra tài liệu:** đối chiếu schema, state, event, API, lịch task và ZIP. Bằng chứng chạy thực tế chỉ có sau khi triển khai.
- **Bước tiếp theo:** T1.1, T1.4 rồi migration/outbox trước booking tuần 3.

### 2026-11-12 — Nâng cấp tài liệu v0.3

- **Hoàn thành tài liệu:** sửa 6 file, thêm README, DATA_MODEL, API_CONTRACT, EVENT_CATALOG, TEST_PLAN, DEMO_AND_RECOVERY và UPGRADE_REPORT. Sửa thứ tự outbox, payment, task thiếu; phân xử payment theo result bền.
- **Triển khai source:** 0; **kiểm thử phần mềm:** 0; **nghiệm thu hệ thống:** chưa thực hiện.
- **Kiểm tra tài liệu:** đối chiếu scope/API/event/task, liên kết và ZIP; kết quả ghi UPGRADE_REPORT.
- **Bước tiếp theo:** T1.1 rồi T1.4; schema/outbox trước booking tuần 3.

### 2026-11-11 — Cập nhật tài liệu sau phản biện Phần 9

- **Hoàn thành:** cập nhật đầy đủ sáu file nền.
- **Thay đổi chính:** thay danh tính do client khai báo bằng mock bearer token seed trong local/test; tách nhả hold của Expiry Worker khỏi lệnh tạo booking; chuyển concurrency test GA/idempotency về tuần 3 và seat/expiry về tuần 4; chuẩn hóa EXPIRED -> REFUND_REQUIRED.
- **Kiểm tra:** rà chéo thuật ngữ, state machine, điều kiện nhận lại inventory và lịch task giữa sáu tài liệu. Không chạy test phần mềm vì chưa có source code.
- **Lỗi/blocker:** không có blocker mới; version stack và schema chi tiết chờ T1.1/T1.4.
- **Bước tiếp theo:** làm T1.1 rồi T1.2; scaffold nghiệp vụ sau khi nền tảng được chốt.

### 2026-11-11 — Khởi tạo bộ tài liệu

- **Hoàn thành:** tạo sáu tài liệu nền ban đầu.
- **Kiểm tra:** thống nhất phạm vi MVP và vai trò database/broker ở mức khởi đầu.
- **Lỗi còn lại:** version stack chưa chốt; chưa có code.
- **Bước tiếp theo:** T1.1.

## Theo dõi giai đoạn

| Giai đoạn | Tài liệu | Source | Test | Nghiệm thu |
|---|---|---|---|---|
| Trước scaffold | v0.4, chờ chốt T1.1/T1.4 | Chưa | Chưa | Chưa |
| Tuần 2–4 | Đặc tả nền có sẵn | Chưa | Chưa | Chưa |
| Tuần 5–10 | Theo TASKS | Chưa | Chưa | Chưa |

## Mẫu cập nhật sau mỗi task

    ### YYYY-MM-DD — Tn.n: Tên task
    - Hoàn thành:
    - Files thay đổi:
    - Kiểm tra đã thực hiện và kết quả:
    - Lỗi/blocker còn lại:
    - Quyết định/tài liệu cần cập nhật:
    - Bước tiếp theo:
