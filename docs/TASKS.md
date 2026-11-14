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
- [ ] **T9.4 — Sửa lỗi ưu tiên cao; rà migration/config và loại secret khỏi repo (In Progress, 2026-11-14):** gate local trên full working tree PASS (130 backend/54 frontend/69 contract/7 load-tool, V1–V9 sạch/restart, secret/dependency scan). Chờ GitHub Actions cho candidate sau khi chủ dự án tự commit/push; run #37 chỉ chứng minh HEAD502d34e. [Bằng chứng](evidence/t9-4-audit-2026-11-14.json).
  - **Mục tiêu/phạm vi:** Sửa lỗi ưu tiên cao; rà migration/config và loại secret khỏi repo. 
  - **Phụ thuộc:** T9.3. **Module/file:** integration-tests/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** CI xanh, không secret, migration sạch; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Tuần 10 — Đóng gói/báo cáo

- [ ] **T10.1 — Demo từ môi trường sạch** theo README.
  - **Mục tiêu/phạm vi:** Demo từ môi trường sạch theo README.
  - **Phụ thuộc:** T9.4. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** demo từ DB sạch theo runbook; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [ ] **T10.2 — Chuẩn bị dữ liệu, persona demo và kịch bản 5–10 phút.**
  - **Mục tiêu/phạm vi:** Chuẩn bị dữ liệu, persona demo và kịch bản 5–10 phút. 
  - **Phụ thuộc:** T10.1. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** kịch bản demo 5–10 phút có seed; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [ ] **T10.3 — Báo cáo** về bất biến, test concurrency tuần 3–4, outbox/recovery, kết quả và giới hạn.
  - **Mục tiêu/phạm vi:** Báo cáo về bất biến, test concurrency tuần 3–4, outbox/recovery, kết quả và giới hạn.
  - **Phụ thuộc:** T10.2. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** báo cáo số thực và giới hạn; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.
- [ ] **T10.4 — Chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo.**
  - **Mục tiêu/phạm vi:** Chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo. 
  - **Phụ thuộc:** T10.3. **Module/file:** docs/demo/infra; hợp đồng liên quan trong DATA_MODEL/API_CONTRACT/EVENT_CATALOG.
  - **Yêu cầu kỹ thuật:** giữ bất biến PRD và ranh giới transaction ARCHITECTURE; thay đổi schema bằng migration, API theo contract.
  - **Acceptance criteria và test bắt buộc:** ZIP/source/tag tái lập được; ghi kết quả thực vào PROGRESS. **Bàn giao/Done:** code hoặc tài liệu của task, bằng chứng test/kiểm tra và cập nhật PROGRESS.

## Ưu tiên khi trễ tiến độ

1. Inventory, idempotency, expiry và booking state machine.
2. Outbox, retry, consumer idempotency và ít nhất một kịch bản recovery.
3. Demo ghế/GA chạy được, concurrency evidence và báo cáo trung thực.
4. Cắt giảm trước dashboard nâng cao, trang trí UI, realtime push và analytics sâu.
