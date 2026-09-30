# RuinHome UI Checklist

> Nguồn: trích lược từ **taste-skill** - `D:\tools\taste-skill\skills\taste-skill\SKILL.md`
> MIT License, Copyright 2026 Leonxlnx (giữ nguồn khi trích).
> Phạm vi: admin/CRUD app (React + AntD). **Bỏ** quy tắc landing/hero/bento/GSAP (taste-skill §13: dashboard/admin ngoài scope của skill gốc).
> **Bắt buộc đọc file này trước mọi task sửa frontend.**

---

## 0. Quy tắc chung (Locks)

- **1 accent color** cho toàn app, saturation < 80%. Không đổi màu accent ở màn hình cuối.
- **1 gray family** (không trộn warm/cool gray).
- **1 radius system** (AntD token mặc định, không override lộn xộn từng component).
- **1 icon family**: `@ant-design/icons` duy nhất. Không hand-rolled SVG icon, không trộn thư viện icon khác.
- **1 design system**: AntD. Không trộn component library khác vào tree.
- **Light mode lock** cho GĐ1 (AntD default). Một trang = một theme, không đảo light/dark giữa trang.
- Không emoji trong UI, markup, message (dùng icon library).
- Không nền/chữ `#000000` / `#ffffff` thuần (AntD token đã làm đúng, đừng override).

## 1. Trạng thái UI (mọi màn hình CRUD)

- **Loading**: skeleton khớp shape của layout cuối. Không spinner tròn chung chung.
- **Empty**: composition có hướng dẫn cách thêm dữ liệu (VD: "Chưa có phòng nào — bấm Thêm phòng").
- **Error**: inline dưới field (form); toast cho thao tác transient. Không `window.alert()`, không modal cho lỗi thường.
- **Interactive feedback**: `:active` → `scale(0.98)` hoặc `translateY(1px)`; transition 200-300ms; focus ring thấy được (bắt buộc cho keyboard).
- **Motion claimed = motion shown**: khai có animation thì phải chạy thật, không build dở.

## 2. Form (AntD)

- Label luôn có (`Form.Item label`). Không placeholder-as-label.
- Error dưới input (AntD `rules`), validate cả client lẫn server.
- Helper text mờ, không cạnh tranh với label.
- Button **contrast check**: text đọc được trên nền (WCAG AA 4.5:1). Cấm white-on-white.
- CTA không wrap 2 dòng ở desktop (rút gọn label hoặc giãn nút).
- Một intent = một nhãn nút: đã chọn "Lưu" thì mọi nơi là "Lưu", không xen "Lưu thay đổi"/"Xác nhận".

## 3. Accessibility

- Contrast WCAG AA cho body (4.5:1), 3:1 cho chữ lớn 18px+. Placeholder/label không xám nhạt quá ngưỡng.
- Focus visible trên mọi interactive element.
- Alt text mô tả thật. Icon-only button → `aria-label`.
- Không có vùng tương tác chỉ phân biệt bằng màu (kèm text/icon trạng thái).

## 4. Tiền tệ & số liệu (đặc thù RuinHome)

- Tiền: lưu `BIGINT` VND, hiển thị `Intl.NumberFormat("vi-VN")` → `1.500.000 ₫`. Nhất quán ở bảng, chi tiết, dashboard, hóa đơn.
- Số trong bảng: `font-variant-numeric: tabular-nums`, cột số canh phải.
- Mã phòng / CCCD / chỉ số đồng hồ: font mono.
- Kỳ hóa đơn: format `MM/YYYY` thống nhất.
- Số liệu không bịa: nếu là dữ liệu demo thì để trong seed, không hardcode số tròn giả (`99.99%`) trong UI.

## 5. Copy tiếng Việt

- Cấm câu AI sáo: "Nâng tầm", "Seamless", "Đột phá", "Giải pháp toàn diện", "Thời đại mới".
- **Không em-dash `—`** trong mọi chuỗi hiển thị (dùng `-`, `.` hoặc xuống dòng). Áp dụng cho tiêu đề, nút, label, message, alt text.
- Không label đánh số kiểu "Bước 1 / Stage 2 / Phase 01" - dùng verb-noun ("Tạo hợp đồng", "Nhập chỉ số").
- Lỗi nói thẳng, active voice: "Không lưu được dữ liệu. Thử lại." Không "Oops!".
- Thành công chấm thường: "Đã lưu hợp đồng." Không dấu `!`.
- Seed data: tên người / địa chỉ / SĐT Việt Nam thật (Nguyễn Thị Lan, Trần Văn Hùng, 0912345678...). Cấm "John Doe", "Jane Smith", "Acme Corp".
- Icon cliche bị tránh: rocket = "Phát hành", shield = "Bảo mật" - chọn icon đúng nghĩa ngữ cảnh.

## 6. Chống "AI tells" (layout & visual)

- Không 3 card ngang bằng nhau làm hàng feature vô nghĩa - nếu lặp phải có lý do nội dung.
- Không gradient tím AI, glow neon, glassmorphism tràn lan.
- Không `border-t` + `border-b` trên mọi hàng của bảng/danh sách dài (AntD Table có divider sẵn, đừng custom lại).
- Badge trạng thái chỉ khi có nghĩa semantic (ĐANG HOẠT ĐỘNG, HẾT HẠN, TRỐNG). Không dot màu trang trí.
- Không section label `01 / INDEX`, `001 · Capabilities`.
- Card chỉ khi elevation thật sự cần; nếu không, dùng spacing / `divide-y` / border-bottom.
- Không status dot trang trí trước nav item / list row.

## 7. Performance & motion

- Chỉ animate `transform` và `opacity`. Không animate `top/left/width/height`.
- Tôn trọng `prefers-reduced-motion` cho mọi animation.
- Không `window.addEventListener("scroll")` thô trong React.
- Mỗi animation phải justify được 1 câu (hierarchy / feedback / state transition). Không justify được thì bỏ.
- Dashboard dùng skeleton, không spinner.

## 8. Pre-flight trước khi giao UI (chạy từng mục)

- [ ] Đã đọc file này trước khi bắt đầu code.
- [ ] Mọi màn hình CRUD có đủ loading / empty / error state.
- [ ] Form: label + error dưới input + validate server.
- [ ] Contrast button và form pass WCAG AA.
- [ ] Format tiền `vi-VN` đúng ở mọi nơi hiển thị.
- [ ] Grep chuỗi hiển thị: không còn `—`, không emoji, không copy AI sáo.
- [ ] Seed/demo data không có John Doe / Acme / số tròn giả.
- [ ] 1 accent, 1 radius, 1 icon family, 1 theme.
- [ ] Icon chỉ từ `@ant-design/icons`.
- [ ] `npm.cmd run build` pass, không warning mới.
