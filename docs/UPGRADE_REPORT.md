# UPGRADE_REPORT — Đối chiếu v0.2 sang v0.3 và v0.4

## Bản v0.4 — Quyết định sau vòng phản biện Word v0.3

Bản Word `Tong_Hop_Diem_Chinh_Sua_v0.3.docx` nêu 6 đề xuất E01–E06. Rà soát bổ sung E07 (dãy version ở checklist chỉ là gợi ý). Bản v0.4 áp dụng có điều chỉnh, không sao chép nguyên văn điểm 90/100 hoặc giả định có code/test.

| ID | Mức sau thẩm định | Quyết định và nơi sửa |
|---|---|---|
| E01 | P1 | Thực hiện: DATA_MODEL/PRD/ARCH/TASKS/TEST_PLAN quy định N vé GA bằng ticket_ordinal 1..N và unique composite. |
| E02 | P1 | Thực hiện: payment_compensations unique(payment_id), retry cùng nghĩa vụ; DATA_MODEL/ARCH/EVENT_CATALOG/TASKS/TEST_PLAN. |
| E03 | P2 | Tinh chỉnh: lock timeout cấu hình/đo, rollback, phân loại nguồn tranh chấp; 503 BOOKING_BUSY nếu chưa xác định được key. ARCH/API_CONTRACT/DATA_MODEL/TASKS/TEST_PLAN/RULES. Không áp cứng 3s hay mọi timeout = 409. |
| E04 | P2 | Tinh chỉnh: TICKET-01 tuần 5 test processor phát N vé; TICKET-02 tuần 6 replay BookingConfirmed chỉ audit. TEST_PLAN/TASKS/EVENT_CATALOG. |
| E05 | P1 | Tinh chỉnh: không dùng buffer; payment consumer dùng inbox/result bền/state, không lấy bookings.aggregate_version làm last-applied. Projection kiểm tra gap bằng version riêng chỉ khi nhận đủ chuỗi; EVENT_CATALOG/ARCH/TASKS/TEST_PLAN/RULES. |
| E06 | P3 | Để T1.4: chọn body limit và CORS origin local theo payload/frontend thực tế; không áp cứng 64 KB. API_CONTRACT/TASKS. |
| E07 | P3 | Giữ T1.1 chốt stack sau khi chạy thử; các số Java/Spring/PostgreSQL/Kafka trong Word không là ràng buộc. TASKS/PROGRESS. |

Tổng: 0 P0, 3 P1, 2 P2, 2 P3. Số này phân loại rủi ro trong tài liệu trước code; không phải điểm nghiệm thu hệ thống. Không tạo thêm file tài liệu, SQL migration hay source code; làm T1.1 rồi triển khai theo task.

## A. Tổng quan

Đầu vào: ZIP v0.2 gồm 6 Markdown và báo cáo kiểm định Word. Bản v0.3 giữ modular monolith, PostgreSQL, Kafka, hai loại vé và kế hoạch 10 tuần; sửa những lỗi phụ thuộc đã phát hiện. Không có source code trong hai bản. Tài liệu mới là hợp đồng để triển khai, chưa chứng minh hệ thống chạy.

## B. Đối chiếu đề xuất trong Word

Trạng thái: APPLIED, APPLIED_WITH_ADJUSTMENT, PARTIAL, NEEDS_CONFIRMATION, NOT_APPLIED.

| ID | Đề xuất kiểm định | File/mục | Cách xử lý | Trạng thái |
|---|---|---|---|---|
| R01 | Task cancel, admin CRUD, failure, my-bookings, simulator, ticket, replay | TASKS T2.4 T3.1 T5.1 T5.3 T5.5 T6.4; API_CONTRACT | Thêm task và endpoint, owner/role/profile | APPLIED |
| R02 | Outbox trước booking/expiry | TASKS T2.5; DATA_MODEL; EVENT_CATALOG | Schema/outbox write tuần 2–4, publisher/consumer tuần 6 | APPLIED |
| R03 | Payment trước Kafka consumer | TASKS T5.2 T6.3; ARCH §7 | Một PaymentResultProcessor dùng bởi adapter cục bộ, worker và Kafka consumer | APPLIED_WITH_ADJUSTMENT |
| R04 | Endpoint UI thiếu | API_CONTRACT; ARCH §9; DESIGN | My bookings, simulator, tickets, replay | APPLIED |
| R05 | Mốc payment đúng hạn | PRD §4.3; ARCH §7; DATA_MODEL | payments.recorded_at DB commit là mốc; worker xem result bền trước release; sau EXPIRED commit không nhận lại inventory | APPLIED_WITH_ADJUSTMENT |
| R06 | Hai request cùng idempotency key | ARCH §4; DATA_MODEL; TEST_PLAN IDEM-01 | Unique INSERT trong transaction chờ commit rồi replay; không lưu STARTED bền/409 in-flight | APPLIED_WITH_ADJUSTMENT |
| R07 | TTL idempotency/replay sau expiry | DATA_MODEL; API_CONTRACT | Không prune key trong MVP 10 tuần; replay original response, GET lấy state mới | APPLIED_WITH_ADJUSTMENT |
| R08 | State failure/cancel/refund | PRD §5; ARCH §7; API_CONTRACT | Bảng event × state; compensation status riêng; hủy CONFIRMED ngoài MVP | APPLIED_WITH_ADJUSTMENT |
| R09 | TTL, worker/batch, max quantity/pool | PRD §5; TASKS T1.1; TEST_PLAN | Giá trị gợi ý 10 phút, 5 giây, 50 booking, 6 vé; pool/latency chốt khi đo | PARTIAL |
| R10 | Admin sửa capacity/ghế/giá | PRD §5; API_CONTRACT; DATA_MODEL; TASKS T5.5 | Chặn capacity dưới held+sold, ghế đang giữ/bán; snapshot giá | APPLIED |
| R11 | Seats vs GA inventory và FK booking | DATA_MODEL; ARCH §4/§9 | Seat row riêng, inventory_ga riêng; insert booking trước seat FK, rollback cùng transaction | APPLIED |
| R12 | Một transaction cho mỗi expiry booking | ARCH §6; DATA_MODEL; TEST_PLAN | Batch chọn, transaction per booking | APPLIED |
| R13 | Publisher ordering và version gap | EVENT_CATALOG; ARCH §8; TASKS T6.2/3 | Một publisher MVP; gap được retry/recovery, không trông cậy SKIP LOCKED để bảo toàn thứ tự | APPLIED_WITH_ADJUSTMENT |
| R14 | Retention outbox/inbox/idempotency | DATA_MODEL; EVENT_CATALOG | Không xóa trong MVP; chính sách lâu dài cần ADR trước cleanup | APPLIED_WITH_ADJUSTMENT |
| R15 | 202 hay 201 | API_CONTRACT; TASKS T3.1 | Giữ 202 + Location vì hold commit nhưng payment pending, thống nhất contract cũ | NOT_APPLIED |
| R16 | CI, Testcontainers, secret/dependency scan | TASKS T1.2; TEST_PLAN; RULES | Ghi thành task/gate; công cụ test chốt theo máy | APPLIED_WITH_ADJUSTMENT |
| R17 | Expiry lag metrics | ARCH §11; TASKS T8.2; TEST_PLAN | overdue hold count và oldest age | APPLIED |
| R18 | Admin replay audit, local-only | API_CONTRACT; ARCH §11; DEMO_AND_RECOVERY | Kiểm tra role + profile, audit actor/correlation | APPLIED |
| R19 | Redis khỏi baseline | ARCH §1; PROGRESS | Chỉ thêm theo ADR khi có use case; không ràng buộc correctness | APPLIED_WITH_ADJUSTMENT |
| R20 | Một nguồn state machine | PRD §5; ARCH §7; README | PRD là nguồn; file khác dẫn chiếu | APPLIED |
| R21 | Wireframe | DESIGN; TASKS T7.2 | Để khi triển khai UI, không giả vờ đã kiểm chứng bố cục | PARTIAL |
| R22 | Cổng Compose, CORS/request limit | PRD §7; ARCH §11; RULES; TASKS T7.4 | Bind localhost; policy CORS và kích thước chốt trong T1.4/T7.4 | PARTIAL |
| R23 | GA hot row, pool size và 50 request | TEST_PLAN; PRD §6; TASKS T9.2 | Đo với PostgreSQL thật; nêu bottleneck và cấu hình máy | APPLIED |
| R24 | Xếp hạng 67/100, 0 code | PROGRESS; README | Ghi đúng trạng thái chưa triển khai; điểm cũ chỉ đánh giá docs v0.2 | APPLIED |

## C. File thay đổi

- Sửa: PRD, ARCHITECTURE, DESIGN, RULES, TASKS, PROGRESS.
- Thêm: README để điều hướng; DATA_MODEL cho bảng/constraint; API_CONTRACT cho route/error; EVENT_CATALOG cho producer/consumer; TEST_PLAN cho chứng cứ; DEMO_AND_RECOVERY cho runbook; UPGRADE_REPORT cho bảng đối chiếu.
- Không thêm SQL migration, OpenAPI machine-readable hay code giả; chúng thuộc các task triển khai và cần chạy thử.

## D. Quyết định và đánh đổi

- Giữ POST booking 202 vì payment bất đồng bộ, kèm Location. Sự lựa chọn 201 hợp lý cho tạo resource đồng bộ nhưng làm lệch hợp đồng đã chốt và UI tuần 3.
- Không dùng STARTED commit riêng: unique insert cùng transaction ngăn hai request đồng thời, tránh key mồ côi. Request thứ hai có thể chờ transaction đầu; cần timeout hữu hạn và test với PostgreSQL.
- Payment recorded_at đúng hạn được worker kiểm tra trước expiry; sau khi expiry đã commit, không thể xác nhận bằng timestamp cũ mà không gây oversell. Chuyển bù trừ và ghi lỗi đối soát.
- Không khẳng định SORT ASC của input hoặc UPDATE WHERE IN bảo đảm khóa đúng thứ tự; lock từng hàng theo ID hoặc kiểm chứng câu truy vấn. Retry deadlock hữu hạn.
- Không thêm REFUNDED vào booking state: REFUND_REQUIRED giữ đến khi bù trừ mô phỏng được ghi riêng ở payment/recovery. Không có hoàn tiền ngân hàng thật.
- Flash sale là kịch bản mở bán và concurrency, không mở rộng hàng chờ Internet trong 10 tuần.
- Java/Spring/PostgreSQL/Kafka/React giữ làm ứng viên; không sao chép version cũ trong tài liệu Word thành quyết định mới. T1.1 chốt sau kiểm tra tương thích.

## E. Kiểm tra và công việc còn lại

- 24 hạng mục được trích xuất từ báo cáo Word: APPLIED 11; APPLIED_WITH_ADJUSTMENT 9; PARTIAL 3; NOT_APPLIED 1; NEEDS_CONFIRMATION 0. Các mục PARTIAL có điều kiện ở T1.1/T1.4/T7.
- Đã đối chiếu 6 file gốc với 7 file mới, kiểm tra tham chiếu, tên route/state/event và ZIP có thể mở. Đây là kiểm tra tài liệu; không có test phần mềm.
- Chưa xác nhận: version stack, pool/latency, migration SQL thực, OpenAPI thực, khả năng lock order với SQL cuối, CI và demo trên máy người dùng.
- Chưa có kết quả benchmark hoặc bằng chứng bảo mật. Việc kế tiếp: T1.1, T1.4; tạo migration/outbox trước booking tuần 3 rồi chạy test thực tế.
