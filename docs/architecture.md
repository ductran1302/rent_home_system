# Kiến trúc RuinHome

## Tổng quan

```
Trình duyệt (React + Ant Design)
    |  fetch /api  (JWT Bearer)
    v
nginx (container web, cổng 80) ---- SPA tĩnh, proxy /api/ -> api:8080
    |
    v
Spring Boot (container api, host 8081 -> nội bộ 8080)
    |  JPA + Flyway
    v
PostgreSQL 16 (container postgres, volume pgdata)
```

Docker Compose có ba service: `postgres`, `api`, `web`. `api` chờ `postgres` healthy mới khởi động, `web` không biết gì về database, chỉ phục vụ file tĩnh và chuyển `/api/` sang `api`. Thư mục upload ảnh nằm ở volume `uploads` gắn vào `/app/uploads`.

## Backend

Package theo domain tại `backend/src/main/java/com/ruinhome/`:

| Package | Nội dung |
| --- | --- |
| `auth` | Đăng nhập, JWT, filter, phân quyền, `CurrentUserService` |
| `user` | `UserAccount`, `Role` (`ADMIN`, `MANAGER`, `USER`), quản lý tài khoản (`UserController`, `UserService`), thời hạn quản lý `ManagerPeriod` |
| `person`, `house`, `room` | CRUD nghiệp vụ, cây nhà và phòng |
| `asset` | Tài sản theo phòng, lịch sử sửa chữa, ảnh tài sản và ảnh lần sửa; chỉ đọc hợp đồng qua `ContractHandoverPort` |
| `contract` | Hợp đồng, người cùng thuê, ảnh hợp đồng, giá phí và ghi chú theo hợp đồng, giao và thu hồi tài sản |
| `billing` | Loại phí, biểu giá, chỉ số, hóa đơn, thanh toán |
| `stats` | Thống kê cho trang tổng quan |
| `file` | Lưu file ảnh trên đĩa, kiểm tra định dạng |
| `common` | `BaseEntity`, `ApiExceptionHandler`, `MultipartConfiguration`, `HealthController` |

Luôn theo tầng `controller -> service -> repository`. Controller chỉ khai báo DTO, validate bằng Bean Validation và khai `@PreAuthorize`; không thao tác repository trực tiếp.

### Xác thực và phân quyền

- `SecurityConfig`: session `STATELESS`, CSRF tắt, `POST /api/auth/login`, `POST /api/auth/register` và `GET /api/health` là các đường công khai, phần còn lại `authenticated()`.
- `JwtAuthenticationFilter` đọc `Authorization: Bearer`, kiểm tra chữ ký và hạn, rồi nạp tài khoản trực tiếp từ DB: tài khoản không còn, bị tắt (`enabled = false`) hoặc `MANAGER` ngoài thời hạn quản lý thì không đặt `SecurityContext` (yêu cầu nhận `401`). Vai trò lấy từ DB mỗi request, không tin claims nên thay vai trò hay tắt tài khoản có hiệu lực ngay.
- `ManagerPeriod` (package `user`) là hàm chung kiểm tra thời hạn quản lý, dùng ở cả `login` (trả `403`) và filter.
- `@EnableMethodSecurity` bật `@PreAuthorize`: ghi nhà và phòng chỉ `ADMIN`; ghi người, hợp đồng, hóa đơn, chỉ số, tài sản và lần sửa là `ADMIN` hoặc `MANAGER`; biểu giá (`PUT /api/billing/fee-rates`) là cấu hình toàn hệ thống nên chỉ admin gốc (kiểm tra `isRoot()` trong `FeeConfigService`, `ADMIN` thường nhận `403`); toàn bộ `/api/users` chỉ `ADMIN`.
- `CurrentUserService` lấy tài khoản hiện tại; `personId()` ném `403` khi tài khoản chưa liên kết hồ sơ; `isRoot()` báo admin gốc, `areaOrNull()` trả khu vực của admin/quản lý (`null` = không lọc, admin gốc luôn `null`), `areaForWrite()` trả khu vực gán cho dữ liệu mới tạo, `checkArea()` chặn admin thường khỏi khu vực khác bằng `403`.
- `GET /api/auth/me` trả `username`, `role`, `personId`, `fullName`, `root` của người đăng nhập. `UserAccountRepository.findByUsername` khai `left join fetch a.person` để trả hồ sơ kèm tên trong cùng phiên làm việc, tránh `LazyInitializationException` khi truy cập ngoài transaction. Các nơi khác chỉ đọc `getId()` trên proxy nên không kích hoạt tải bổ sung.
- `POST /api/auth/register` (công khai) qua `RegisterService`: tên đăng nhập không trùng, khớp `[A-Za-z0-9._-]{3,100}`, mật khẩu `8` đến `32` ký tự; tạo tài khoản `ADMIN` (chủ cho thuê) bật sẵn, khu vực mang tên chính nó, không phải admin gốc, chưa liên kết hồ sơ (liên kết sau). Đăng ký chỉ chạy khi `ruinhome.auth.allow-self-register = true` (mặc định tắt, bật bằng biến môi trường `ALLOW_SELF_REGISTER`), tắt thì trả `403`.
- Phạm vi dữ liệu theo khu vực: `house.area_admin`, `person.area_admin`, `user_account.area_admin` gán khi tạo (dữ liệu cũ và bốn tài khoản sẵn có thuộc `admin`). Admin gốc bỏ qua mọi bộ lọc; admin thường bị chặn ở `checkArea` (nhà, phòng, hợp đồng, hóa đơn, chỉ số, ảnh) và bị lọc trong danh sách nhà/người/tài khoản, hợp đồng, hóa đơn, thống kê; người và tài khoản ngoài khu vực trả `404`. `USER` luôn bị chặn, `MANAGER` kiểm tra chủ/quản lý của nhà cộng với khu vực khi liệt kê người. Danh sách chỉ số điện nước lọc theo `owner OR manager OR area`.
- `AdminUserInitializer` là `CommandLineRunner`: chạy khi khởi động, tạo user `admin` nếu chưa có, mật khẩu lấy từ biến `SEED_ADMIN_PASSWORD` (`is_root = true`, khu vực `admin`); không đặt biến thì bỏ qua, không còn mật khẩu mặc định trong mã nguồn.
- JWT ký HMAC từ `JWT_SECRET`, hạn mặc định 24 giờ (`ruinhome.jwt.expiration-ms`). Không còn khóa dự phòng: thiếu, dưới 32 ký tự hoặc còn chuỗi dev cũ thì app dừng ngay lúc khởi động. File `.env` (gitignored, mẫu `.env.example`) cung cấp `JWT_SECRET`, `SEED_ADMIN_PASSWORD`, `ALLOW_SELF_REGISTER` cho cả Docker và `dev.cmd backend`. Cổng `postgres` 5432 và `api` 8081 chỉ bind `127.0.0.1`.

### Ràng buộc nghiệp vụ

- Một phòng chỉ có tối đa một hợp đồng `ACTIVE`: unique index `uniq_contract_active_per_room` trên `(room_id) WHERE status = 'ACTIVE'`. Trạng thái hợp đồng chỉ đổi qua service, không update trực tiếp repository.
- Thời gian hợp đồng phải `endDate > startDate`.
- Xoá mềm bằng cột `active = false` cho `person`, `house`, `room`, `asset`. Dòng có khoá ngoại lịch sử như `contract`, `invoice` không xoá cứng. Xoá mềm `person` trả `409` khi người vẫn còn tài khoản đang bật.
- Một phòng chỉ có một dòng giao tài sản còn hiệu lực tại một thời điểm: `contract_asset` gắn tài sản với hợp đồng, xoá mềm tài sản đang được giao trả `409`. `asset` chỉ phụ thuộc vào `contract` qua interface `ContractHandoverPort` (package `asset`) và `ContractHandoverAdapter` (package `contract` implements), để không import ngược giữa hai package.
- Tài sản sửa chữa: bản ghi trong bảng `asset_repair` giữ `reportedAt`, `description`, `cost`, `status`; đổi `status` sang `DONE` thì ghi `doneAt` (để trống lấy hôm nay) và hạ tình trạng tài sản `NEEDS_REPAIR` / `BROKEN` xuống `USED`. Ghi tình trạng lúc trả qua `POST /api/contracts/{id}/assets/{assetId}/return`.

- Ảnh tài sản: bảng `asset_photo` gắn với tài sản, ảnh lần sửa thì thêm `repair_id` (bắt buộc kèm `stage` `TRUOC` / `SAU`). Mỗi tài sản và mỗi lần sửa tối đa 5 ảnh, 5 MB mỗi file, chỉ `jpeg` / `png` / `webp`, cùng cách lưu `UUID.ext` trong `upload-dir` như ảnh hợp đồng; xoá tài sản hoặc lần sửa thì ảnh con bị cascade xoá.
- Quản lý tài khoản (`/api/users`): chỉ tạo được vai trò `MANAGER` và `USER`; `MANAGER` bắt buộc liên kết hồ sơ và ngày bắt đầu quản lý, `ADMIN` sửa hồ sơ là tùy chọn; không xoá cứng tài khoản, chỉ tắt `enabled`; không tự tắt hoặc tự đổi vai trò của chính mình; admin thường chỉ sửa tài khoản trong khu vực mình, ngoài khu vực trả `404`.
- Ảnh hợp đồng: 5 MB mỗi file, chỉ `jpeg` / `png` / `webp`, lưu dạng `UUID.ext` trong `upload-dir`, metadata ghi vào bảng `contract_photo`.
- Mọi lỗi đi qua `ApiExceptionHandler`, trả `{ status, message }`, lỗi validate thêm `fields`.

### Entity chung

`BaseEntity` (`@MappedSuperclass`) cho mọi bảng: `id` identity, `created_at`, `updated_at` tự gán ở `@PrePersist` và `@PreUpdate`. Cột `active` do từng entity khai báo riêng.

Riêng `Person` override `onCreate()`: vẫn gán `createdAt` nhưng để `updatedAt = null`, kèm `created_by` / `updated_by` do `PersonService` ghi bằng tên đăng nhập hiện tại. Vì vậy hồ sơ mới tạo hiển thị "Chưa cập nhật", mỗi thao tác ghi đều làm mới mốc thời gian và người cập nhật.

## Database

Migration qua Flyway, chỉ thêm `V<n>__*.sql` mới.

**`V1__init.sql`**: `person`, `house`, `room`, `asset`, `contract`, `contract_tenant`, `contract_photo`, `user_account`. Index theo khoá ngoại: chủ nhà, quản lý, phòng, tài sản, người giữ hợp đồng, người cùng thuê.

**`V2__billing.sql`**: `fee_type`, `fee_rate`, `meter_reading`, `invoice`, `invoice_line`. Index cho `meter_reading(room_id, period)`, `invoice(period)`, `invoice(status)`, `invoice_line(invoice_id)`. File này cũng seed 5 loại phí: tiền phòng, điện, nước, internet, dịch vụ.

**`V3__seed_demo_data.sql`**: dữ liệu mẫu demo (người, nhà, phòng, tài sản, hợp đồng, biểu giá, chỉ số, hóa đơn kỳ 2026-08 và 2026-09, tài khoản `quanly` và `nguoidung`). Chỉ chạy một lần khi lần đầu khởi động schema mới.

**`V4__contract_fee_price.sql`**: bảng `contract_fee_price (contract_id, fee_code, price)` với unique `(contract_id, fee_code)`, cột `invoice.room_price_note` ghi lý do sửa giá phòng, và seed giá riêng cho các hợp đồng mẫu (phòng A102 cố ý để trống để thấy nhánh lấy giá chung).

**`V5__contract_note.sql`**: cột `contract.note VARCHAR(500)` cho ghi chú thêm của hợp đồng (ký ngày nào, đến ở ngày nào, tiền cọc).

**`V6__house_room_note.sql`**: cột `house.note` và `room.note` `VARCHAR(500)` cho ghi chú nhà và phòng.

**`V7__person_audit_account_manager.sql`**: `person.created_by`, `person.updated_by`, bỏ `NOT NULL` của `person.updated_at`; `user_account.manager_start_date`, `user_account.manager_end_date` cộng seed thời hạn cho tài khoản `MANAGER` có sẵn; những người chưa từng cập nhật thì `updated_at` để `NULL`.

**`V8__area_admin.sql`**: `house.area_admin`, `person.area_admin`, `user_account.area_admin` (`VARCHAR(100)`, khu vực = tên đăng nhập chủ cho thuê) và `user_account.is_root` (`BOOLEAN`, admin gốc thấy tất cả); dữ liệu sẵn có backfill về khu vực `admin`, tài khoản `admin` đánh dấu `is_root = true`.

**`V9__invoice_readings.sql`**: `invoice.pre_elect_reading`, `invoice.current_elect_reading`, `invoice.pre_water_reading`, `invoice.current_water_reading` (`NUMERIC(12,2)`, số công tơ đầu kỳ và cuối kỳ của điện và nước); hóa đơn sẵn có có chỉ số và dòng tiền tương ứng được backfill từ `meter_reading` (`current` = chỉ số kỳ đó, `pre` = chỉ số trừ số lượng dòng).

**`V10__asset_repair.sql`**: cột `asset.category` (`VARCHAR(30)`, mặc định `KHAC`, backfill nhóm cho dữ liệu mẫu theo tên), bảng `contract_asset` (bàn giao tài sản theo hợp đồng: `handover_condition`, `return_condition`, `returned_at`, unique `(contract_id, asset_id)`), bảng `asset_repair` (lịch sử sửa chữa: `reported_at`, `description`, `cost >= 0`, `status`, `done_at`) và 3 dòng sửa chữa mẫu.

**`V11__asset_photo.sql`**: bảng `asset_photo` (`asset_id` không null, `repair_id` nullable xoá theo `asset_repair`, `stage VARCHAR(10)` chỉ nhận `TRUOC` / `SAU`, `file_path`, `original_name`, `uploaded_at TIMESTAMPTZ`), hai check (`stage IN ('TRUOC','SAU')` và `repair_id` null đúng lúc `stage` null) cùng index theo `asset_id`, `repair_id`.

Quy ước cột: tiền `BIGINT` (VND không thập phân), ngày `DATE`, kỳ `VARCHAR(7)` dạng `YYYY-MM`, thời gian `TIMESTAMP`. Tên cột tiếng Anh, trùng với tên field Java.

## Luồng hóa đơn

```
fee_type (điện, nước, dịch vụ, ...)
   -> fee_rate theo kỳ (ưu tiên, cấu hình qua nút Cấu hình giá ở trang hóa đơn)
   -> giá theo hợp đồng (contract_fee_price, dùng khi không có fee_rate của kỳ)
   -> meter_reading (chỉ số điện nước theo phòng, theo kỳ)
   -> POST /api/billing/invoices/generate?period=2026-09
        sinh hóa đơn DRAFT cho từng phòng có hợp đồng ACTIVE
        chụp chỉ số công tơ vào hóa đơn:
          pre = current của hóa đơn kỳ trước -> chỉ số meter_reading kỳ trước -> 0
          current = meter_reading kỳ này (chưa nhập thì để trống, nhập sau)
        dòng tiền = phí cố định (fee_rate kỳ ưu tiên, không có thì hợp đồng) + tiêu thụ (current - pre)
   -> PUT /{id}/readings nhập hoặc sửa chỉ số sau khi tạo,
        tính lại dòng điện/nước và totalAmount (PAID thì 409)
   -> publish: DRAFT -> UNPAID
   -> payments: UNPAID -> PARTIAL -> PAID
   -> có thể sửa, xóa dòng tiền thủ công (cộng dồn lại totalAmount)
   -> PUT /{id}/room-price đổi giá phòng theo từng kỳ, ghi roomPriceNote (PAID thì 409)
```

`generate` trả `created` cộng `skipped` (đã có hóa đơn kỳ này, thiếu giá, chỉ số cuối kỳ nhỏ hơn đầu kỳ) để UI báo lại; phòng chưa nhập chỉ số cuối kỳ vẫn sinh hóa đơn, thiếu dòng điện nước thì nhập sau bằng `readings`. Trang hợp đồng cho khai giá điện, nước, mạng, dịch vụ chung theo từng hợp đồng; trang hóa đơn giữ biểu giá chung theo kỳ, cho sửa riêng giá phòng và chỉ số công tơ của hóa đơn.

## Frontend

`frontend/src/`:

| Thư mục | Nội dung |
| --- | --- |
| `api/client.ts` | axios `baseURL: '/api'`, gắn JWT từ `localStorage`, `401` thì xoá token và chuyển `/login` |
| `auth/` | `AuthProvider` nạp `/api/auth/me`, context cấp `me` và `logout` |
| `components/` | `AppLayout` (menu theo vai trò, header), `ProtectedRoute`, `RoleRoute` (chặn trang theo vai trò), skeleton, khung ảnh hợp đồng, `PhotoUpload` (upload và xem ảnh tài sản, ảnh trước và sau lần sửa, tải blob có token), `ContractAssetDrawer` (bàn giao và thu hồi tài sản của hợp đồng) |
| `components/billing/` | `InvoiceDrawer`, `MeterModal`, `FeeRateModal` |
| `pages/` | `Login`, `Register`, `Home`, `Houses`, `Persons`, `Contracts`, `Assets`, `Billing`, `Accounts` |
| `utils/format.ts` | Formatter tiền VND dùng chung |
| `utils/asset.ts` | Nhóm tài sản, tình trạng, trạng thái sửa chữa, loại ảnh và nhãn tiếng Việt dùng chung |

- Menu và route theo vai trò: `USER` chỉ thấy Tổng quan, Hợp đồng, Hóa đơn; `MANAGER` thêm Nhà & phòng (chỉ đọc), Người và Tài sản; `ADMIN` thêm Tài khoản và Tài sản. Truy cập sai vai trò thì `RoleRoute` chuyển về trang tổng quan. Đăng ký tạo `ADMIN` chủ cho thuê nên menu đầy đủ; dữ liệu đã lọc theo khu vực phía server, giao diện không cần phân biệt admin gốc với admin khu vực.

- Trạng thái server quản bằng TanStack Query; mỗi trang tự `useQuery`.
- Route cấp trang dùng `React.lazy`, `AppLayout` bọc `<Outlet>` bằng `Suspense` với `PageSkeleton` khớp khung trang.
- `vite.config.ts` chia chunk `manualChunks` (antd, rc, react, router, query, axios, dayjs) để mọi chunk dưới 500 kB, và đặt `assetsDir: 'static'` để thư mục file build không đè lên route SPA `/assets`.
- Chuỗi hiển thị tiếng Việt, tên file và mã tiếng Anh. Mọi UI phải theo `docs/ui-checklist.md`: có trạng thái loading, rỗng, lỗi; form có validate; truy cập bàn phím và nhãn đúng; không emoji, không gạch ngang em.

## Thêm module mới

1. Migration mới `V<n>__*.sql` nếu cần bảng hoặc cột.
2. Package domain mới: entity kế thừa `BaseEntity`, repository, DTO record, service (validate nghiệp vụ, kiểm tra vai trò và phạm vi), controller với `@PreAuthorize` cho mọi thao tác ghi.
3. Nếu có file đính kèm thì dùng `FileStorageService`, không ghi thẳng đường dẫn tùy tiện.
4. Frontend: page trong `pages/`, component dùng chung trong `components/`, gọi qua `api` từ `api/client.ts`.
5. Chạy `.\dev.cmd backend verify` và `.\dev.cmd frontend lint`, `.\dev.cmd frontend build` trước khi giao.
