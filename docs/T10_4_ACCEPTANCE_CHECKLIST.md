# Kế hoạch Nghiệm thu T10.4, Rà soát Bằng chứng và Chốt Backlog ngoài MVP

> **Phiên bản**: 1.0 · Ngày lập: **15/11/2026 (Asia/Saigon)**  
> **Dự án**: `event-ticketing-platform`  
> **Phạm vi tài liệu**: Thuộc pha **T10.4A** (Pha 1 của Task T10.4).  
> **Trạng thái task T10.4**: `[ ] In Progress` (chưa đánh dấu hoàn thành trên `docs/TASKS.md`).  
> **Mốc Git Checkpoint kiểm tra**: HEAD `46704bee8181176a1ef819e0a245fefcaaf64a51` (commit *"hoàn thiện demo báo cáo và tài liệu bàn giao"*).

---

## Phần 1: Bảng đối soát Checkpoint & Evidence T10.1 – T10.3

### 1.1 Tính toàn vẹn Git Checkpoint và Artifacts Báo cáo

Tại thời điểm thực hiện pha T10.4A, mã nguồn và tài liệu tại HEAD `46704bee8181176a1ef819e0a245fefcaaf64a51` ở trạng thái clean (`working tree clean`).

Các file artifact báo cáo của đồ án được kiểm tra mã băm SHA-256 thực tế trên đĩa và đối chiếu trực tiếp với biên bản kiểm chứng tại [docs/evidence/t10-3-report-2026-11-14.json](evidence/t10-3-report-2026-11-14.json):

| Artifact báo cáo | Dung lượng | SHA-256 thực tế đo trên đĩa | SHA-256 ghi nhận tại Evidence T10.3 | Kết luận |
|---|---:|---|---|---|
| [docs/report/bao-cao-do-an.docx](report/bao-cao-do-an.docx) | 707.011 bytes | `14ce6c15d41ee283bf30f16ff59cc8fbcc61eecb3bb3ea9ef48112afd61a95a3` | `14ce6c15d41ee283bf30f16ff59cc8fbcc61eecb3bb3ea9ef48112afd61a95a3` | **PASS (Khớp 100%)** |
| [docs/report/bao-cao-do-an.pdf](report/bao-cao-do-an.pdf) | 883.996 bytes | `82766fa4987e4ac6d03d1052c0a9a32ee0eb149708fccd39f367ad3907484c2e` | `82766fa4987e4ac6d03d1052c0a9a32ee0eb149708fccd39f367ad3907484c2e` | **PASS (Khớp 100%)** |
| [docs/report/BAO_CAO.md](report/BAO_CAO.md) | 94.212 bytes | `76bba4f72c360542f3599a6e2a14aa83f654a2812f14fa391efd9cc6ec464285` | `76bba4f72c360542f3599a6e2a14aa83f654a2812f14fa391efd9cc6ec464285` | **PASS (Khớp 100%)** |

> [!NOTE]
> Các giá trị băm khớp chính xác, xác nhận các file báo cáo định dạng Word, PDF và Markdown không bị sửa đổi ngoài ý muốn kể từ mốc bàn giao T10.3.

---

### 1.2 Bảng tổng hợp số liệu kỹ thuật đã kiểm chứng (T9.1 – T10.3)

| Hạng mục kỹ thuật | Quy mô / Số liệu thực tế đã kiểm chứng | Nguồn bằng chứng chính thức | Ghi chú & Giới hạn đã xác nhận |
|---|---|---|---|
| **Cơ sở dữ liệu & Schema** | 18 bảng nghiệp vụ, Flyway migrations `V1`–`V9`, schema `ticketing`. | [docs/evidence/t9-1-invariants-2026-11-14.json](evidence/t9-1-invariants-2026-11-14.json) | Không sửa migration đã áp dụng; restart ứng dụng kiểm tra tính idempotent không chạy lại migration. |
| **Kiểm thử tự động** | 130 test backend, 54 test frontend, 69 ca test contract OpenAPI 3.1 (18 operations). | [docs/evidence/t9-4-audit-2026-11-14.json](evidence/t9-4-audit-2026-11-14.json), [docs/evidence/t9-4-ci39-2026-11-14.json](evidence/t9-4-ci39-2026-11-14.json) | Chạy trên PostgreSQL và Kafka thật; CI run #39 thành công toàn bộ 4 jobs (backend, frontend, contracts, secrets). |
| **Kiểm thử tải đồng thời (T9.2)** | 3 vòng × 50 requests/mode cho GA và ghế (tổng 300 requests đo). Kết quả: GA 30 success / 120 conflict; Ghế 3 success / 147 conflict. | [docs/evidence/t9-2-load-2026-11-14.json](evidence/t9-2-load-2026-11-14.json), [docs/evidence/t9-2-samples-2026-11-14.csv](evidence/t9-2-samples-2026-11-14.csv) | **Oversell = 0**, Duplicate booking = 0, Partial hold = 0. Đo đạc độ trễ p50, p95, p99 thực tế trong điều kiện loopback cùng principal. |
| **Khôi phục sự cố & Bất biến (T9.3)** | 37 snapshot × 11 counter vi phạm = 407 lần kiểm tra; tất cả counter đều = 0. | [docs/evidence/t9-3-recovery-2026-11-14.json](evidence/t9-3-recovery-2026-11-14.json), [docs/evidence/t9-3-snapshots-2026-11-14.json](evidence/t9-3-snapshots-2026-11-14.json) | Broker Kafka ngừng/phục hồi, outbox lưu bền, inbox deduplication no-op khi replay, timely payment được worker xử lý ngay khi broker tạm ngừng. |
| **Demo môi trường sạch (T10.1)** | Khởi chạy từ clone sạch `ticketing-readme-94eeadabbaa1` độc lập. Seed sạch GA và Ghế; 2 booking `CONFIRMED`. | [docs/evidence/t10-1-clean-demo-2026-11-14.json](evidence/t10-1-clean-demo-2026-11-14.json), [docs/evidence/t10-1-chrome-user-2026-11-14.md](evidence/t10-1-chrome-user-2026-11-14.md) | Restart an toàn: 18 bảng và 9 bản ghi Flyway giữ nguyên. Xác nhận Chrome do người dùng thực hiện trực tiếp. |
| **Diễn tập kịch bản demo (T10.2)** | 8/8 bước hoàn thành; lượt có định giờ thuyết minh đạt 365,499 giây (6 phút 5,499 giây), gồm 310 giây thuyết minh. | [docs/evidence/t10-2-rehearsal-2026-11-14.json](evidence/t10-2-rehearsal-2026-11-14.json), [docs/DEMO_AND_RECOVERY.md](DEMO_AND_RECOVERY.md) | Thực hiện trên project và fixture độc lập `ticketing-t102-e8b72037cdaf`; kiểm tra stop/start an toàn giữ nguyên volume. |
| **Báo cáo đồ án (T10.3)** | 43 trang Word/PDF chuẩn A4; 8 sơ đồ kiến trúc, 24 bảng dữ liệu, 46 mục lục khớp số trang chính xác. | [docs/evidence/t10-3-report-2026-11-14.json](evidence/t10-3-report-2026-11-14.json) | Toàn bộ số liệu định lượng trích xuất tự động từ JSON evidence; font Times New Roman 13, rà soát trực quan 43 trang không lỗi. |

---

### 1.3 Rà soát 7 trường thông tin bìa `[CẦN ĐIỀN]`

Trong tài liệu báo cáo [docs/report/BAO_CAO.md](report/BAO_CAO.md) và file nguồn [tools/report/content.md](../tools/report/content.md), phần bìa hiện có 7 trường giữ chỗ thông tin:

1. `[CẦN ĐIỀN TÊN TRƯỜNG]`: Tên trường Đại học / Học viện.
2. `[CẦN ĐIỀN KHOA HOẶC VIỆN]`: Tên Khoa hoặc Viện quản lý ngành đào tạo.
3. `[CẦN ĐIỀN]` (Mã sinh viên): Mã số sinh viên của tác giả.
4. `[CẦN ĐIỀN]` (Lớp và khóa học): Lớp sinh hoạt và niên khóa đào tạo.
5. `[CẦN ĐIỀN]` (Ngành hoặc chuyên ngành): Ngành đào tạo (ví dụ: Công nghệ thông tin / Kỹ thuật phần mềm).
6. `[CẦN ĐIỀN]` (Giảng viên hướng dẫn): Học hàm, học vị và họ tên giảng viên hướng dẫn.
7. `[CẦN ĐIỀN]` (Địa điểm và thời gian nộp): Địa danh và tháng/năm bảo vệ (ví dụ: *Hà Nội, Tháng 11/2026*).

*(Ghi nhận: Họ tên sinh viên thực hiện đã được xác định thống nhất là **Lê Hữu Hào** theo đúng PRD và Báo cáo).*

> [!IMPORTANT]
> **Quy tắc bảo toàn thông tin hành chính**:
> Đây là các thông tin hành chính cá nhân và thể chế đào tạo. Theo quy tắc đạo đức học thuật và ranh giới kỹ thuật của dự án:
> - **Tuyệt đối không tự ý bịa đặt hoặc điền thông tin giả định** vào các file báo cáo trong mã nguồn repository.
> - Các trường này được giữ nguyên ở dạng placeholder `[CẦN ĐIỀN]`. Tác giả sẽ điền thông tin chính thức khi áp dụng bìa mẫu của trường trước khi in hoặc xuất bản tài liệu nộp hội đồng.

---

## Phần 2: Chốt Backlog ngoài MVP & Giới hạn Kỹ thuật (Out-of-Scope Backlog)

Để đảm bảo tính trung thực kỹ thuật của đồ án và xác định ranh giới nghiệm thu rõ ràng, danh mục sau đây được chốt chính thức là **Nằm ngoài phạm vi MVP** (dựa trên [docs/PRD.md](PRD.md) mục 3.2, [docs/ARCHITECTURE.md](ARCHITECTURE.md) và Chương 8 của Báo cáo):

### 2.1 Cổng thanh toán & Tài chính
- **Không hỗ trợ thanh toán tiền thật**: Hệ thống chỉ tích hợp cổng thanh toán mô phỏng (`Payment Simulator`) phục vụ kiểm thử và trình diễn đồ án.
- **Không lưu trữ thông tin thẻ**: Không lưu số thẻ tín dụng, ngày hết hạn, mã CVV hoặc thông tin ngân hàng của người dùng.
- **Không có đối soát thanh toán thực tế (Financial Settlement)**: Bù trừ và hoàn tiền (`REFUND_REQUIRED` -> `COMPLETED`) chỉ là mô phỏng cập nhật trạng thái nghĩa vụ bù trừ trong cơ sở dữ liệu nội bộ.

### 2.2 Nghiệp vụ sự kiện nâng cao
- **Không hỗ trợ Đa nhà tổ chức (Multi-tenancy)**: Hệ thống phục vụ một đơn vị tổ chức duy nhất, quản trị danh mục và cấu hình qua vai trò ADMIN demo.
- **Không phát hành QR code bảo mật cho cổng soát vé Production**: Vé phát hành chỉ có chuỗi mã vé định danh duy nhất (`ticket_code`), chưa mã hóa khóa công khai / HMAC chống làm giả bằng máy quét chuyên dụng.
- **Không có thị trường chuyển nhượng vé (Secondary Market)**: Không hỗ trợ tính năng chuyển quyền sở hữu vé, bán lại vé giữa các cá nhân.
- **Không có định giá động (Dynamic Pricing)**: Giá vé được lưu cố định theo từng loại vé (`ticket_types.price_amount`) tại thời điểm tạo sự kiện.

### 2.3 Kiến trúc hạ tầng & Tính sẵn sàng cao (High Availability)
- **Kiến trúc Modular Monolith**: Ứng dụng tích hợp toàn bộ HTTP API và Background Workers (Expiry Worker, Outbox Publisher, Inbound Event Consumer, Refund Worker) trong cùng một tiến trình Spring Boot chạy trên máy chủ Windows host. Không tách thành các Microservices độc lập triển khai phân tán.
- **Không hỗ trợ Kubernetes / Phân tán Multi-region**: Không sử dụng k8s, không có cơ chế điều phối container động hay cơ chế phân vùng địa lý.
- **Cấu hình Kafka đơn Broker**: Apache Kafka chạy trên Docker với Replication Factor = 1 (`RF1`), không có broker dự phòng. Chưa thử nghiệm các kịch bản phân vùng mạng (network partition / split-brain) giữa cụm broker.
- **Không cam kết chịu mất ổ đĩa (Disk Loss)**: Độ bền dữ liệu phụ thuộc vào volume PostgreSQL cục bộ; không triển khai sao lưu dự phòng thời gian thực qua nhiều trung tâm dữ liệu.

### 2.4 Quy mô & Tải chịu đựng
- **Quy mô đo kiểm tải**: Hệ thống đã được kiểm chứng với burst 50 requests đồng thời trong môi trường loopback máy đơn (đạt 0 oversell). Hệ thống **không cam kết và chưa kiểm chứng** tải duy trì quy mô Internet (hàng trăm ngàn người dùng tranh vé flash-sale cùng lúc).
- **Không có phòng chờ ảo (Virtual Waiting Room)**: Việc chống quá tải dựa trên HTTP connection limit và cơ chế lock timeout nhanh (1.500 ms) của PostgreSQL, không có hàng đợi phân tầng trước tầng ứng dụng.

### 2.5 Cam kết thông điệp (Messaging Guarantee)
- **At-least-once kết hợp Deduplication**: Hệ thống đảm bảo phân phối sự kiện ít nhất một lần qua Transactional Outbox và triệt tiêu xử lý lặp bằng bảng `inbox_events` trong cùng giao dịch PostgreSQL.
- **Không cam kết Exactly-once xuyên hệ thống**: Không tuyên bố tính năng exactly-once giữa các hệ thống lưu trữ phân tán độc lập (PostgreSQL và Kafka).

### 2.6 Giao diện người dùng & Khách hàng
- **Giới hạn số lượng bản ghi lịch sử**: API `GET /api/me/bookings` hiện trả về danh sách đơn hàng được giới hạn (tối đa 100 bản ghi), chưa triển khai phân trang vô tận (infinite scroll / cursor-based pagination).
- **Kiểm thử trực quan đa nền tảng**: Giao diện React đã kiểm thử đơn vị, DOM và chạy thử trên Chrome máy cá nhân; chưa triển khai kiểm thử tự động trực quan (Visual regression testing) đa trình duyệt/thiết bị di động.

> [!CAUTION]
> **Nguyên tắc đóng task**: Tuyệt đối không bổ sung các tính năng ngoài MVP trên đây chỉ nhằm mục đích làm dày sản phẩm. Mọi đánh giá nghiệm thu phải bám sát các bất biến và mục tiêu cốt lõi đã hoàn thành.

---

## Phần 3: Checklist Thực thi các Pha Tiếp theo của T10.4

Để hoàn tất trọn vẹn task **T10.4**, các pha tiếp theo cần thực hiện tuần tự theo kế hoạch nghiệm thu sau:

### Pha T10.4B: Đóng gói mã nguồn & Kiểm chứng tái lập từ môi trường sạch

- [ ] **Danh sách loại trừ khi đóng gói ZIP mã nguồn**:
  - Thư mục quản lý phiên bản: `.git/`
  - Thư viện phụ thuộc tải ngoài: `frontend/node_modules/`, `tools/contracts/node_modules/`
  - Thư mục build và artifact tạm: `backend/target/`, `dist/`
  - Kết quả kiểm thử và render cục bộ: `reports/` (gồm `reports/t9-4/`, `reports/t10-2/`, `reports/t10-3/`,...)
  - Môi trường thực thi tạm thời: `.local-runtime/`
  - Cấu hình môi trường chứa mật khẩu thật: `.env`, `.env.*` (chỉ giữ lại `.env.example` với giá trị placeholder an toàn)
  - Volume cơ sở dữ liệu và dữ liệu thử nghiệm cá nhân
- [ ] **Kịch bản kiểm chứng tái lập độc lập**:
  - Đóng gói file `event-ticketing-platform-source.zip` sạch.
  - Giải nén ra một thư mục tạm độc lập trên máy (không ghi đè repo chính).
  - Khởi tạo `.env` từ `.env.example` qua script `infra/local-env.ps1`.
  - Cài đặt dependency bằng `npm ci`.
  - Khởi động backend và frontend trên cổng thử nghiệm độc lập hoặc sau khi cổng chính đã được dọn sạch.
  - Kiểm tra Flyway migration chạy sạch `V1`–`V9`, seed dữ liệu chuẩn thành công, Actuator health báo `UP`.

### Pha T10.4C: Kiểm chứng hình ảnh minh họa & Sơ đồ kỹ thuật

- [ ] **Đối chiếu 8 sơ đồ kiến trúc tại `docs/report/figures/`**:
  - `architecture.png`: Kiến trúc tổng thể Modular Monolith.
  - `eda.png`: Luồng phân phối sự kiện hướng sự kiện qua Outbox/Kafka/Inbox.
  - `er.png`: Sơ đồ thực thể liên kết 18 bảng nghiệp vụ.
  - `payment.png`: Quy trình phân xử thanh toán mô phỏng đúng hạn và trễ hạn.
  - `recovery.png`: Quy trình xử lý lỗi và phục hồi dữ liệu qua Replay API.
  - `seat-race.png`: Cơ chế chống race condition khi đặt ghế.
  - `states.png`: Máy trạng thái vòng đời Booking.
  - `transaction.png`: Ranh giới giao dịch cơ sở dữ liệu PostgreSQL.
  - *Xác nhận*: Tất cả 8 sơ đồ đều có nguồn gốc từ tài liệu thiết kế nội bộ và mã nguồn dự án.
- [ ] **Rà soát tính trung thực của bằng chứng hình ảnh Chrome**:
  - Bám sát biên bản [docs/evidence/t10-1-chrome-user-2026-11-14.md](evidence/t10-1-chrome-user-2026-11-14.md).
  - Không tự ý chèn ảnh chụp giả mạo hoặc tuyên bố agent đã tự quan sát màn hình Chrome khi công cụ tự động hóa không hỗ trợ chụp ảnh trực tiếp.

### Pha T10.4D: Đóng mốc nghiệm thu & Phát hành (Release Tag)

- [ ] **Cập nhật tài liệu tiến độ**:
  - Ghi nhận hoàn thành trọn vẹn T10.4 trong [docs/PROGRESS.md](PROGRESS.md).
  - Đổi trạng thái trong [docs/TASKS.md](TASKS.md) sang `[x] T10.4 — Chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo (Done, 2026-11-15)`.
  - Đạt chỉ số hoàn thành: **45/45 tasks Done (100%)**.
- [ ] **Đề xuất mốc Commit và Release Tag chính thức**:
  - Đề xuất commit message: `T10.4: hoàn tất nghiệm thu, chốt backlog ngoài phạm vi và đóng gói phát hành`
  - Đề xuất Git Release Tag: `v1.0.0-demo`
  - *Lưu ý*: Chỉ thực hiện commit và gắn tag khi có chỉ dẫn phê duyệt cuối cùng từ Tech Lead và Chủ dự án.
