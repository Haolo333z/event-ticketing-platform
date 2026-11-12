# ADR 0001 — Stack và môi trường local

- Ngày: 2026-11-12 (Asia/Saigon)
- Trạng thái: Accepted — quyết định T1.1; chưa nghiệm thu ứng dụng.
- Bổ sung 2026-11-12 tại T1.2: đã xác minh java/javac Temurin 25.0.5+8 và Maven Wrapper 3.9.16 build/test scaffold thành công; xem PROGRESS. Các dòng môi trường/chưa cài bên dưới giữ làm lịch sử quan sát ở T1.1.
- Phạm vi: chốt phiên bản, tương thích, tài nguyên và tham số ban đầu. T1.2 chưa bắt đầu.

## Quyết định

| Thành phần | Phiên bản chốt | Cách sử dụng |
|---|---|---|
| Java | Eclipse Temurin JDK 25.0.5+8 LTS | Compile target/release 25; local, CI và runtime cùng JDK 25 |
| Spring Boot | 4.1.2 | Maven parent/BOM; Spring MVC, JDBC, validation, security, actuator |
| Maven | 3.9.16 | Pin bằng Maven Wrapper khi scaffold; không cần Maven global |
| PostgreSQL | 17.11 (`postgres:17.11`) | Nguồn sự thật, transaction và row lock; không cài DB native |
| Flyway | 12.4.0 | Theo Boot BOM; thêm flyway-database-postgresql cùng version |
| JDBC PostgreSQL | 42.7.14 | Theo Boot BOM |
| Spring Kafka / kafka-clients | 4.1.2 / 4.2.2 | Theo Boot BOM, không override riêng |
| Kafka broker | 4.2.2 (`apache/kafka:4.2.2`) | Một node KRaft broker/controller cho local; JVM trong image độc lập host |
| Node / npm | 26.13.0 / 11.20.0 | Giữ phiên bản đã cài; Node 26 thuộc nhánh LTS tại ngày kiểm tra |
| React / React DOM | 19.3.1 / 19.3.1 | UI demo |
| TypeScript / Vite / plugin React | 7.0.2 / 8.3.8 / 6.3.0 | Pin direct dependencies; lockfile được tạo khi scaffold |
| Docker Engine / Compose | 29.9.1 / 5.6.0 | Baseline máy đã kiểm tra; Linux containers, Compose v2 syntax qua `docker compose` |
| Redis | Không dùng | Chưa có cache/rate-limit use case cần Redis; bổ sung phải có ADR và test |

Giữ modular monolith, payment mô phỏng và các invariant trong PRD. JDBC giúp kiểm soát SQL/lock rõ ràng. OpenAPI/ER được chốt ở T1.4; chưa chọn thư viện sinh OpenAPI. Không dùng preview Java, native image hoặc Lombok trong baseline.

## Tương thích và nguồn kiểm chứng

1. [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html), trang hiển thị **4.1.2** ngày kiểm tra: Java 17–27, Maven >=3.6.3. Java 26.0.1 hiện có **phù hợp với phạm vi Boot công bố**; chưa chạy build/startup ứng dụng trên Java 26.
2. [Oracle Java roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html): 25 là LTS, 26 là non-LTS (Premier Support tới 09/2026). [Kafka 4.2 Java support](https://kafka.apache.org/42/operations/java-version/) hỗ trợ đầy đủ Java 17/21/25; mới hơn LTS là best effort. Vì vậy baseline chọn Java 25, không suy từ “Boot hỗ trợ Java 26” thành mọi dependency đã được kiểm chứng.
3. [Boot managed dependencies](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html), trang 4.1.2: Spring Kafka 4.1.2, Kafka client 4.2.2, Flyway 12.4.0, JDBC 42.7.14. Broker chọn cùng client 4.2.2. Tương thích end-to-end phải kiểm tra ở các task triển khai.
4. [PostgreSQL versioning](https://www.postgresql.org/support/versioning/): 17.11 đang được hỗ trợ, nhánh 17 tới 08/11/2029. PostgreSQL chạy container, không phụ thuộc JDK host; JDBC/Flyway theo BOM, migration sạch kiểm tra ở T1.3.
5. [Node release archive](https://nodejs.org/en/download/archive/v24.22.3) liệt kê nhánh 26 trong LTS. [Vite requirements](https://vite.dev/guide/) và npm registry `npm.cmd view vite@8.3.8 engines --json`: `^20.19.0 || >=22.12.0`, Node 26.13.0 đáp ứng. React/DOM, TypeScript, Vite và plugin được tra bằng `npm.cmd view`; chưa cài dependencies hay chạy frontend build.
6. [Maven download](https://maven.apache.org/download.cgi): 3.9.16 là nhánh stable còn được duy trì; đáp ứng Boot. [Adoptium API](https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse) trả `25.0.5+8.0.LTS` cho Windows x64.

Các URL không gắn version có thể đổi nội dung; phiên bản quan sát được ghi trong ADR này. Pin dependency bằng parent và lockfile khi triển khai; image không dùng `latest`. Manifest registry chỉ chứng minh tag/platform tồn tại, không chứng minh service đã chạy.

## Môi trường thực tế ngày kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| OS / CPU | Windows 11 Home Single Language; Intel i5-11400H, 6 core / 12 logical CPU |
| RAM host | 16.551.224 KiB ≈ 15,78 GiB; trống tại thời điểm đo 2.704.740 KiB ≈ 2,58 GiB |
| Disk D: | Trống 140.253.761.536 bytes ≈ 130,62 GiB |
| Docker | Client **và Server** 29.9.1; context desktop-linux; Linux x86_64; 12 CPU; RAM daemon 8.208.908.288 bytes ≈ 7,65 GiB |
| Compose / Git | 5.6.0 / 2.55.0.windows.3 |
| Java / javac | Oracle 26.0.1 / 26.0.1; thư mục Java liệt kê jdk-26.0.1 và latest; chưa xác minh JDK 25 được cài |
| Node / npm | 26.13.0 / 11.20.0, lệnh trả thành công |
| Maven | `mvn -version` không tìm thấy; repository chưa có wrapper |
| Containers | `docker ps` không có container chạy tại thời điểm kiểm tra |
| Cổng dự kiến | Không thấy TCP listener tại 5432, 9092, 8080, 5173 ở thời điểm kiểm tra; phải kiểm tra lại khi start |
| Kafka image | `docker manifest inspect apache/kafka:4.2.2` thành công, có linux/amd64 |
| PostgreSQL image | `docker manifest inspect postgres:17.11` thành công, có linux/amd64 |

Lệnh đã dùng: `java -version`, `javac -version`, `node --version`, `npm.cmd --version`, `mvn -version`, `git --version`, `docker version`, `docker compose version`, `docker info`, `docker ps`, `docker image ls`, `Get-CimInstance Win32_OperatingSystem`, `Get-CimInstance Win32_Processor`, `Get-PSDrive D`, `Get-NetTCPConnection -State Listen` và tra registry/API ở trên. Chưa pull/start DB/Kafka, chưa có code để build/test. Không ghi trạng thái Docker là blocker.

## Yêu cầu máy và ngân sách local

- Baseline dự án: host >=16 GiB RAM, >=4 logical CPU, >=20 GiB disk trống; khuyến nghị 32 GiB khi chạy IDE, browser và load test cùng lúc. Đây là ngân sách thiết kế, chưa benchmark.
- Docker >=6 GiB RAM, >=4 CPU. Máy đáp ứng RAM tổng/CPU/disk và allocation Docker; **RAM trống host 2,58 GiB còn thấp**. Đóng ứng dụng nặng để có khoảng 6 GiB available trước khi chạy toàn stack; không tăng allocation Docker trên máy 16 GiB ở thời điểm này.
- Ngân sách container dự kiến: PostgreSQL 1 GiB, Kafka 2 GiB (heap 512 MiB–1 GiB), backend 1,5 GiB (heap max 768 MiB), frontend 512 MiB; tổng 5 GiB, còn khoảng đệm cho daemon/page cache. Không cộng RAM WSL vào RAM vật lý host như tài nguyên riêng.
- Kafka một node, replication factor/min ISR = 1 chỉ cho local, không có HA. Bind host ports `127.0.0.1:5432`, `:9092`, `:8080`, `:5173`; bên trong Docker dùng service DNS/listener riêng.

## Tham số ban đầu

Đây là giá trị cấu hình khởi đầu, chưa đo throughput/latency và chưa được áp vào code.

| Tham số | Giá trị | Giới hạn/cách dùng |
|---|---|---|
| Hold TTL | 600 giây | PostgreSQL clock quyết định deadline, lưu UTC |
| Expiry worker interval | 5 giây fixed delay | Một worker ban đầu; không chạy cleanup trong booking command |
| Expiry batch | Tối đa 50 booking/lượt | Một transaction/booking; kiểm tra payment bền dưới lock trước khi nhả |
| Worker concurrency | 1 | Tránh chiếm hết pool; khi tăng phải kiểm tra lại lock/race |
| Max quantity | 6 vé/booking | Tổng quantity của mọi item, áp dụng cả GA và ghế; server validate |
| Hikari maximumPoolSize / minimumIdle | 10 / 2 | Một backend; dùng chung API/worker, không tạo pool riêng mỗi module |
| Hikari connectionTimeout | 30.000 ms | Timeout lấy connection, **không phải** DB lock timeout |
| PostgreSQL max_connections | 50 | Đủ một app pool 10 và quản trị/test; kiểm tra lại khi tăng instance |
| Deadlock/serialization retry | Tối đa 3 lần thực thi tổng cộng | Retry toàn transaction sau rollback, cùng idempotency key; backoff 50/100 ms + jitter |
| Outbox poll / batch / instances | 1 giây / 50 / 1 | Gửi tuần tự theo aggregateVersion trong mỗi booking, ack trước published |
| Kafka consumer concurrency | 1 | Inbox và nghiệp vụ cùng transaction; at-least-once |
| Kafka partitions / retention | 3 / 7 ngày | Topic nghiệp vụ/retry/DLQ, RF=1 local; outbox/inbox/idempotency DB không cleanup trong MVP |

Lock timeout, statement/request timeout, body limit, CORS và error mapping chi tiết chốt ở T1.4 theo payload và đo thực tế; không áp cứng 3s/64 KB. Timeout phải rollback; chưa phân loại được trả BOOKING_BUSY theo contract, không suy mọi timeout là idempotency conflict. Mục tiêu 50 request đồng thời của PRD không đồng nghĩa cần 50 DB connection.

## Acceptance và bước tiếp theo

- Đạt T1.1: ADR tương thích Java/Spring, dependency/broker/Node; đo RAM/CPU/disk/Docker; quyết định Redis; chốt phiên bản và tham số; cập nhật TASKS/PROGRESS.
- Còn thiếu trước build baseline: cài/chọn JDK 25.0.5+8; tạo Maven Wrapper 3.9.16 ở task scaffold. Maven global không bắt buộc. Cần giải phóng RAM trước demo.
- T1.3 kiểm chứng startup/health/migration DB sạch; các task Kafka kiểm chứng publish/consume; concurrency/load test xác nhận pool/worker/TTL. Những kiểm tra này chưa được thực hiện và không phải bằng chứng đã có của T1.1.
- T1.2 vẫn Todo. Không tạo backend/frontend/infra, Compose, CI hoặc cài công cụ trong lần xử lý này.

Lệnh tiếp theo (thực hiện thủ công khi chuẩn bị môi trường; kiểm tra phiên bản sau cài):

```powershell
winget show --id EclipseAdoptium.Temurin.25.JDK --exact --versions
```

Chọn gói 25.0.5+8 trong danh sách (định dạng version của winget có thể khác), cài bằng `winget install --id EclipseAdoptium.Temurin.25.JDK --exact --version <version-vừa-xem>`. Mở terminal mới, đặt JAVA_HOME/PATH về thư mục JDK 25 vừa cài nếu vẫn ưu tiên Java 26, rồi chạy:

```powershell
java -version
javac -version
node --version
npm.cmd --version
docker version
docker compose version
docker pull postgres:17.11
docker pull apache/kafka:4.2.2
```

Kỳ vọng Java/javac 25.0.5, Node 26.13.0, npm 11.20.0. Pull chỉ chuẩn bị image, không start service. `mvnw.cmd` và Compose file chưa tồn tại, chỉ dùng sau task scaffold tương ứng.
