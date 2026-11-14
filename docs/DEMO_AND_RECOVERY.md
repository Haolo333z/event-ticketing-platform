# DEMO_AND_RECOVERY — Vận hành và trình diễn

## T6 — Quy trình đã triển khai

Dashboard/metrics/recovery UI hiện đã triển khai tại T7–T8; xem README gốc để chạy Windows và PROGRESS để biết bằng chứng. Các lệnh T6 bên dưới giữ làm runbook lỗi; không reset database/volume. verify-part7 ghi fixture, không dùng trên demo cần bảo toàn dữ liệu.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -HoldSeconds 20 -ExpiryIntervalSeconds 1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part7.ps1
```

Outage tự động và đối chiếu PostgreSQL: `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part7.ps1 -Tests KafkaRecoveryTest`. Script thực sự docker stop/start broker test hai lần, giữ broker demo.

Nếu tự thử demo: `docker compose --env-file .env -f infra/docker-compose.yml stop kafka`, tạo booking/payment qua UI, rồi `docker compose --env-file .env -f infra/docker-compose.yml start kafka`. Giữ volume. Publisher tối đa3attempt/backoff1/2s, PENDING rồi FAILED nếu chưa phục hồi; version sau cùng booking chờ. Expiry vẫn xử lý payment bền đúng hạn khi Kafka offline.

Xem outbox/replay bằng fixture token công khai local/test:

```powershell
$admin=@{Authorization='Bearer demo-admin-token'}
$page=Invoke-RestMethod 'http://127.0.0.1:8080/api/admin/operations/outbox?page=0&size=100' -Headers $admin
$page.items | Format-Table eventId,bookingId,aggregateVersion,eventType,status,attempts
$failed=$page.items | Where-Object status -eq 'FAILED' | Sort-Object bookingId,aggregateVersion | Select-Object -First 1
if($failed){
  $body=@{requestId=[Guid]::NewGuid().ToString();reason='Kafka recovered; retry original event'}|ConvertTo-Json
  Invoke-RestMethod "http://127.0.0.1:8080/api/admin/operations/events/$($failed.eventId)/replay" -Method Post -Headers $admin -ContentType 'application/json' -Body $body
}
```

Phân trang khi hơn100event; chọn version thấp nhất chưa publish. Replay tối đa3requestId khác nhau/event; HTTP chưa rõ kết quả dùng lại **cùng body**. API202 là đã ghi audit/requeue, không phải consumer hoàn tất. Giữ event ID/inbox; side effect đã commit sẽ no-op.

Consumer lỗi: đọc message_failures (consumer/topic/partition/offset/event_id/error/status/dlq_attempts) bằng psql. Raw record giữ trong DB, không in vào log công khai. Sửa nguyên nhân rồi replay outbox hợp lệ gốc. Gap audit: xem event_projections.last_applied_version, replay missing version trước rồi lần lượt event cao hơn bị park. RESOLVED chỉ sau handler commit. Poison schema/key không có outbox hợp lệ cần điều tra/cập nhật consumer; API không sửa payload hoặc ép thực thi.

DLQ ticketing.booking.dlq.v1 là thông báo raw/metadata, gửi từ failureDB sau commit, tối đa3attempt. Kafka/DLQ chưa gửi được thì DB vẫn giữ lỗi. Không tự reset counter bằng SQL. Refund FAILED sau3attempt giữ nghĩa vụ/history để điều tra, không tạo nghĩa vụ thay thế; chưa có API ép refund. REFUND_REQUIRED giữ sau COMPLETED; xem payment_compensations và compensation_attempts. Không dùng tiền thật.

Giới hạn: một brokerRF1, chưa chứng minh chịu mất ổ đĩa/volume, cluster nhiều node hoặc chaos dài hạn. Retention Kafka7ngày; T6 không tự xóa outbox/inbox/recovery. Demo token chỉ local/test.

> Runbook dự kiến trước khi có source code. Lệnh chính xác và screenshot bổ sung ở tuần 8–10; không nhận đây là quy trình đã chạy.

## Chuẩn bị

1. Chốt version ở T1.1, khởi động Docker Compose chỉ trên localhost. Đảm bảo PostgreSQL/Kafka healthy, backend migration thành công.
2. Seed một event ghế, một event GA, customer/admin mock token local/test; không triển khai profile demo ra Internet.
3. Mở OpenAPI, UI, health, dashboard; kiểm tra profile local/test và dữ liệu seed.
4. Reset dữ liệu demo bằng script được tài liệu hóa; không xóa dữ liệu người dùng thật.

## Kịch bản 5–10 phút

1. Xem event và availability; đặt hai ghế bằng customer token, thấy PENDING_PAYMENT và 202.
2. Gửi hai request cạnh tranh cùng ghế; một thành công, một conflict. Gửi cùng idempotency key để quan sát replay.
3. Đặt GA với nhiều request vượt capacity, xem count sau test.
4. Đợi hết hạn: trước khi worker commit, ghế cũ chưa đặt lại được; sau commit, ghế AVAILABLE và booking EXPIRED.
5. Kích hoạt payment success muộn; booking REFUND_REQUIRED, không có vé; xem history/outbox.
6. Dừng broker, tạo booking, kiểm tra outbox pending; bật lại và quan sát publisher/consumer hồi phục, không gây hiệu ứng lặp.

## Recovery

| Sự cố | Quan sát | Hành động có kiểm soát |
|---|---|---|
| Kafka tắt | outbox pending/oldest age tăng | bật broker, publisher retry; không sửa booking thủ công |
| Worker expiry tắt | overdue hold count/age tăng | bật worker, kiểm tra release đúng một lần |
| Consumer lỗi | lag/DLQ, inbox | sửa nguyên nhân, replay event bằng admin local có audit |
| Payment success muộn | REFUND_REQUIRED, payment record | chạy bù trừ mô phỏng idempotent; không phát vé |
| Retry request mơ hồ | idempotency key | gửi lại đúng key/payload, GET booking kiểm tra state |

Chỉ replay sau khi xác định event/aggregate và nguyên nhân; không bỏ inbox/state guard. Mọi lệnh thao tác dữ liệu cần log actor, correlation ID và kết quả. Các thao tác demo/replay bị vô hiệu ngoài local/test.

## Giới hạn

Đây là mô phỏng payment/refund, không thu tiền. GA là một hàng khóa nên throughput có thể giới hạn; báo cáo số đo thật. README triển khai sẽ ghi lệnh chạy và reset chính xác khi có source.
