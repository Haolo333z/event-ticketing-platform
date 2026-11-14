# Bàn giao event-ticketing-platform

Chốt ngày **14/11/2026 (Asia/Saigon)** tại `D:\event-ticketing-platform`. Đây là snapshot bàn giao; lần làm tiếp phải kiểm tra lại Git và runtime. Đọc [RULES](RULES.md), [TASKS](TASKS.md), [PROGRESS](PROGRESS.md) trước khi sửa; hướng dẫn chạy đầy đủ ở [README](../README.md), [infra](../infra/README.md) và [runbook demo](DEMO_AND_RECOVERY.md).

## 1. Git và tiến độ

- Nhánh `main`, HEAD **`984e95d7dc7aca0b7ea94743e27e21d5d713d906`**. Không commit/push trong lượt bàn giao.
- Working tree **chưa sạch**: trước lượt này có 6 file sửa (`README.md`, `docs/DEMO_AND_RECOVERY.md`, `docs/PROGRESS.md`, `docs/TASKS.md`, `docs/TEST_PLAN.md`, `infra/README.md`); các evidence T9.4/T10.1–T10.3, `docs/report/`, `infra/demo-{rehearsal.ps1,audit.sql}` và `tools/report/` còn untracked. Giữ nguyên, không checkout/reset/clean. Hai file bàn giao này cũng chưa commit.
- CI được xác nhận gần nhất trong tài liệu: **run #39**, đúng SHA trên, cả backend/frontend/contracts/secrets success; xem [evidence CI39](evidence/t9-4-ci39-2026-11-14.json). Đây là bằng chứng của commit đó, **không chứng minh các thay đổi chưa commit đã chạy GitHub Actions**.

| Task | Trạng thái và giới hạn |
|---|---|
| T1.1–T7.4, T8.1–T8.4, T9.1–T9.4 | Done theo TASKS/PROGRESS; gate tích hợp, tải, lỗi và CI có evidence. Không làm lại nếu không phát hiện khoảng trống/lỗi thật. |
| T10.1 | Done: môi trường riêng sạch, migration/seed/restart/SQL đã kiểm; Chrome do người dùng xác nhận, không có file ảnh được agent xem. Evidence/tài liệu mới chưa commit. |
| T10.2 | Done: hai lượt HTTP/SQL đạt, có runbook/fixture riêng. Chưa có visual QA Chrome T10.2. Script/tài liệu/evidence chưa commit. |
| T10.3 | Done: báo cáo 43 trang Word/PDF đã kiểm. Chưa áp mẫu trường, chưa điền thông tin bìa, chưa kiểm Microsoft Word GUI. Báo cáo/tool/evidence chưa commit. |
| **T10.4** | **Todo, chưa thực hiện**. Task duy nhất còn lại: chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo; AC chính xác: **ZIP/source/tag tái lập được**, có bằng chứng và cập nhật PROGRESS. |

Tổng trong TASKS: **44/45 Done**, không phải tỷ lệ chất lượng. Các giới hạn chưa kiểm chứng không tự trở thành tính năng phải thêm trong T10.4.

## 2. Stack và các điều không được phá

Temurin **25.0.5+8**, Spring Boot **4.1.2**, Maven Wrapper **3.9.16**; PostgreSQL **17.11**, Kafka **4.2.2**; Node/npm **26.13.0/11.20.0**, React **19.3.1**, TypeScript **7.0.2**, Vite **8.3.8**. Docker/Compose đã đo **29.9.1/5.6.0**. Giữ BOM/lockfile/image pin; xem [ADR](adr/). Không .NET/Redis hoặc tự đổi stack.

Modular monolith: API và worker cùng backend; Java/Vite chạy host Windows, PostgreSQL/Kafka chạy Compose. PostgreSQL là nguồn sự thật, Kafka phân phối sự kiện sau commit. V1–V9 đã có **18 bảng**; không sửa migration đã chạy. OpenAPI có **18 operation** ở [contracts/openapi.json](contracts/openapi.json).

- GA: `held + sold <= capacity`; ghế có tối đa một owner hiện hành. Giữ nhiều ghế all-or-nothing, khóa ID theo thứ tự ổn định. Giá/số tiền VND do server tính và lưu snapshot.
- Booking, item, inventory, history, key/response và outbox phải commit/rollback nhất quán; timeout rollback trước khi trả 503. Cùng account/operation/key/body trả kết quả cũ; khác body trả 409. Cached receipt tạo đơn có thể vẫn PENDING_PAYMENT sau confirm; đọc GET để biết trạng thái mới.
- State: `PENDING_PAYMENT → CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED`; chỉ `EXPIRED → REFUND_REQUIRED`. Không tự thêm cạnh state.
- Expiry Worker chủ động quét, không cleanup inline trong đặt vé; hold quá giờ vẫn chưa trống trước khi worker commit. Kiểm state/owner để không nhả lần hai hoặc nhả tài nguyên của booking mới.
- Payment/refund **mô phỏng**. Dùng `payments.recorded_at` của DB và processor chung với expiry. Timely result đã bền có thể được worker xử lý dù Kafka ngừng. Success sau mất hold không confirm/chiếm lại vé; EXPIRED đi REFUND_REQUIRED, không vé, một nghĩa vụ/payment. BookingConfirmed chỉ thông báo, không phát vé lại.
- Outbox gửi sau commit; Kafka ACK và DB mark là hai bước nên có thể gửi lặp. Inbox `(consumer,eventId)` cùng transaction với hiệu ứng, commit trước ACK offset. **At-least-once + dedup**, không cam kết exactly-once xuyên hệ.
- Retry hữu hạn, failure DB trước ACK, DLQ sau commit. Replay giữ eventId/envelope/inbox, ADMIN local/test, audit và tối đa 3 request mới/event; không force/xóa inbox/reset budget. Audit projection kiểm gap/version riêng, không bỏ payment theo booking.version hiện tại.
- Danh tính/role từ mock bearer do server ánh xạ, chỉ local/test; không X-User-Id/role client tự khai. Không log token, raw payment hoặc gắn ID cá nhân vào metric label.

## 3. Windows và các môi trường tạm

**Quan sát lúc bàn giao:** hai backend health UP, bốn container PostgreSQL/Kafka healthy; listener đúng các cổng dưới. Đây là tiến trình/clone/volume trên máy hiện tại, **không phải deployment bền hoặc thứ được Git clone mang sang máy khác**.

| Môi trường | Clone / project | UI / API / PG / Kafka |
|---|---|---|
| T10.1 cần bảo toàn | `D:\ticketing-t101-94eeadabbaa1` / `ticketing-readme-94eeadabbaa1` | `http://localhost:5173` qua IPv6 /18080/15432/19092 |
| T10.2 tập riêng | `D:\ticketing-t102-e8b72037cdaf` / `ticketing-t102-e8b72037cdaf` | `http://127.0.0.1:5173` /18081/15433/19093 |

T10.2 dùng session ignored `reports/t10-2/session.json`; clone/session có thể không tồn tại trên máy mới. Không copy `.env` hoặc volume demo để tạo môi trường sạch. Cổng bận thì nhận diện owner và dừng, không kill nhầm/đổi port rồi bỏ qua CORS.

Từ root hiện tại, dùng session T10.2 đã có:

```powershell
git status --short
git rev-parse HEAD
docker version
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Start
Invoke-RestMethod http://127.0.0.1:18081/actuator/health
# Tập lượt mới: các lệnh này tạo fixture/booking trong môi trường T10.2 riêng
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action NewRun
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Rehearse -Paced
# Verify chỉ sau khi lượt chính hoàn tất; Stop giữ volume
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Verify
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Stop
```

Máy mới/session mới: đọc runbook rồi dùng `-Action Prepare` khi cổng trống; nó tạo snapshot working tree và project/volume riêng. Backend/frontend T10.2 chạy ẩn, không cần giữ terminal launcher. TTL 20 giây/expiry 1 giây là override process; `.env` baseline vẫn 600/5 giây. Không gọi Prepare lại lên session đã có.

Luồng mặc định README, **chỉ trong đúng workspace và khi cổng trống**: `infra/local-env.ps1` tạo `.env` nếu thiếu, `npm.cmd ci --prefix frontend` và `npm.cmd ci --prefix tools/contracts`; chạy `infra/start-local.ps1` và `infra/start-frontend.ps1` bằng `powershell -NoProfile -ExecutionPolicy Bypass -File` ở hai terminal giữ mở (API 8080/UI 127.0.0.1:5173, PG 5432/Kafka 9092). Dừng bằng Ctrl+C hoặc `infra/stop-local.ps1 -StopServices` trong chính workspace đó, giữ volume. Không dùng lệnh dừng mặc định ở root để thay lệnh dừng clone T10.1/T10.2.

## 4. Kiểm tra và nguồn bằng chứng

```powershell
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
# Gate đầy đủ trên chính working tree, môi trường riêng; không sửa source khi đang chạy
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-invariants.ps1 -WorkingTree
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-load.ps1 -Requests 50 -Rounds 3
```

`-WorkingTree` tránh chứng nhận bản sửa chưa commit bằng HEAD cũ. Launcher Maven có thể skip IT: không gọi đó là full backend PASS. `verify-local.ps1` chỉ đọc health/V1/schema; `verify-part4`…`verify-part8` lịch sử **ghi dữ liệu**, có script cố định cổng 8080, không chạy lên demo cần bảo toàn. Chi tiết [TEST_PLAN](TEST_PLAN.md); JUnit ở `backend/target/surefire-reports`, raw gate ở `reports/` ignored.

| Số liệu / artifact | Nguồn trong docs/evidence |
|---|---|
| Gate 130 backend, 54 frontend, 69 contract; phân biệt lượt chạy | `t9-1-invariants-2026-11-14.json`, `t9-3-recovery-2026-11-14.json`, `t9-4-audit-2026-11-14.json` |
| 3×50 request/mode; GA 30 success/120 conflict, ghế 3/147, 0 lỗi khác, oversell = 0 | `t9-2-load-2026-11-14.json`; mẫu CSV `t9-2-samples-2026-11-14.csv`, SQL `t9-2-audits-2026-11-14.sql`. Có p50/p95/p99/throughput/máy/pool. Burst loopback, cùng principal, không đo TPS duy trì. |
| Kafka/late/rollback/restart; 37 snapshot × 11 counter đều 0, replay no-op | `t9-3-recovery-2026-11-14.json`, `t9-3-snapshots-2026-11-14.json`, `t9-3-queries-2026-11-14.sql` |
| Demo sạch/restart, Chrome chỉ user xác nhận | `t10-1-clean-demo-2026-11-14.json`, `t10-1-chrome-user-2026-11-14.md` |
| T10.2: 8/8 bước, 365,499 giây gồm 310 giây xem/thuyết minh; launcher 9 chạy/121 skip | `t10-2-rehearsal-2026-11-14.json`. Chưa kiểm Chrome T10.2; không gộp với 130 test gate. |
| Báo cáo 43 trang, 8 sơ đồ/24 bảng/46 mục trong mục lục | `t10-3-report-2026-11-14.json`; hash Word/PDF/Markdown khớp khi bàn giao. |

Báo cáo: [Word](report/bao-cao-do-an.docx), [PDF](report/bao-cao-do-an.pdf), [nội dung Markdown](report/BAO_CAO.md), [hướng dẫn chỉnh/build](report/README.md). Nguồn dựng ở `tools/report/`, sơ đồ ở `docs/report/figures/`. Không chèn PNG QA hoặc ảnh Chrome chưa có vào báo cáo. File `Quy_Trinh_Xay_Dung_He_Thong_Dat_Ve_Le_Huu_Hao.docx` là kế hoạch cũ, không phải hiện trạng hoặc mẫu trường.

## 5. Việc tiếp theo cho T10.4

1. Điền **7 ô `[CẦN ĐIỀN]`** báo cáo: trường; khoa/viện; MSSV; lớp/khóa; ngành; giảng viên; địa điểm và thời gian nộp. Tên Lê Hữu Hào đã có nguồn PRD. Có mẫu trường thì áp mẫu, xuất lại Word/PDF và kiểm số trang 30–50, mục lục, bảng/hình/glyph từng trang; không sửa số đo để khớp mẫu.
2. Chốt backlog ngoài MVP và giới hạn (tiền thật, xác thực production, HA/mất đĩa, tải duy trì, giao diện khách với hơn 100 bản ghi…), ghi rõ chưa làm/chưa kiểm, không triển khai tính năng mới chỉ để đóng task.
3. Tái sử dụng sơ đồ có source; thu/đối chiếu ảnh demo **thực** khi có, ghi môi trường/ngày/nguồn, che bí mật. Nếu còn thiếu ảnh/kiểm Chrome thì ghi thiếu, không bịa hoặc đánh dấu đã xem.
4. Chuẩn bị source/ZIP có đúng candidate đã kiểm, không chỉ `git archive HEAD` khi report/harness còn untracked. Loại cấu hình riêng/cache/log/database/volume. Thử theo README/runbook từ bản sao sạch, cổng/project/volume riêng; lưu lệnh và kết quả. Không reset demo cũ.
5. Đề xuất mốc commit/tag trỏ đúng source đã kiểm; chỉ tạo/công bố khi có chỉ dẫn của chủ dự án. Kiểm CI của đúng commit mới khi có; không dùng CI39 chứng nhận working tree. Hiện chưa được commit/push và chưa có bằng chứng ZIP/source/tag cuối tái lập được.

**Chỉ Done T10.4 khi** đủ AC TASKS: ZIP/source/tag tái lập được, backlog/sơ đồ/ảnh được chốt với nguồn thật, kết quả kiểm tra và giới hạn ghi vào PROGRESS; không còn phần bắt buộc chưa kiểm mà vẫn ghi hoàn thành. Các bước trên là kế hoạch bàn giao, chưa được thực hiện trong lượt này.

## 6. Git hygiene và kiểm tra lượt bàn giao

Không đưa vào Git `.env`/`.env.*` riêng (chỉ `.env.example` placeholder), password/token/bí mật, raw log/raw payment, database dump/dữ liệu demo hoặc volume, `reports/`, `.local-runtime`, `target`, `node_modules`, `dist`. Chỉ chọn source, tài liệu, báo cáo và evidence đã lọc; không `git add .` thiếu rà soát, không force-add file ignored. Không xóa volume/inbox/key hoặc reset DB để làm test xanh.

Lượt bàn giao này chỉ thêm HANDOFF và AGENTS; không chạy lại suite nghiệp vụ, không Start/Stop/NewRun, không thay báo cáo hoặc `.env`. Đã kiểm `git status`/HEAD, nguồn tài liệu, listener/`docker ps`, HTTP health 18080/18081 UP và hash 3 artifact khớp evidence T10.3. Kiểm tra UTF-8, 14 liên kết nội bộ và `git diff --check` PASS; hash 258 file có sẵn không đổi, HEAD giữ nguyên. Gitleaks quét riêng hai file mới: exit 0, không có finding; kết quả cục bộ ở `reports/handoff/secrets.json` (ignored). Git chỉ cảnh báo LF→CRLF theo cấu hình hiện có. Các lệnh khởi động/test ở trên là hướng dẫn cho lượt sau, không phải test mới đã chạy.
