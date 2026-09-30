# RuinHome

Ứng dụng quản lý nhà cho thuê: quản lý người, nhà, phòng, hợp đồng, chỉ số điện nước và hóa đơn. Giao diện tiếng Việt, backend REST, chạy được bằng Docker hoặc chạy dev trên máy.

## Tính năng

- **Đăng nhập**: JWT, phân vai trò `ADMIN`, `MANAGER`, `USER`. Tài khoản admin mặc định `admin / admin123` được tạo khi khởi động. Tài khoản `MANAGER` có thời hạn quản lý, hết hạn thì không đăng nhập được.
- **Tài khoản**: trang quản lý tài khoản dành cho `ADMIN`: tạo tài khoản quản lý và người dùng, liên kết hồ sơ cá nhân, đặt thời hạn quản lý, bật tắt tài khoản (không xoá cứng).
- **Người**: thông tin liên hệ, CCCD, tìm kiếm, phân trang, xoá mềm; kèm ngày tạo, người tạo, ngày cập nhật, người cập nhật.
- **Nhà và phòng**: cây nhà và phòng, diện tích, trạng thái đã có người thuê hay chưa. Tạo sửa nhà và phòng là quyền của `ADMIN`, `MANAGER` chỉ xem.
- **Hợp đồng**: thuê theo kỳ, liệt kê người cùng thuê, trạng thái `ACTIVE` / `EXPIRED` / `TERMINATED`, thanh lý hợp đồng, ghi chú thêm (ngày dọn đến, tiền cọc), giá điện, nước, mạng, dịch vụ chung theo từng hợp đồng (để trống thì lấy giá chung của kỳ). Mỗi phòng chỉ có tối đa một hợp đồng `ACTIVE` (bắt buộc ở tầng database).
- **Hóa đơn**: loại phí và biểu giá theo kỳ, chỉ số điện nước, sinh hóa đơn theo kỳ, phát hành, thu tiền từng phần, thêm sửa dòng tiền thủ công, đổi giá phòng của từng kỳ hóa đơn kèm lý do.
- **Thống kê**: số nhà, số phòng, phòng trống, hợp đồng còn hiệu lực, hóa đơn chưa thu, công nợ còn lại.
- **Ảnh hợp đồng**: API upload, xem, xoá đã có (tối đa 5 MB/ảnh, `jpg` / `png` / `webp`). UI đặt khung ảnh trống ở biểu mẫu sửa hợp đồng, phần upload làm ở giai đoạn sau.

## Kiến trúc

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Spring Boot 3.5, Java 17 (dev) / 21 (Docker), Spring Security + JWT, Spring Data JPA |
| Database | PostgreSQL 16, Flyway (`V1__init.sql` đến `V7__person_audit_account_manager.sql`) |
| Frontend | Vite 7, React 19, TypeScript 5, Ant Design 5, TanStack Query 5, React Router 7, axios |
| Packaging | Docker Compose: `postgres`, `api`, `web` (nginx) |

Chi tiết: [docs/architecture.md](docs/architecture.md). Danh sách endpoint: [docs/api.md](docs/api.md).

## Cấu trúc thư mục

```
RuinHome/
  backend/
    src/main/java/com/ruinhome/    # package theo domain: auth, person, house, room,
                                   # asset, contract, billing, stats, user, file, common
    src/main/resources/db/migration/
  frontend/
    src/pages/                     # mỗi trang một file
    src/components/                # component dùng chung + modal theo feature
    src/api/client.ts              # axios instance, baseURL /api, gắn JWT
    src/auth/                      # AuthProvider, context
  docker-compose.yml
  dev.cmd                          # launcher dev (không đổi config hệ thống)
  docs/                            # tài liệu
  AGENTS.md                        # quy ước dành cho AI/dev
```

## Chạy bằng Docker

Yêu cầu: Docker Desktop đang chạy.

```powershell
docker compose up -d --build     # build image api + web, khởi động cả stack
docker compose ps                # kiểm tra trạng thái
docker compose logs -f api       # log backend
```

| Dịch vụ | Cổng | Ghi chú |
| --- | --- | --- |
| web (nginx) | 80 | SPA tĩnh, map `/api/` sang `api:8080` |
| api | 8080 | REST `/api/**` |
| postgres | 5432 | volume `pgdata` |

Mở `http://localhost`, đăng nhập `admin / admin123`.

```powershell
docker compose stop api web      # dừng app, giữ database
docker compose down              # dừng cả stack, giữ volume
docker compose down -v           # xoá luôn database (reset sạch)
```

## Dữ liệu mẫu

Migration `V3__seed_demo_data.sql` tự nạp dữ liệu demo khi khởi động, không cần thao tác thêm:

- 10 người, 2 nhà (`H001` Nhà trọ Mai Linh, `H002` Nhà trọ An Bình), 8 phòng, 7 tài sản
- 6 hợp đồng: 4 `ACTIVE`, 1 `EXPIRED`, 1 `TERMINATED`, có người cùng thuê
- Biểu giá điện, nước, internet, dịch vụ cho kỳ `2026-08` và `2026-09`
- Chỉ số điện nước 3 kỳ (`2026-07` đến `2026-09`) cho 4 phòng đang thuê
- 8 hóa đơn có đủ 5 dòng tiền: kỳ `2026-08` đã thu đủ, kỳ `2026-09` đủ bốn trạng thái `PAID`, `PARTIAL`, `UNPAID`, `DRAFT`
- Giá phí theo hợp đồng (`V4__contract_fee_price.sql`): phòng `A101` đủ 4 loại, `B101` điện nước, `B201` mạng dịch vụ, `A102` để trống để thấy nhánh lấy giá chung

Tài khoản demo:

| Tài khoản | Mật khẩu | Vai trò | Dữ liệu liên quan |
| --- | --- | --- | --- |
| `admin` | `admin123` | `ADMIN` | tạo khi khởi động, thấy toàn bộ |
| `quanly` | `quanly123` | `MANAGER` | quản lý nhà `H001`, chỉ thấy nhà đó |
| `nguoidung` | `nguoidung123` | `USER` | thuê phòng `A101`, chỉ đọc |

Xoá sạch và lấy lại dữ liệu mẫu: `docker compose down -v` rồi `docker compose up -d --build`.

## Chạy dev trên máy

Yêu cầu: JDK 17 (Corretto 17 tại `C:\Users\Admin\.jdks\corretto-17.0.17`), Node.js và npm, PostgreSQL đang chạy (dùng luôn Postgres của Docker: `docker compose up -d postgres`).

Toàn bộ lệnh đi qua `dev.cmd`, file này set `JAVA_HOME` riêng cho phiên chạy nên không đụng vào config hệ thống. Máy này chặn script `.ps1` nên đừng gọi npm/mvn trực tiếp.

```powershell
.\dev.cmd backend spring-boot:run     # backend, cổng 8080
.\dev.cmd backend verify              # build + test
.\dev.cmd backend flyway:info         # danh sách migration

.\dev.cmd frontend dev                # Vite dev server, cổng 5173, proxy /api -> 8080
.\dev.cmd frontend build              # production build
.\dev.cmd frontend lint               # ESLint
```

Chạy dev thì mở `http://localhost:5173`. Dev backend và Docker cùng dùng cổng 8080, muốn chạy cả hai thì dừng một trong hai.

## Kiểm thử

- Backend: `.\dev.cmd backend verify` (JUnit + Spring context + Hibernate validate). Test ràng buộc hợp đồng (`ContractActiveConstraintTest`) cần PostgreSQL đang chạy.
- Frontend: `.\dev.cmd frontend lint` và `.\dev.cmd frontend build` (tsc + Vite).

## Database

- Migration qua Flyway, chỉ thêm file `V<n>__*.sql` mới, không sửa file đã chạy.
- `V3__seed_demo_data.sql` là dữ liệu mẫu, xem mục "Dữ liệu mẫu" phía trên.
- Reset môi trường dev: `docker compose down -v` rồi `docker compose up -d postgres`.
- Tiền: cột `BIGINT`, đơn vị VND, hiển thị qua formatter `vi-VN`.
- Xoá mềm: cờ `active = false` cho `house`, `room`, `person`, `asset`. Dòng có khoá ngoại lịch sử (`contract`, `invoice`) không xoá cứng.

## Quy ước

- Tên file và mã: tiếng Anh. Chuỗi hiển thị trên UI: tiếng Việt.
- UI phải theo [docs/ui-checklist.md](docs/ui-checklist.md): có đủ trạng thái loading/rỗng/lỗi, form có validate và thông báo lỗi tiếng Việt, bàn phím và nhãn truy cập đúng.
- Backend thêm module mới theo package domain, controller không truy cập repository trực tiếp.
- Không commit khi chưa được yêu cầu. Chi tiết trong [AGENTS.md](AGENTS.md).
