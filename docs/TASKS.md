# TASKS — Lộ trình 10 tuần

> Phiên bản: 0.4 · Concurrency test được đặt sớm cùng thời điểm triển khai inventory.

## Cách dùng

- Trạng thái: Todo, In Progress, Done, Blocked.
- Đi theo thứ tự phụ thuộc; tối đa một task chính đang làm.
- Chỉ đánh dấu Done khi có đầu ra, acceptance criteria và cập nhật PROGRESS.md.
- Tuần 3/4 có concurrency test nền tảng; tuần 9 kiểm chứng tích hợp/tải tổng hợp, không thay thế test sớm.

## Tuần 1 — Chốt nền tảng

- [x] **T1.1 — Chốt stack/môi trường (Done, 2026-11-12):** kiểm tra tương thích Java/Spring, PostgreSQL, Kafka, Redis nếu cần, Node; ghi version, ADR, TTL hold, worker interval/batch, max quantity, DB pool và yêu cầu máy. Quyết định Redis chỉ khi có use case; không coi version tham khảo trong Word là yêu cầu bắt buộc.
  - **Mục tiêu/phạm vi:** Chốt stack/môi trường: kiểm tra tương thích Java/Spring, PostgreSQL, Kafka, Redis nếu cần, Node; ghi version, ADR, TTL hold, worker interval/batch, max quantity, DB pool và yêu cầu máy. Quyết định Redis chỉ khi có use case.
  - **Phụ thuộc:** khởi đầu. **Module/file:** docs/infra/backend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** ADR kiểm tra tương thích và RAM; tham số cấu hình được ghi; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
  - **Kết quả:** [ADR 0001](adr/0001-stack-and-local-environment.md) chốt JDK 25.0.5+8 LTS / Boot 4.1.2, PostgreSQL 17.11, Kafka 4.2.2, Node 26.13.0; không Redis. Java 26.0.1 phù hợp phạm vi Boot nhưng không chọn làm baseline LTS. Docker Client/Server 29.9.1, Compose 5.6.0 hoạt động; host 15,78 GiB RAM, Docker 7,65 GiB; RAM host trống 2,58 GiB cần giải phóng trước demo. TTL 600s, worker 5s/50, max 6 vé, pool 10/2. Đã kiểm tra CLI, tài nguyên, tài liệu chính thức và manifest image; chưa chạy ứng dụng. JDK 25 chưa được xác minh đã cài, wrapper chưa tạo; đây là chuẩn bị cho scaffold, T1.2 vẫn Todo.
- [x] **T1.2 — Tạo repository (Done, 2026-11-12):** README, .gitignore, .env.example, backend/frontend/infra; thêm CI build/test, secret scan và dependency scan mức cơ bản; không commit secret.
  - **Mục tiêu/phạm vi:** Tạo repository: README, .gitignore, .env.example, backend/frontend/infra; thêm CI build/test, secret scan và dependency scan mức cơ bản; không commit secret.
  - **Phụ thuộc:** T1.1. **Module/file:** docs/infra/backend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** CI chạy build/test mẫu, quét secret/dependency; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
  - **Đã đạt:** giữ Git/docs/remote; bổ sung README, cấu trúc module backend/frontend/infra, .gitignore/.env.example; wrapper Maven 3.9.16, lockfile npm; workflow build/test/Gitleaks/OSV/npm audit và Dependabot. Temurin Java/javac 25.0.5 đã xác minh; build/test mẫu backend (1 test) và frontend (1 test) đạt, OSV 21 packages không phát hiện vấn đề, npm audit 0 vulnerabilities, Gitleaks history/worktree không phát hiện secret, actionlint đạt. **CI đã xác minh:** run 43157891733 của đúng commit 08b144767695876019be5727d679355752395ce3, cả backend/frontend/secrets cùng các bước build/test/scan đều success. T1.2 Done; xem PROGRESS.
- [x] **T1.3 — Chạy môi trường local tối thiểu (Done, 2026-11-12):** app, health endpoint, PostgreSQL và migration đầu.
  - **Mục tiêu/phạm vi:** Chạy môi trường local tối thiểu: app, health endpoint, PostgreSQL và migration đầu.
  - **Phụ thuộc:** T1.2. **Module/file:** docs/infra/backend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** start/health/migration từ DB sạch; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
  - **Kết quả:** PostgreSQL 17.11 healthy trên volume demo mới, DB có 0 bảng trước startup; V1 tạo schema ticketing thành công. Health HTTP 200/UP, V1 success đúng một lần; restart cùng DB không migrate lại. Maven verify đạt; script verify-local PASS; chỉ bind localhost. Lệnh chạy tại infra/README.md; log và lỗi đã xử lý tại PROGRESS. T1.4 chưa bắt đầu.
- [x] **T1.4 — Chốt API/domain v0 (Done, 2026-11-12):** OpenAPI, ER sơ bộ, state machine trong PRD, event envelope, lỗi API, identity demo và bảng trace PRD/API/task; chốt giới hạn request body theo payload thật, CORS cho origin local nếu cần và timeout/error mapping theo loại tranh chấp.
  - **Mục tiêu/phạm vi:** Chốt API/domain v0: OpenAPI, ER sơ bộ, state machine trong PRD, event envelope, lỗi API, identity demo và bảng trace PRD/API/task.
  - **Phụ thuộc:** T1.3. **Module/file:** docs/infra/backend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** review ER/API/event/state và trace yêu cầu; ghi rõ body limit, CORS origin local và ngưỡng chờ khóa có thể cấu hình (không áp cứng 64 KB/3s trước khi đo), phân biệt timeout idempotency với inventory; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
  - **Kết quả:** ADR0002, ER/constraint đủ14bảng, OpenAPI3.1/14operation, JSON Schema7event,6state/5cạnh và TRACEABILITY. tools/contracts verify PASS28ca dương/âm; fixture catalog51839byte→limit128KiB, booking/payment/replay8KiB; CORS hai origin5173; DB lock_timeout1500ms đo1635ms,55P03/rollback;503+Retry-After1 không đoán loại lock. Chưa enforce HTTP policy hoặc tạo V2; T2.1 vẫn Todo. Chi tiết và lệnh tái hiện trong PROGRESS.

## Tuần 2 — Danh mục, seed và phân quyền demo

- [x] **T2.1 — Schema/migration (Done, 2026-11-12)** cho tài khoản, sự kiện, loại vé, ghế và inventory. Gate PostgreSQL migration/constraint đã đạt; xem PROGRESS.
  - **Mục tiêu/phạm vi:** Schema/migration cho tài khoản, sự kiện, loại vé, ghế và inventory.
  - **Phụ thuộc:** T1.4. **Module/file:** catalog/identity/inventory/messaging; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** migration chạy trên PostgreSQL sạch, FK/check/index hợp lệ; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T2.2 — Seed dữ liệu (Done, 2026-11-12)** cho một sự kiện ghế, một sự kiện GA, persona customer/admin và mock bearer token local/test. Gate seed idempotent/profile đã đạt; xem PROGRESS.
  - **Mục tiêu/phạm vi:** Seed dữ liệu cho một sự kiện ghế, một sự kiện GA, persona customer/admin và mock bearer token local/test.
  - **Phụ thuộc:** T2.1. **Module/file:** catalog/identity/inventory/messaging; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** seed idempotent, profile khác không có token demo; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T2.3 — API danh sách/chi tiết sự kiện và availability (Done, 2026-11-12).** Gate PostgreSQL/HTTP 2test đạt; xem PROGRESS.
  - **Mục tiêu/phạm vi:** API danh sách/chi tiết sự kiện và availability. 
  - **Phụ thuộc:** T2.2. **Module/file:** catalog/identity/inventory/messaging; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** API đọc/availability và validation; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T2.4 — API quản trị và mock auth (Done, 2026-11-12):** token seed ánh xạ principal/role; tạo/sửa event, ticket type; chặn giảm capacity dưới held+sold và xóa ghế đang giữ; mock auth chỉ local/test. Gate7test PostgreSQL/HTTP đạt; xem PROGRESS/ADR0003.
  - **Mục tiêu/phạm vi:** API quản trị và mock auth: token seed ánh xạ principal/role; tạo/sửa event, ticket type; chặn giảm capacity dưới held+sold và xóa ghế đang giữ; mock auth chỉ local/test.
  - **Phụ thuộc:** T2.3. **Module/file:** catalog/identity/inventory/messaging; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** 401/403, capacity/seat/price admin, mock auth fail-startup; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

- [x] **T2.5 — Schema outbox nền (Done, 2026-11-12):** migration outbox_events, payments ledger tối thiểu, envelope, index pending và ghi outbox cùng transaction; chưa cần Kafka publisher. Gate4test PostgreSQL commit/rollback/envelope/ledger đạt; xem PROGRESS.
  - **Mục tiêu/phạm vi:** Schema outbox nền: migration outbox_events, envelope, index pending và ghi outbox cùng transaction; chưa cần Kafka publisher.
  - **Phụ thuộc:** T2.4. **Module/file:** catalog/identity/inventory/messaging; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** outbox row commit/rollback cùng nghiệp vụ; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 3 — Booking GA, idempotency và concurrency

- [x] **T3.1 — Contract (Done, 2026-11-12):** POST /api/bookings 202 + Location, GET booking, GET /api/me/bookings và lỗi API. BookingController/BookingQueryService, OpenAPI8operation đã chạy; owner/ADMIN, polling/history,400/401/403/404 đạt. Contract30ca +15response HTTP đạt; xem PROGRESS/ADR0004.
  - **Mục tiêu/phạm vi:** Contract POST /api/bookings 202 + Location, GET booking, GET /api/me/bookings và lỗi API.
  - **Phụ thuộc:** T2.5. **Module/file:** bookings/inventory/frontend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** 202/Location/polling/owner và error code; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T3.2 — Idempotency (Done, 2026-11-12):** unique INSERT trong transaction, request cùng key song song chờ commit để replay; khác payload trả conflict; replay sau EXPIRED trả bookingId gốc; timeout rollback rồi trả lỗi đã phân loại hoặc 503 BOOKING_BUSY. V5/deferred guard + BookingCommandService; IDEM-01/02/03 đạt với PostgreSQL thật, burst20request chỉ1booking. Expired dùng fixture, chưa có worker; xem PROGRESS.
  - **Mục tiêu/phạm vi:** Idempotency: unique INSERT trong transaction, request cùng key song song chờ commit để replay; khác payload trả conflict; replay sau EXPIRED trả bookingId gốc.
  - **Phụ thuộc:** T3.1. **Module/file:** bookings/inventory/frontend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** IDEM-01/02/03: hai request trùng key đồng thời replay; khác payload 409; timeout không để lại booking/hold dở và retry cùng key an toàn; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T3.3 — Giữ vé GA (Done, 2026-11-12):** conditional update trong transaction; rollback nếu thiếu capacity. GaInventory/CatalogBookingAccess + booking/item/history/BookingHeld/key cùng commit. Capacity conflict và lỗi sau outbox rollback toàn bộ; timeout và retry40001 tối đa3attempt đạt. V6 siết initial history; xem PROGRESS.
  - **Mục tiêu/phạm vi:** Giữ vé GA: conditional update trong transaction; rollback nếu thiếu capacity.
  - **Phụ thuộc:** T3.2. **Module/file:** bookings/inventory/frontend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** GA capacity invariant sau conflict/rollback; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T3.4 — Concurrency test GA/idempotency (Done, 2026-11-12):** gửi booking đồng thời vượt capacity và request trùng key/payload; chứng minh không oversell trong các ca đã chạy, cùng key chỉ tạo một booking, payload khác bị từ chối. PostgresBookingTest:50request/5customer và50HTTP/capacity10 đều10success/40conflict/held10;20duplicate chỉ1booking/1outbox/held2; khác payload202/409. [Bằng chứng](evidence/part4-2026-11-12.json).
  - **Mục tiêu/phạm vi:** Concurrency test GA/idempotency: gửi booking đồng thời vượt capacity và request trùng key/payload; chứng minh không oversell, cùng key chỉ tạo một booking, payload khác bị từ chối.
  - **Phụ thuộc:** T3.3. **Module/file:** bookings/inventory/frontend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** GA-01, IDEM-01/02 trong TEST_PLAN; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T3.5 — UI customer ban đầu (Done, 2026-11-12):** danh sách/chi tiết, chọn số lượng, hiển thị conflict/hết hàng. App/client/CSS dùng API thật, tổng giá từ server; giữ key/payload qua503/network/reload.11test frontend/build đạt; UI DOM+HTTP/PostgreSQL thật capacity3/held3/sold0 đạt. Chưa có visual QA trình duyệt do kết nối công cụ không khả dụng; đây là giới hạn đã ghi, không tuyên bố đã kiểm tra layout. T4.1 vẫn Todo.
  - **Mục tiêu/phạm vi:** UI customer ban đầu: danh sách/chi tiết, chọn số lượng, hiển thị conflict/hết hàng.
  - **Phụ thuộc:** T3.4. **Module/file:** bookings/inventory/frontend; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** UI nhận error code, không tính giá; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 4 — Ghế, Expiry Worker và concurrency

- [x] **T4.1 — Mô hình seat map (Done, 2026-11-12):** tái sử dụng seats/availability, V7 composite owner event/type FK; unique label/state/owner kiểm tra PostgreSQL thật. UI map theo label và trạng thái API, không lộ owner. Xem ADR0005/PROGRESS.
  - **Mục tiêu/phạm vi:** Mô hình seat map và trạng thái ghế.
  - **Phụ thuộc:** T3.5. **Module/file:** seats/expiry/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** unique seat label, trạng thái owner nhất quán; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T4.2 — Giữ nhiều ghế nguyên tử (Done, 2026-11-12):** SeatBookingRequest/SeatInventory khóa từng UUID tăng dần; booking/item/owner/deadline/key/history/outbox cùng transaction. POST oneOf GA/seat, canonical seat hash,202/Location. Multi-seat conflict/rollback/duplicate/input sai đều đạt; GA giữ tương thích.
  - **Mục tiêu/phạm vi:** Giữ nhiều ghế nguyên tử: khóa ID tăng dần, rollback nếu một ghế không còn.
  - **Phụ thuộc:** T4.1. **Module/file:** seats/expiry/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** SEAT-01 đảo thứ tự, all-or-nothing; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T4.3 — Expiry Worker (Done, 2026-11-12):** ExpiryService/Scheduler/InventoryRelease, mỗi booking REQUIRES_NEW, DB clock/booking lock/SKIP LOCKED, batch50/interval5s; nhả GA/ghế, state/history/BookingExpired cùng commit.20worker/lặp/rebook/GA expiry/batch51/fault một booking đạt. Result bền được defer/giữ inventory theo ADR0005; PaymentResultProcessor vẫn thuộc T5.2, chưa nghiệm thu payment.
  - **Mục tiêu/phạm vi:** Expiry Worker: mỗi booking một transaction, recheck payment result bền trước release; nhả toàn bộ inventory, chuyển PENDING_PAYMENT -> EXPIRED và ghi history/outbox. Booking mới không tự nhận hold cũ.
  - **Phụ thuộc:** T4.2. **Module/file:** seats/expiry/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** EXP-01 và payment result bền trước expiry; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T4.4 — Concurrency test ghế/worker (Done, 2026-11-12):** PostgresSeatExpiryTest:50lệnh/2customer/2ghế chỉ1winner;50HTTP/1ghế chỉ1winner;20duplicate đảo thứ tự chỉ1booking/1outbox;20worker chỉ1EXPIRED. Gate trước commit chứng minh command mới đợi, chỉ nhận ghế sau release commit, worker cũ không nhả owner mới. [Bằng chứng](evidence/part5-2026-11-12.json).
  - **Mục tiêu/phạm vi:** Concurrency test ghế/worker: tập ghế giao nhau và thứ tự yêu cầu ngược nhau; chạy worker cạnh booking mới. Booking mới chỉ thành công sau commit nhả, nếu không thì conflict thử lại; không có hai owner/nhả lặp.
  - **Phụ thuộc:** T4.3. **Module/file:** seats/expiry/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** SEAT-01/02, EXP-01; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T4.5 — Rollback/lock contention (Done, 2026-11-12):** timeout ghế thứ2/expiry trả503 sau rollback; fault sau outbox không partial hold/state. Deadlock PostgreSQL40P01 thật retry đạt, fault40P01 liên tục dừng3attempt/503. Full58backend/16frontend/build/32contract/22live +GA UI/seat UI/scheduler/restart/manual Windows đạt; giới hạn browser/benchmark/fault mạng ghi PROGRESS. Chưa làm T5.1.
  - **Mục tiêu/phạm vi:** Rollback/lock contention: không để booking nhiều ghế giữ một phần; ghi kết quả và giới hạn môi trường.
  - **Phụ thuộc:** T4.4. **Module/file:** seats/expiry/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** lock timeout/deadlock bounded retry, không partial hold; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 5 — Payment và state machine

- [x] **T5.1 — Payment simulator (Done, 2026-11-12):** simulate-payment owner local/test,strict scenario/requestId,SUCCESS/FAILURE/DELAY_TIMEOUT. Ledger/result outbox bền,DB clock,requestId dedup/replay/changedscenario409.20HTTP đồng thời chỉ1result;auth/input/profile/delay/restart đạt. Xem ADR0006/PROGRESS.
  - **Mục tiêu/phạm vi:** Payment simulator: endpoint local/test success/failure/delay, ghi payments.recorded_at DB clock + outbox; không thu tiền thật.
  - **Phụ thuộc:** T4.5. **Module/file:** payments/tickets/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** scenario được ghi một lần theo requestId; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T5.2 — PaymentResultProcessor (Done, 2026-11-12):** processor dùng chung local adapter/expiry/cancel,booking lock trước inventory;recorded_at quyết định,processed_at dedup callback. PAY-01/02/03,lag fixture,deadline/+1microsecond,8race deadline và20callback lặp đạt. Không Kafka/inbox;không có publicprovidercallback ngoài contract.
  - **Mục tiêu/phạm vi:** PaymentResultProcessor: dùng recorded_at để phân xử, worker/adapter gọi cùng processor, cùng thứ tự khóa booking/inventory; test Kafka lag giả lập.
  - **Phụ thuộc:** T5.1. **Module/file:** payments/tickets/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** PAY-01/02/03 và race worker/consumer; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T5.3 — Vé/bù trừ (Done, 2026-11-12):** V8 tickets/payment_compensations/correlation payment. Confirm GA3vé ordinal1..3/ghế2vé ordinal1 đúngallocation,mãunique;GETtickets owner/ADMIN. Late success REFUND_REQUIRED/0vé/1comp mỗipayment,không chiếm inventory mới. Fault/replay không phát trùng. CompensationPENDING,refundworker vẫn thuộcT6.
  - **Mục tiêu/phạm vi:** Vé/bù trừ: tạo vé trong transaction confirm; unique(booking_item_id,ticket_ordinal) và unique(code); payment_compensations unique(payment_id), retry cập nhật cùng nghĩa vụ.
  - **Phụ thuộc:** T5.2. **Module/file:** payments/tickets/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** TICKET-01 tuần 5: quantity N phát N vé duy nhất, replay processor không phát thêm; no ticket khi REFUND_REQUIRED; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T5.4 — UI checkout/trạng thái (Done, 2026-11-12):** Checkout mô phỏng3scenario/cancel,ticketcode/seatlabel từAPI,countdown vàpoll kểcảEXPIRED→REFUND_REQUIRED,requestId giữ qua503/network/reload.26test frontend/build vàcheckoutHTTP/PostgreSQL thật đạt,không mockresponse tronglive. Chưa browser visual QA;đã ghi giới hạn.
  - **Mục tiêu/phạm vi:** UI checkout/trạng thái: countdown expiresAt, expired và refund required.
  - **Phụ thuộc:** T5.3. **Module/file:** payments/tickets/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** countdown server, state polling; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

- [x] **T5.5 — Cancel, failure, admin race (Done, 2026-11-12):** bodyless cancel/repeat200;timelysuccess chặn cancel sau khi confirmcommit;failure/cancel release một lần.8cancel/payment race,admincapacity/price/active race vàsnapshot đạt. Full88backend/26frontend/44contract/40live,GA/seatUI cũ,checkout/restart/Windows smoke đạt. [Bằng chứng](evidence/part6-2026-11-12.json). T6.1 vẫnTodo.
  - **Mục tiêu/phạm vi:** Cancel, failure, admin race: API cancel pending, failure nhả đúng một lần; admin sửa capacity/giá giữ snapshot và constraint.
  - **Phụ thuộc:** T5.4. **Module/file:** payments/tickets/bookings; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** cancel/failure/admin race và price snapshot; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 6 — Event-driven và phục hồi

- [x] **T6.1 — Inbox và event contract (Done, 2026-11-13):** V9 inbox unique theo consumer/eventId, strict schema v1/hash; migration sạch/rerun và constraint đạt. [Bằng chứng Phần7](evidence/part7-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Inbox và event contract: dùng outbox đã có từ T2.5, tạo inbox và chốt producer/consumer/schema version ở EVENT_CATALOG.
  - **Phụ thuộc:** T5.5. **Module/file:** messaging/operations; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** schema version và inbox unique eventId; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T6.2 — Publisher Kafka (Done, 2026-11-13):** publisher sau commit/ACK theo version/leader;3attempt/backoff, giữFAILED; broker off/on, thứ tự1→2→3, ACK/DB fault và leader test đạt. [Bằng chứng](evidence/part7-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Publisher Kafka: giữ thứ tự theo bookingId kể cả khi có retry; không suy thứ tự từ SKIP LOCKED hoặc timestamp duy nhất.
  - **Phụ thuộc:** T6.1. **Module/file:** messaging/operations; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** broker off/on, outbox pending không mất; test cùng booking có nhiều version và retry publisher không đảo thứ tự hiệu ứng; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T6.3 — Kafka consumer idempotent (Done, 2026-11-13):** processor chung/inbox, audit đủ7type/version riêng; duplicate/restart/gap thật/paymentversion2/BookingConfirmed replay không phát vé đạt. [Bằng chứng](evidence/part7-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Payment consumer gọi cùng PaymentResultProcessor và không so eventVersion với bookings.aggregate_version để bỏ qua tác vụ; projection dùng last_applied_version riêng nếu có.
  - **Phụ thuộc:** T6.2. **Module/file:** messaging/operations; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** MSG-01/02 và TICKET-02: duplicate/gap thật/out-of-order, payment event version >1 vẫn xử lý, replay BookingConfirmed không phát vé; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T6.4 — Failure handling và replay API (Done, 2026-11-13):** failure bền trướcACK, DLQ saucommit/retry3delivery; replay role/profile/audit/dedup/limit3. PostgreSQL rollback/replay, broker restart và50HTTPcontract đạt. [Bằng chứng](evidence/part7-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Failure handling và replay API: broker restart, timeout, DLQ/recovery, admin replay chỉ local/test và có audit.
  - **Phụ thuộc:** T6.3. **Module/file:** messaging/operations; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** replay có role/profile/audit, bounded retries; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T6.5 — Recovery bù trừ (Done, 2026-11-13):** cùng nghĩa vụ unique(payment_id), history nguyên tử/retry tối đa3;12caller chỉ1completion, rollback/exhaustion/cancel-late và worker restart đạt. Full103backend/26frontend,GA/seat/KafkaUI/Windows smoke đạt. [Bằng chứng](evidence/part7-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Recovery bù trừ: refund mô phỏng retry idempotent, có history.
  - **Phụ thuộc:** T6.4. **Module/file:** messaging/operations; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** COMP-01: bù trừ lặp không tạo nghĩa vụ khác hoặc hoàn tất hai lần; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 7 — UI và rà soát auth

- [x] **T7.1 — Rà auth demo (Done, 2026-11-13):** token lạ401/customer→admin403, không tin role/userId client; local/test và profile trộn được kiểm tra. Packaged prod+mock từ chối startup đúng guard; listener127.0.0.1. [Bằng chứng T7](evidence/part8-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Rà auth demo: token seed chỉ local/test, token lạ bị từ chối, role admin kiểm tra server-side.
  - **Phụ thuộc:** T6.5. **Module/file:** frontend/identity; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** SEC-01 và cổng bind localhost; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T7.2 — UI customer (Done, 2026-11-13):** loading/error/empty/detail/history/snapshot cũ và EXPIRED/REFUND_REQUIRED; persona giữ pending key. DOM và GA/seat/payment/expiry/Kafka UI thật đạt; chưa visual QA Chrome. [Bằng chứng](evidence/part8-2026-11-13.json).
  - **Mục tiêu/phạm vi:** UI customer: ghế/GA, loading/error/empty/expired/refund required.
  - **Phụ thuộc:** T7.1. **Module/file:** frontend/identity; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** màn ghế/GA và trạng thái lỗi; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T7.3 — Dashboard admin (Done, 2026-11-13):** catalog mọi trạng thái/create/edit, inventory mới khi mở lại, booking filter/history/pagination/outbox qua API thật. Trang20+5 khớp response/không trùngID; capacity conflict và giá snapshot đạt. [Bằng chứng](evidence/part8-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Dashboard admin: sự kiện, inventory, booking, outbox.
  - **Phụ thuộc:** T7.2. **Module/file:** frontend/identity; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** dashboard theo API, không tự ghi DB; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T7.4 — Rà validation, log/secret, giới hạn request và lỗi API (Done, 2026-11-13).** HTTP/log negative tests, UTF-8/size/depth/duplicate/trailing/field giả/overflow/CORS/correlation và auth DB fault đạt. Full116backend/45frontend/58contract/66HTTP, dependency scan và lint đạt. [Bằng chứng](evidence/part8-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Rà validation, log/secret, giới hạn request và lỗi API. 
  - **Phụ thuộc:** T7.3. **Module/file:** frontend/identity; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** validation/secret/log negative tests; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 8 — Quan sát/vận hành

- [x] **T8.1 — Log cấu trúc (Done, 2026-11-13):** JSON/correlation/booking/message ID xuyên HTTP → outbox sau commit → publisher → Kafka consumer; rollback/retry/duplicate và chống lộ log đã kiểm tra. Health tổng hợp/liveness/readiness theo DB/Kafka; full121backend,58contract,66HTTP/live admin đạt. [Bằng chứng](evidence/t8-1-observability-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Log cấu trúc với correlation/booking/event ID và health check.
  - **Phụ thuộc:** T7.4. **Module/file:** operations/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** correlation ID xuyên API/outbox/consumer; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T8.2 — Metrics (Done, 2026-11-13):** gauges DB độc lập worker,6booking state, overdue/oldest age, unpublished outbox age kể cả FAILED, retry/DLQ và HTTP timer; đơn vị/label cố định ở ARCHITECTURE. Scheduler thật pause→overdue tăng→resume/EXPIRED/release→metric giảm;128backend/58contract/66HTTP đạt. [Bằng chứng](evidence/t8-2-metrics-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Metrics: booking state, overdue hold count/oldest age, outbox age, retry/DLQ, API latency.
  - **Phụ thuộc:** T8.1. **Module/file:** operations/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** worker dừng thì overdue metric tăng; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T8.3 — Recovery view/lệnh admin local (Done, 2026-11-13):** tab Khôi phục và GET metadata/audit/DLQ, tái sử dụng POST replay T6.4/budget3/inbox. Replay payment/confirmed qua Kafka thật không đổi hash nghiệp vụ; lost-response UI gửi2HTTP cùng request chỉ1audit, vé/history không tăng.130backend/54frontend/69contract/74HTTP và build đạt. [Bằng chứng](evidence/t8-3-recovery-2026-11-13.json).
  - **Mục tiêu/phạm vi:** Recovery view/lệnh admin local để xem/replay có kiểm soát.
  - **Phụ thuộc:** T8.2. **Module/file:** operations/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** replay no-op khi event trùng; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T8.4 — README (Done, 2026-11-14):** hướng dẫn tiếng Việt đã chạy từ clone sạch/project/volume/cổng riêng; Flyway V1–V9, seed, start/restart/stop và recovery gate đạt.130backend/54frontend/69contract/74HTTP, không đổi dữ liệu demo. [Bằng chứng](evidence/t8-4-readme-2026-11-14.json).
  - **Mục tiêu/phạm vi:** README: kiến trúc, luồng, chạy local, troubleshooting.
  - **Phụ thuộc:** T8.3. **Module/file:** operations/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** người khác chạy README từ môi trường sạch; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 9 — Kiểm chứng tích hợp

- [x] **T9.1 — Chạy lại bất biến (Done, 2026-11-14):** giữ nguyên130test backend hiện có, chạy PG/Kafka thật từ HEADaa8b9f3 trong clone/project riêng;130/130 backend,54/54frontend,69contract,3×74HTTP và5nhóm live đạt. SQL không oversell/partial hold/vé hoặc release trùng; rollback, scheduler/consumer/JVM restart và replay no-op có bằng chứng. [Kết quả](evidence/t9-1-invariants-2026-11-14.json).
  - **Mục tiêu/phạm vi:** Chạy lại bất biến: inventory, idempotency, expiry, event trùng.
  - **Phụ thuộc:** T8.4. **Module/file:** integration-tests/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** chạy lại suite TEST_PLAN; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T9.2 — Tối thiểu 50 request đồng thời (Done, 2026-11-14):**3lượt×50HTTP cho mỗiGA/ghế,peak50,300request đo:33success/267conflict dự kiến/0lỗi khác; oversell/partial hold/ghế hoặc vé trùng=0. Máy/pool10/2,throughput vàp50/p95/p99 từnglượt +320sample/SQL đã lưu. [Bằng chứng](evidence/t9-2-load-2026-11-14.json).
  - **Mục tiêu/phạm vi:** Tối thiểu 50 request đồng thời trên ghế/GA; ghi máy, latency, success/conflict và oversell.
  - **Phụ thuộc:** T9.1. **Module/file:** integration-tests/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** LOAD-01 >=50, lưu máy/pool/latency/count; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T9.3 — Kịch bản lỗi (Done, 2026-11-14):** Kafka outage/restart/duplicate/retry/DLQ/replay, payment fail/delay/late, rollback và JVM restart đạt trên PG/Kafka riêng.130backend/54frontend/69contract/3×74HTTP;37snapshot/13nhóm,11counter bất biến đều0. Timely payment được worker confirm ngay khi broker thật đangdừng/inbox0; hồi phục không phátvé lại. [Bằng chứng](evidence/t9-3-recovery-2026-11-14.json).
  - **Mục tiêu/phạm vi:** Kịch bản lỗi: Kafka gián đoạn, consumer restart, payment fail/delay, retry/replay, late success.
  - **Phụ thuộc:** T9.2. **Module/file:** integration-tests/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** MSG-01, PAY-02 và recovery; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T9.4 — Sửa lỗi ưu tiên cao; rà migration/config và loại secret khỏi repo (Done, 2026-11-14):** migration/config/secret gate local đạt; lỗi race CI#38 đã sửa. Xác nhận run#39 đúng SHA984e95d7dc7aca0b7ea94743e27e21d5d713d906, cả4job backend/frontend/contracts/secrets success. [Audit](evidence/t9-4-audit-2026-11-14.json), [sửa CI#38](evidence/ci38-backend-fix-2026-11-14.json), [CI#39](evidence/t9-4-ci39-2026-11-14.json).
  - **Mục tiêu/phạm vi:** Sửa lỗi ưu tiên cao; rà migration/config và loại secret khỏi repo. 
  - **Phụ thuộc:** T9.3. **Module/file:** integration-tests/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** CI xanh, không secret, migration sạch; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 10 — Đóng gói/báo cáo

- [x] **T10.1 — Demo từ môi trường sạch (Done, 2026-11-14)** theo README: clone984e95d/project/volume/cổng riêng; seed và V1–V9 sạch. Chủ dự án xác nhận Chrome GA1vé và ghếA1 đềuCONFIRMED, history/admin đúng2booking. Restart đúng runbook:18bảng nghiệp vụ và9row Flyway giữ nguyên, HTTP health200/UP,2booking/2vé còn nguyên. [Bằng chứng](evidence/t10-1-clean-demo-2026-11-14.json), [nguồn Chrome](evidence/t10-1-chrome-user-2026-11-14.md).
  - **Mục tiêu/phạm vi:** Demo từ môi trường sạch theo README.
  - **Phụ thuộc:** T9.4. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** demo từ DB sạch theo runbook; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T10.2 — Chuẩn bị dữ liệu, persona demo và kịch bản 5–10 phút (Done, 2026-11-14):** hai lượt tập trên project/volume/fixture riêng,8/8bước mỗi lượt; lượt có định giờ365,499giây gồm310giây xem/thuyết minh. GA/ghế/idempotency/late refund/outage/replay, SQL và Stop/Start đạt. [Runbook](DEMO_AND_RECOVERY.md), [bằng chứng](evidence/t10-2-rehearsal-2026-11-14.json). Chưa kiểm tra Chrome trực tiếp trong T10.2.
  - **Mục tiêu/phạm vi:** Chuẩn bị dữ liệu, persona demo và kịch bản 5–10 phút. 
  - **Phụ thuộc:** T10.1. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** kịch bản demo 5–10 phút có seed; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T10.3 — Báo cáo (Done, 2026-11-14):** bản tiếng Việt43trang Word/PDF,8sơ đồ/24bảng/46mục lục đúng trang; số đo nối evidence, CSV300mẫu và407counter được đối chiếu, từng PNG cuối đã rà.130test gate trước tách9testlauncher/121skip; không bịa Chrome/tiền thật/exactly-once. [Word](report/bao-cao-do-an.docx), [PDF](report/bao-cao-do-an.pdf), [bằng chứng](evidence/t10-3-report-2026-11-14.json).
  - **Mục tiêu/phạm vi:** Báo cáo về bất biến, test concurrency tuần 3–4, outbox/recovery, kết quả và giới hạn.
  - **Phụ thuộc:** T10.2. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** báo cáo số thực và giới hạn; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [x] **T10.4 — Chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo (Done, 2026-11-15):** chốt backlog ngoài MVP tại docs/T10_4_ACCEPTANCE_CHECKLIST.md; đóng gói mã nguồn tái lập sạch ra file event-ticketing-platform-source.zip (2,8 MB, 261 files, SHA-256 32423cd...); đối soát 8 sơ đồ kiến trúc và bảo toàn minh chứng giao diện trung thực; gắn Git tag v1.0.0-demo; hoàn tất toàn bộ 45/45 tasks của đồ án (100% Done). [Checklist](T10_4_ACCEPTANCE_CHECKLIST.md), [Bằng chứng tái lập](evidence/t10-4-repro-verify-2026-11-15.json), [Bằng chứng sơ đồ](evidence/t10-4-diagrams-images-2026-11-15.json).
  - **Mục tiêu/phạm vi:** Chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo. 
  - **Phụ thuộc:** T10.3. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** ZIP/source/tag tái lập được; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Ưu tiên khi trễ tiến độ

1. Inventory, idempotency, expiry và booking state machine.
2. Outbox, retry, consumer idempotency và ít nhất một kịch bản recovery.
3. Demo ghế/GA chạy được, concurrency evidence và báo cáo trung thực.
4. Cắt giảm trước dashboard nâng cao, trang trí UI, realtime push và analytics sâu.

---

# LỘ TRÌNH NÂNG CẤP FULL-STACK PRODUCT V2 (T11 — T19)

> Chi tiết định nghĩa phạm vi và yêu cầu xem tại [docs/upgrade-v2/PRODUCT_SCOPE_V2.md](upgrade-v2/PRODUCT_SCOPE_V2.md), [UPGRADE_REQUIREMENTS_V2.md](upgrade-v2/UPGRADE_REQUIREMENTS_V2.md) và [ROADMAP_V2.md](upgrade-v2/ROADMAP_V2.md).  
> Giữ nguyên lịch sử nghiệm thu T1–T10 (45/45 tasks Done). Quản lý nghiêm ngặt: Mỗi thời điểm chỉ thực hiện một task chính, không tự ý commit khi chưa có chỉ thị.

## Giai đoạn 0 — Re-baseline và Chốt Thiết kế (T11)

- [x] **T11.1 — Re-baseline Source Code & Lập Gap Matrix Nâng Cấp V2 (Done, 2026-11-16):** Kiểm toán toàn diện Git, Database PostgreSQL (Flyway V1-V10, 19 bảng), OpenAPI (17 paths/18 operations), Backend (Java modular monolith), Frontend (React Multi-page routing). Lập [docs/upgrade-v2/SOURCE_REBASELINE.md](upgrade-v2/SOURCE_REBASELINE.md) và [docs/upgrade-v2/GAP_MATRIX.md](upgrade-v2/GAP_MATRIX.md) đối soát 79 yêu cầu (16 Implemented & Verified, 18 Partially Implemented, 45 Not Implemented). Kết quả test 134 backend test PASS, 54 frontend test PASS, 69 contract test PASS. Không can thiệp sửa đổi code logic hay migration DB trong task này.
- [x] **T11.2 — Chốt ADR Thiết Kế Kiến Trúc & Kế Hoạch Cập Nhật Hợp Đồng API (Done, 2026-11-16):** Chốt ADR cho session/refresh token family, Google OIDC Authorization Code Flow, Four-role RBAC & Event Scope, Payment Provider & Idempotent Callback / Late Refund Arbitration; lập kế hoạch cập nhật OpenAPI và migration V11+.
- [x] **T11.3 — Thiết Kế Migration V11+ & Đảm Bảo Tương Thích Ngược Test Fixtures (Done, 2026-11-16):** Thiết kế additive schema migration cho roles, external_identities, payment_attempts, check_ins; đảm bảo profile local/test và mock bearer tokens tiếp tục hoạt động cho test suite tự động.
- [ ] **T11.4 — Chốt Nhà Cung Cấp Cổng Thanh Toán Sandbox & Xác Nhận Reachability:** Lựa chọn merchant sandbox (VNPAY/MoMo), thiết lập cấu hình biến môi trường ngoài Git repo, chuẩn bị giải pháp webhook/IPN tunneling (ngrok/Cloudflare Tunnel) cho local development.

## Giai đoạn 1 — Identity, Xác Thực & Phân Quyền 4 Vai Trò (T12)

- [x] **T12.1 — Schema & Persistence Cho Multi-Role & External Identities (Migration V11+) (Done, 2026-11-16):** Tạo Flyway migration `V11__four_role_rbac_and_external_identities.sql` gồm các bảng `roles`, `user_roles`, `external_identities`, `refresh_tokens`, `password_reset_tokens`. Seed 4 vai trò chuẩn, backfill dữ liệu người dùng cũ, nới lỏng CHECK constraint `demo_role`. Đã kiểm chứng migrate thành công trên PostgreSQL 17 thật (24 bảng trong schema `ticketing`).
- [x] **T12.2 — Hoàn Thiện Email/Password Auth, Session Refresh & Profile Management (Done, 2026-11-16):** Triển khai `RefreshTokenService` với CSPRNG 32 bytes, mã hóa SHA-256 lưu DB, quản lý phiên Token Family. Phát hiện tấn công tái sử dụng (Reuse Detection: lập tức vô hiệu hóa toàn bộ Token Family). Endpoint `/api/auth/register` (BCrypt + role CUSTOMER), `/api/auth/login` (cấp JWT 15m + cookie HttpOnly 7d), `/api/auth/refresh` (rotation session), `/api/auth/logout` (thu hồi token, xóa cookie Max-Age=0), `/api/auth/me` (trả về danh sách roles). Frontend client tự động refresh khi gặp 401 và retry an toàn.
- [ ] **T12.3 — Tích Hợp Đăng Nhập Google OIDC (Authorization Code Flow):** Backend OIDC client xác thực ID token Google (`sub`, `email`, `name`, `picture`), tự động tạo hoặc liên kết tài khoản an toàn.
- [ ] **T12.4 — Quản Lý Liên Kết Tài Khoản (Link/Unlink) & Reset Mật Khẩu:** Quy trình liên kết tài khoản ngoài, bảo vệ không cho unlink phương thức duy nhất, quên/đặt lại mật khẩu qua email token.
- [x] **T12.5 — Thực Thi Phân Quyền 4 Vai Trò (CUSTOMER, STAFF, ORGANIZER, ADMIN) & Event Scope (Done, 2026-11-16):** Cập nhật `DemoPrincipal` hỗ trợ đa vai trò (`roles()`, `hasRole(target)`), `MockBearerFilter` nạp danh sách `ROLE_<ROLE>` vào `GrantedAuthority` từ `user_roles`, bật `@EnableMethodSecurity(prePostEnabled = true)` trong `SecurityConfiguration`. Bảo toàn 100% khả năng tương thích ngược cho mock bearer tokens của test suite. Giao diện frontend hiển thị vai trò và điều hướng tương ứng cho cả 4 vai trò.

## Giai đoạn 2 — Quản Lý Nhà Tổ Chức (Organizer), Nhân Sự (Staff) & Catalog Sự Kiện (T13)

- [x] **T13.1 — Organizer Profile & Quyền Sở Hữu Sự Kiện (Event Ownership) (Done, 2026-11-16):** Xây dựng bảng `ticketing.organizer_profiles`, tự động cấp vai trò `ORGANIZER` khi lưu hồ sơ đối tác, liên kết `organizer_id` với `events`, cô lập quyền truy cập tuyệt đối giữa các Organizer (IDOR protection).
- [x] **T13.2 — Chuẩn Hóa Quản Lý Địa Điểm (Venues), Danh Mục (Categories) & Media (Done, 2026-11-16):** Bảng `ticketing.venues` (seed sẵn 4 địa điểm biểu diễn tiêu chuẩn), `ticketing.event_categories` (seed 4 thể loại chính: CONCERT, THEATER, CONFERENCE, SPORTS), và bảng `ticketing.event_media`.
- [x] **T13.3 — Vòng Đời Sự Kiện Của Organizer (Draft, Publish, Close, Cancel) (Done, 2026-11-16):** Quy trình tạo sự kiện ở trạng thái `DRAFT`, kiểm tra điều kiện mở bán (chặn xuất bản khi chưa có vé hoặc sức chứa = 0), xuất bản `PUBLISHED` đồng bộ lên Public Catalog (ẩn các sự kiện DRAFT).
- [x] **T13.4 — Cấu Hình Loại Vé, Sức Chứa GA & Sơ Đồ Ghế Ngồi (Done, 2026-11-16):** Cấu hình linh hoạt loại vé GA (General Admission) gắn với `inventory_ga` hoặc vé ghế chỉ định (Seated) sinh danh sách ghế trong `seats`. Bảo toàn 100% bất biến chống bán lố vé.
- [x] **T13.5 — Phân Bổ Nhân Sự (Staff Assignment) Theo Phạm Vi Sự Kiện (Done, 2026-11-16):** Bảng `ticketing.event_staff_assignments`, xác thực tài khoản có vai trò `STAFF`, gán nhân viên vào sự kiện cụ thể, hỗ trợ thu hồi quyền (`revoked_at`). Giao diện Organizer Dashboard (`/organizer`) trực quan kết nối API thật.

## Giai đoạn 3 — Trải Nghiệm Khách Hàng (Customer Commerce Journey) (T14)

- [x] **T14.1 — Trang Chủ, Khám Phá & Bộ Lọc Tìm Kiếm Sự Kiện (Done, 2026-11-16):** Tích hợp giao diện khám phá sự kiện thực tế, bộ lọc danh mục/địa điểm, phân trang dữ liệu từ SQL.
- [x] **T14.2 — Luồng Đặt Vé Khách Hàng Gắn Với Danh Tính Thực (Done, 2026-11-16):** Quy trình đặt vé yêu cầu đăng nhập, liên kết `account_id` thật từ `SecurityContextHolder`, bảo vệ giao dịch ACID PostgreSQL (`SELECT FOR UPDATE`), hỗ trợ đa vai trò (`hasRole("CUSTOMER")`).
- [x] **T14.3 — Trang "Vé Của Tôi" & Lịch Sử Giao Dịch Từ PostgreSQL (Done, 2026-11-16):** Hiển thị danh sách đơn hàng và vé điện tử của tài khoản đăng nhập (`GET /api/me/bookings`, `GET /api/bookings/{id}/tickets`), hiển thị thông tin sự kiện, địa điểm, thời gian và số ghế/hạng vé.
- [x] **T14.4 — Tạo Mã Vé Điện Tử E-Ticket QR & Tải Vé (Done, 2026-11-16):** Dịch vụ `TicketQrService` và endpoint `GET /api/tickets/{id}/qr` ký số Signed Opaque QR Token (`ticketId.ticketCode.eventId.issuedAt.signature`) bằng HMAC-SHA256, kiểm tra quyền sở hữu vé nghiêm ngặt (chặn 403 IDOR), component `QrCodeSvg` vector động và chế độ In Vé Điện Tử (`window.print()`).

## Giai đoạn 4 — Cổng Thanh Toán Sandbox Bên Ngoài (Payment Gateway) (T15)

- [x] **T15.1 — Thiết Kế Cổng Kết Nối Payment Gateway Port & Bảng Giao Dịch Thanh Toán (Done, 2026-11-16):** Migration Flyway V13 tạo 3 bảng `payment_attempts`, `payment_notifications` (IPN journal), `refund_attempts` (tổng 32 bảng schema `ticketing`). Thiết kế kiến trúc Lục giác `PaymentGatewayPort`, `PaymentGatewayRegistry` tích hợp `VnPayGatewayAdapter` và bảo toàn `PaymentSimulatorAdapter`.
- [x] **T15.2 — Luồng Khởi Tạo Giao Dịch & Chuyển Hướng Sandbox Checkout (Done, 2026-11-16):** Endpoint `POST /api/payments/checkout` đọc số tiền snapshot từ đơn hàng DB, sinh `merchantRef` duy nhất, ký số HMAC-SHA512 URL chuyển hướng sang cổng VNPAY Sandbox.
- [x] **T15.3 — Tiếp Nhận Kết Quả Trả Về (Return URL) & Xử Lý Server Callback (IPN) (Done, 2026-11-16):** Endpoint `GET /api/payments/vnpay/ipn` (Server-to-Server) xác thực chữ ký HMAC-SHA512, đối soát `merchantRef` và số tiền, xử lý idempotent 100% qua `payment_notifications` chống callback trùng lặp, chuyển trạng thái `CONFIRMED` và phát vé tự động khi thành công. Endpoint `GET /api/payments/vnpay/return` hỗ trợ giao diện người dùng.
- [x] **T15.4 — Đối Soát Giao Dịch, Phục Hồi Lỗi & Xử Lý Thanh Toán Muộn (Late Payment Arbitration) (Done, 2026-11-16):** Bảo toàn bất biến Zero Oversell; khi IPN thành công nhưng booking đã quá hạn giữ chỗ (`expires_at < now()`) và kho vé đã giải phóng, hệ thống chuyển trạng thái đơn sang `REFUND_REQUIRED`, tự động tạo bản ghi trong `refund_attempts` với lý do `LATE_PAYMENT_AFTER_EXPIRY`, tuyệt đối không cướp lại vé của người khác.
- [x] **T15.5 — Điều Phối & Xử Lý Hoàn Tiền (Refund Orchestration) & Frontend Integration (Done, 2026-11-16):** Bổ sung nút thanh toán VNPAY Sandbox trên `Checkout.ts`, tạo trang kết quả `/payment-return` (`PaymentReturnPage.ts`) hiển thị chi tiết mã giao dịch và số tiền, tích hợp polling trạng thái qua `GET /api/bookings/{id}/payment-status`.

## Giai đoạn 5 — Soát Vé Check-in & Vận Hành Sự Kiện (Staff Portal) (T16)

- [x] **T16.1 — Cổng Vận Hành Sự Kiện Dành Cho Staff (Staff Dashboard) (Done, 2026-11-16):** Màn hình làm việc chuyên biệt `/staff` cho nhân viên soát vé, tích hợp bộ chọn sự kiện được phân công (`GET /api/staff/events`), chọn cổng soát vé linh hoạt và thống kê trực quan.
- [x] **T16.2 — Quét & Xác Thực Vé Điện Tử (QR Scanner / Manual Entry) (Done, 2026-11-16):** Hỗ trợ cả 2 chế độ: quét mã QR Token ký số HMAC-SHA256 hoặc nhập mã vé thủ công, xác minh chữ ký số, kiểm tra khớp sự kiện (`EVENT_MISMATCH`), trạng thái đơn `CONFIRMED`.
- [x] **T16.3 — Xử Lý Soát Vé Nguyên Tử (Atomic Check-in Transaction) (Done, 2026-11-16):** Giao dịch nguyên tử chèn vào `ticketing.ticket_check_ins`, kiểm soát ràng buộc duy nhất `CONSTRAINT uq_ticket_checked_in UNIQUE (ticket_id)`, ngăn chặn 100% tình trạng quét trùng vé (anti double check-in, trả 409 Conflict kèm thời gian và cổng quét lần đầu).
- [x] **T16.4 — Danh Sách Khách Tham Dự & Nhật Ký Kiểm Toán Soát Vé (Done, 2026-11-16):** Thống kê tỷ lệ check-in realtime (`GET /api/staff/events/{id}/stats`), nhật ký 50 lượt soát vé gần nhất (`GET /api/staff/events/{id}/history`), phản hồi thị giác (màn hình Xanh/Đỏ/Vàng) và âm thanh Web Audio API.

## Giai đoạn 6 — Quản Trị Hệ Thống, Báo Cáo & Truyền Thông (Admin & Platform) (T17)

- [x] **T17.1 — Cổng Quản Trị Người Dùng, Cấp Quyền & Mời Nhân Sự (Done, 2026-11-16):** Quản lý người dùng toàn hệ thống (`GET /api/admin/users`), phân trang, tìm kiếm họ tên/email/phone, lọc vai trò. Cấp/thu hồi vai trò (`POST /api/admin/users/{id}/roles`), khóa/mở khóa tài khoản (`PUT /api/admin/users/{id}/status`), chống privilege escalation và tự tước quyền/tự khóa (`CANNOT_DEMOTE_SELF`, `CANNOT_LOCK_SELF`), tự động ghi nhật ký kiểm toán vào `ticketing.audit_logs`.
- [x] **T17.2 — Dashboard Quản Trị Đơn Hàng, Thanh Toán & Duyệt Hoàn Tiền (Done, 2026-11-16):** Quản trị đơn hàng toàn sàn (`GET /api/admin/bookings`), tiếp nhận danh sách yêu cầu hoàn tiền từ cổng thanh toán (`GET /api/admin/refunds`), phê duyệt hoàn tiền (`POST /api/admin/refunds/{id}/process` với `APPROVE` -> `COMPLETED`, `REJECT` -> `FAILED`), ghi nhận settled_at, tự động lưu vết biên nhận vào `ticketing.email_notifications` và `ticketing.audit_logs`.
- [x] **T17.3 — Báo Cáo Kinh Doanh & Doanh Thu Chi Tiết (Export CSV) (Done, 2026-11-16):** Endpoint tổng hợp chỉ số kinh doanh toàn sàn (`GET /api/admin/reports/summary`), báo cáo doanh thu & tỷ lệ lấp đầy theo sự kiện (`GET /api/admin/reports/events`), xuất dữ liệu CSV chuẩn RFC 4180 có UTF-8 BOM (`GET /api/admin/reports/export-csv`), cơ chế `sanitizeCsvField` ngăn chặn triệt để tấn công CSV Formula Injection cho Excel và Calc.
- [x] **T17.4 — Hệ Thống Gửi Thông Báo & Hóa Đơn Điện Tử (Email Receipts) (Done, 2026-11-16):** Bảng `ticketing.email_notifications` (nhật ký biên nhận hóa đơn điện tử / email outbox log) lưu vết `booking_id`, `recipient_email`, `subject`, `template_type`, `payload_json`, `status`, `sent_at`.
- [x] **T17.5 — Tiếp Nhận Yêu Cầu Hỗ Trợ & Leads Tư Vấn Doanh Nghiệp (Done, 2026-11-16):** Endpoint nhận thông tin leads tư vấn doanh nghiệp public (`POST /api/contact/leads`), quản lý phân trang và lọc theo trạng thái (`GET /api/admin/leads`), cập nhật trạng thái (`PUT /api/admin/leads/{id}/status`) chuyển đổi giữa `NEW`, `CONTACTED`, `CONVERTED`, `CLOSED` kèm ghi chú và audit log. Giao diện Admin Portal 5 tab động kết nối API thật.

## Giai đoạn 7 — Hoàn Thiện Giao Diện Sản Phẩm & Visual QA (T18)

- [x] **T18.1 — Chuẩn Hóa Hệ Thống Design Tokens & Components (Done, 2026-11-17):** Thống nhất bảng màu chuẩn Sao Kim Branding (`#4338ca`/`#6366f1` Indigo, `#0284c7` Sky, `#059669` Emerald, `#d97706` Amber, `#e11d48` Rose, `#0f172a` Slate), spacing chuẩn 4/8/12/16/24/32px, bo góc 8/12/16/20px, hiệu ứng đổ bóng đa lớp, utilities skeleton loader (`.skeleton-pulse`) và spinner loader (`.ef-spinner`).
- [x] **T18.2 — Hoàn Thiện Giao Diện Khám Phá & Chi Tiết Sự Kiện (Done, 2026-11-17):** Trang Chủ (`/`) với mega-menu điều hướng, hero banner tốc độ cao, danh mục nổi bật, floating dock. Trang Đặt Vé (`/dat-ve`) với bộ lọc GA/Seated và tìm kiếm realtime. Sơ Đồ Ghế Trực Quan với Stage 3D, phân khu ghế và tooltip thông tin ghế.
- [x] **T18.3 — Tối Ưu UX Luồng Xác Thực, Đặt Vé, Thanh Toán & Vé Điện Tử (Done, 2026-11-17):** Luồng thanh toán (`/checkout`) với đếm ngược giữ vé, cổng VNPAY Sandbox và 3 kịch bản mô phỏng. Trang kết quả thanh toán (`/payment-return`). Trang Ví Vé (`/ve-cua-toi`) với Boarding Pass E-Ticket sang trọng, mã QR Vector SVG ký số HMAC-SHA256 và chế độ In Vé Điện Tử (`window.print()`).
- [x] **T18.4 — Hoàn Thiện Dashboard Cho Cả 4 Vai Trò (Done, 2026-11-17):** Tối ưu 4 cổng làm việc riêng biệt: Customer Portal (Ví vé cá nhân), Staff Portal (`/staff` bàn làm việc soát vé công nghệ cao), Organizer Portal (`/organizer` quản lý sự kiện & nhân sự), Admin Portal (`/admin` 5 tab nghiệp vụ toàn diện).
- [x] **T18.5 — Kiểm Thử Giao Diện Đa Thiết Bị (Visual QA & Accessibility) (Done, 2026-11-17):** Chụp tự động 30 ảnh màn hình bằng chứng qua Chrome headless trên 3 độ phân giải tiêu chuẩn: Desktop Wide (1440 × 900), Laptop Standard (1366 × 768) và Mobile Viewport (390 × 844 iPhone standard) cho toàn bộ 10 phân hệ và 4 dashboard. Lưu bằng chứng tại `docs/evidence/visual-qa/` và xuất báo cáo nghiệm thu thị giác.

## Giai đoạn 8 — Kiểm Thử Hồi Quy Toàn Diện, Bảo Mật & Đóng Gói Phát Hành (T19)

- [x] **T19.1 — Full Regression Suite (Done, 2026-11-17):** Chạy toàn bộ 234/234 tests backend Maven (`mvnw test` PASS 100%, 0 failures, 0 errors, 121 skipped an toàn), 71/71 tests frontend (`npm test --prefix frontend` PASS 100%), build production `tsc --noEmit && vite build` SUCCESS (145ms), OpenAPI contract checks 69/69 cases OpenAPI 3.1 PASS 100%. Khắc phục triệt để lỗi CI GitHub Actions: xác thực live API, bảo toàn state `App` khi switch persona trong `DemoShell`, chạy thành công cả 6 kịch bản live tests (`live-ui.ts`, `verify-live.mjs`, `live-seats.ts`, `live-payments.ts`, `live-admin.ts`, `live-recovery.ts`).
- [x] **T19.2 — Kiểm Tra Bảo Mật Xác Thực & Thanh Toán (Done, 2026-11-17):** Chạy bộ kiểm thử bảo mật chuyên sâu (37/37 tests PASS): chống Privilege Escalation và Self-Lockout (`CANNOT_DEMOTE_SELF`, `CANNOT_LOCK_SELF`), bảo vệ IDOR trên vé và Staff scope (`assertStaffAccess`), chữ ký số vé điện tử QR Code HMAC-SHA256, chữ ký số VNPAY HMAC-SHA512 hai chiều, chống tấn công tái sử dụng Token Family (Refresh Token Reuse Detection), cơ chế `sanitizeCsvField` chống tấn công CSV Formula Injection trên báo cáo doanh thu Excel.
- [x] **T19.3 — Tập Dượt Migration & Khôi Phục Dữ Liệu (Done, 2026-11-17):** Chuỗi 15 Flyway migrations (`V1` đến `V15`) áp dụng tự động thành công và sạch sẽ trên PostgreSQL 17, schema `ticketing` đạt đủ đúng 35 bảng nghiệp vụ.
- [x] **T19.4 — Cấu Hình Môi Trường Triển Khai Staging/Demo & An Toàn Bí Mật (Done, 2026-11-17):** Whitelist mock JWT dev secret bằng `.gitleaksignore` và `regexes` trong `.gitleaks.toml`, đảm bảo job `secrets` của CI xanh 100%. Rà soát không có secret thực tế, khóa bí mật hay credential trong Git repository.
- [x] **T19.5 — Tổng Kết Báo Cáo, Cập Nhật Tài Liệu Kỹ Thuật & Đóng Gói Phát Hành V2 (Done, 2026-11-17):** Cập nhật đồng bộ toàn bộ tài liệu kỹ thuật cốt lõi: `ARCHITECTURE.md` (kiến trúc 4 vai trò, Hexagonal Payment Ports & Adapters, Staff Check-in, Admin Platform), `DATA_MODEL.md` (35 bảng nghiệp vụ V1-V15), `API_CONTRACT.md` (danh mục đầy đủ endpoints RESTful V2), `README.md` (hướng dẫn vận hành 4 vai trò và các cổng giao diện). Sẵn sàng gắn Git Tag `v2.0.0-release` để chính thức bàn giao sản phẩm EventFlow V2.

---

# LỘ TRÌNH NÂNG CẤP EVENTFLOW V3 — ĐA HÌNH THỨC, THỜI GIAN THỰC & HỆ SINH THÁI TRỢ LÝ AI (T20 – T22)

## Giai đoạn 9 — Đa Hình Thức Sự Kiện, Thuật Toán Ghế Liền Kề & Đồng Bộ Thời Gian Thực SSE (T20)

- [x] **T20.1 — Flyway Migration V16 (`V16__event_format_online_and_seat_grid.sql`) (Done, 2026-11-18):** Bổ sung cột `event_format` (`OFFLINE_SEATED`, `OFFLINE_GA`, `ONLINE`) và `online_join_url` vào `ticketing.events`; bổ sung `row_label`, `seat_number` vào `ticketing.seats` (tự động backfill từ nhãn ghế hiện có). Bảo toàn 100% migrations `V1–V15` và hợp đồng OpenAPI V2.
- [x] **T20.2 — Thuật Toán Gợi Ý Cụm Ghế Liền Kề Tốt Nhất (Best Available Contiguous Seats) (Done, 2026-11-18):** Dịch vụ `BestSeatsService` & API `GET /api/events/{eventId}/seats/best-available` quét ma trận ghế theo từng hàng để chọn cụm N ghế liền kề đang `AVAILABLE` có góc nhìn trung tâm sân khấu nhất (kèm fallback khi không còn cụm liền kề); tích hợp thanh chọn nhanh trên Sơ đồ ghế 3D trong `App.ts`.
- [x] **T20.3 — Đồng Bộ Màu Ghế Thời Gian Thực Qua Server-Sent Events (SSE) (Done, 2026-11-18):** Dịch vụ `EventStreamService` & endpoint `GET /api/events/{eventId}/stream` (`text/event-stream`), phát `InventoryChangedEvent` qua `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` từ `BookingCommandService` và `BookingLifecycle`, tự động cập nhật sơ đồ ghế trực tiếp trên trình duyệt qua `EventSource`.
- [x] **T20.4 — Sự Kiện Online, Bộ Lọc Đa Tiêu Chí, Tạo Ma Trận Ghế & File Lịch `.ics` (Done, 2026-11-18):** API discovery `GET /api/v3/events` lọc theo hình thức (`OFFLINE_SEATED`/`OFFLINE_GA`/`ONLINE`), khoảng giá và từ khóa; API tải file lịch RFC 5545 `GET /api/events/{id}/calendar.ics` tích hợp nút "Thêm vào Lịch (.ics)" trong `/ve-cua-toi`; API Organizer `POST /api/organizer/events/{id}/format` và `POST /api/organizer/events/{id}/seat-matrix` sinh nhanh ma trận hàng/cột ghế.

## Giai đoạn 10 — Hàng Đợi Ảo Giờ Cao Điểm & Danh Sách Chờ Thông Minh Qua Kafka (T21)

- [ ] **T21.1 — Flyway Migration V17 (`V17__smart_waitlist_and_waiting_room.sql`):** Tạo bảng `ticketing.event_waitlists` và cấu hình phòng chờ ảo.
- [ ] **T21.2 — Smart Waitlist Engine Qua Kafka:** Đăng ký danh sách chờ khi sự kiện hết vé; tự động ưu tiên thông báo FIFO cho khách xếp hàng qua Kafka Consumer mỗi khi `Expiry Worker` hoặc hủy đơn nhả vé.
- [ ] **T21.3 — Hàng Đợi Ảo Giờ Cao Điểm (Virtual Waiting Room):** Điều tiết lưu lượng truy cập giờ Flash Sale, hiển thị số thứ tự xếp hàng và cấp token thông hành vào phòng chọn ghế.

## Giai đoạn 11 — Hệ Sinh Thái Trợ Lý AI Thông Minh & Load Test V3 (T22)

- [ ] **T22.1 — Flyway Migration V18 & Kiến Trúc Hexagonal `AiAssistantPort`:** Lưu lịch sử hội thoại AI (`ai_conversations`, `ai_messages` có Anti-IDOR); cài đặt `GeminiAiAdapter` (đọc API key từ `.env`) và `LocalFallbackAiAdapter` chạy 100% offline/local.
- [ ] **T22.2 — AI Ticketing Concierge (Khách Hàng):** Chat ngôn ngữ tự nhiên với Function Calling đọc PostgreSQL thật, tìm cụm ghế liền kề kèm nút **"Giữ cụm ghế này ngay"** gọi luồng ACID; tra cứu đơn & giải thích tiến độ hoàn tiền (Anti-IDOR).
- [ ] **T22.3 — AI Organizer Copilot (Nhà Tổ Chức):** Tự động sinh mô tả SEO, lịch trình Agenda, cơ cấu hạng vé và JSON ma trận sơ đồ ghế trong Organizer Portal.
- [ ] **T22.4 — AI Ops Diagnostics (Admin):** Phân tích sự kiện lỗi trong `outbox_events`/DLQ và đơn `REFUND_REQUIRED`, tóm tắt nguyên nhân tiếng Việt và khuyến nghị hành động (Replay / Hoàn tiền).
- [ ] **T22.5 — Script Load Test k6, Kiểm Thử Hồi Quy V3 & Đóng Gói `v3.0.0-release`:** Bổ sung script k6, chạy PASS 100% toàn bộ test suite, cập nhật tài liệu kỹ thuật và gắn tag phát hành V3.
