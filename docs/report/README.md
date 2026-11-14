# Báo cáo đồ án T10.3

- `bao-cao-do-an.docx`: bản Word chỉnh sửa được, có heading, bookmark, bảng và nội dung văn bản.
- `bao-cao-do-an.pdf`: bản đọc đã kiểm tra từ cùng DOCX, **43 trang A4**.
- `BAO_CAO.md`: nội dung đã giải các bảng dữ liệu từ evidence, thuận tiện diff/review.
- `figures/`: tám sơ đồ giải thích được dựng từ source/ADR, không phải ảnh Chrome.

Thông tin trường/khoa, MSSV, lớp/khóa, ngành, giảng viên và địa điểm/thời gian nộp trên bìa có ô **[CẦN ĐIỀN]**. Tên Lê Hữu Hào lấy từ PRD. Chưa có mẫu trường; định dạng hiện tại là A4, Times New Roman 13 cho thân bài, tiêu đề đen và bảng có viền. Khi áp mẫu trường hoặc sửa nội dung, cần xuất lại và kiểm tra pagination.

Mục lục hiện có46mục, title/link theo heading tại thời điểm build, số trang dùng trường PAGEREF. Trong Word có thể Ctrl+A rồi F9 để cập nhật trường/số trang; nếu thêm/xóa/đổi tên heading, cần tạo lại mục lục hoặc chạy lại builder. Bản PDF là bản kiểm chứng đọc, không tự thay đổi khi sửa Word.

Số liệu được gắn mã E1–E6 và đường dẫn `docs/evidence` trong báo cáo. Các gate130backend là kết quả đã lưu ở T9; launcher T10.2 chỉ thực chạy9test/skip121. T10.3 không chạy lại nghiệp vụ hoặc benchmark để tạo số liệu mới. Chrome T10.1 là lời xác nhận người dùng; không có ảnh được truy cập để chèn vào báo cáo. Thanh toán/hoàn tiền là mô phỏng và giao nhận Kafka là at-least-once với deduplication.

## Tạo lại trong môi trường Codex Windows đã dùng

Nguồn biên tập: `tools/report/content.md`; `build_report.py` đọc trực tiếp evidence, kiểm CSV và dựng Word/Markdown/hình. Các công cụ xuất tài liệu tách biệt với stack ứng dụng. Runtime Windows hiện không có LibreOffice; adapter dùng một container render dùng một lần, không network/port/database/volume sản phẩm. Font Windows chỉ mount đọc. Không cần các công cụ này để mở hai file báo cáo đã xuất.

```powershell
cd D:\event-ticketing-platform
$artifactPython=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
& $artifactPython -X utf8 tools/report/build_report.py
docker build -t ticketing-t103-render:local -f tools/report/Dockerfile.render tools/report
& $artifactPython -X utf8 tools/report/render_report.py docs/report/bao-cao-do-an.docx --output_dir reports/t10-3/render-new --dpi 150 --emit_pdf
& $artifactPython -X utf8 tools/report/verify_report.py reports/t10-3/render-new
```

`render_report.py` tái sử dụng `render_docx.py` của skill documents và Poppler/Python đi kèm runtime; version/đường dẫn skill nằm trong script. Nếu runtime thay đổi, xác minh lại đường dẫn trước khi build. `verify_report.py` kiểm số trang/mục lục/font/biên/bảng, 300mẫu CSV và407counter snapshot đã lưu; việc kiểm layout vẫn cần mở từng PNG cuối. PNG, log và dữ liệu QA nằm trong `reports/t10-3` được Git ignore.

Bằng chứng hoàn tất và hash artifact: `docs/evidence/t10-3-report-2026-11-14.json`. Không có báo cáo slide/tag/phát hành hoặc công việc T10.4 trong phần này.
