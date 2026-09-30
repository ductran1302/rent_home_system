# Kiến trúc RuinHome

## Tổng quan

```
Trình duyệt (React + Ant Design)
    |  fetch /api  (JWT Bearer)
    v
nginx (container web, cổng 80) ---- SPA tĩnh, proxy /api/ -> api:8080
    |
    v
Spring Boot (container api, cổng 8080)
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
| `asset` | Entity và repository cho tài sản theo phòng, chưa có controller |
| `contract` | Hợp đồng, người cùng thuê, ảnh hợp đồng, giá phí và ghi chú theo hợp đồng |
| `billing` | Loại phí, biểu giá, chỉ số, hóa đơn, thanh toán |
| `stats` | Thống kê cho trang tổng quan |
| `file` | Lưu file ảnh trên đĩa, kiểm tra định dạng |
| `common` | `BaseEntity`, `ApiExceptionHandler`, `MultipartConfiguration`, `HealthController` |

Luôn theo tầng `controller -> service -> repository`. Controller chỉ khai báo DTO, validate bằng Bean Validation và khai `@PreAuthorize`; không thao tác repository trực tiếp.

### Xác thực và phân quyền

- `SecurityConfig`: session `STATELESS`, CSRF tắt, `POST /api/auth/login` và `GET /api/health` là hai đường công khai, phần còn lại `authenticated()`.
- `JwtAuthenticationFilter` đọc `Authorization: Bearer`, kiểm tra chữ ký và hạn, rồi nạp tài khoản trực tiếp từ DB: tài khoản không còn, bị tắt (`enabled = false`) hoặc `MANAGER` ngoài thời hạn quản lý thì không đặt `SecurityContext` (yêu cầu nhận `401`). Vai trò lấy từ DB mỗi request, không tin claims nên thay vai trò hay tắt tài khoản có hiệu lực ngay.
- `ManagerPeriod` (package `user`) là hàm chung kiểm tra thời hạn quản lý, dùng ở cả `login` (trả `403`) và filter.
- `@EnableMethodSecurity` bật `@PreAuthorize`: ghi nhà và phòng chỉ `ADMIN`; ghi người, hợp đồng, hóa đơn, chỉ số là `ADMIN` hoặc `MANAGER`; biểu giá (`PUT /api/billing/fee-rates`) là cấu hình toàn hệ thống nên chỉ `ADMIN`; toàn bộ `/api/users` chỉ `ADMIN`.
- `CurrentUserService` lấy tài khoản hiện tại; `personId()` ném `403` khi tài khoản chưa liên kết hồ sơ.
- `GET /api/auth/me` trả `username`, `role`, `personId`, `fullName` của người đăng nhập. `UserAccountRepository.findByUsername` khai `left join fetch a.person` để trả hồ sơ kèm tên trong cùng phiên làm việc, tránh `LazyInitializationException` khi truy cập ngoài transaction. Các nơi khác chỉ đọc `getId()` trên proxy nên không kích hoạt tải bổ sung.
- Phạm vi dữ liệu: kiểm tra chủ nhà (`checkHouseVisible`) cho hợp đồng, hóa đơn, ảnh, phòng, nhà. `ADMIN` đi qua, `USER` luôn bị chặn, `MANAGER` chỉ được với nhà mà mình là chủ hoặc quản lý. Danh sách chỉ số điện nước lọc theo `owner OR manager` trong repository.
- `AdminUserInitializer` là `CommandLineRunner`: chạy khi khởi động, tạo user `admin` nếu chưa có (mật khẩu `admin123`).
- JWT ký HMAC từ `JWT_SECRET` (khóa dự phòng là chuỗi dev, phải đổi khi triển khai thật), hạn mặc định 24 giờ (`ruinhome.jwt.expiration-ms`).

### Ràng buộc nghiệp vụ

- Một phòng chỉ có tối đa một hợp đồng `ACTIVE`: unique index `uniq_contract_active_per_room` trên `(room_id) WHERE status = 'ACTIVE'`. Trạng thái hợp đồng chỉ đổi qua service, không update trực tiếp repository.
- Thời gian hợp đồng phải `endDate > startDate`.
- Xoá mềm bằng cột `active = false` cho `person`, `house`, `room`, `asset`. Dòng có khoá ngoại lịch sử như `contract`, `invoice` không xoá cứng. Xoá mềm `person` trả `409` khi người vẫn còn tài khoản đang bật.
- Quản lý tài khoản (`/api/users`): chỉ tạo được vai trò `MANAGER` và `USER`; `MANAGER` bắt buộc liên kết hồ sơ và ngày bắt đầu quản lý; không xoá cứng tài khoản, chỉ tắt `enabled`; không tự tắt hoặc tự đổi vai trò của chính mình.
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

Quy ước cột: tiền `BIGINT` (VND không thập phân), ngày `DATE`, kỳ `VARCHAR(7)` dạng `YYYY-MM`, thời gian `TIMESTAMP`. Tên cột tiếng Anh, trùng với tên field Java.

## Luồng hóa đơn

```
fee_type (điện, nước, dịch vụ, ...)
   -> giá theo hợp đồng (contract_fee_price, có thì ưu tiên)
   -> fee_rate theo kỳ (giá cố định cho một kỳ, dùng khi hợp đồng không đặt giá riêng)
   -> meter_reading (chỉ số điện nước theo phòng, theo kỳ)
   -> POST /api/billing/invoices/generate?period=2026-09
        sinh hóa đơn DRAFT cho từng phòng có hợp đồng ACTIVE
        dòng tiền = phí cố định (hợp đồng hoặc fee_rate) + tiền theo chỉ số
   -> publish: DRAFT -> UNPAID
   -> payments: UNPAID -> PARTIAL -> PAID
   -> có thể thêm, sửa, xóa dòng tiền thủ công (cộng dồn lại totalAmount)
   -> PUT /{id}/room-price đổi giá phòng theo từng kỳ, ghi roomPriceNote (PAID thì 409)
```

`generate` trả `created` cộng `skipped` (phòng không có hợp đồng hoặc thiếu chỉ số) để UI báo lại. Trang hợp đồng cho khai giá điện, nước, mạng, dịch vụ chung theo từng hợp đồng; trang hóa đơn giữ biểu giá chung theo kỳ và cho sửa riêng giá phòng của hóa đơn.

## Frontend

`frontend/src/`:

| Thư mục | Nội dung |
| --- | --- |
| `api/client.ts` | axios `baseURL: '/api'`, gắn JWT từ `localStorage`, `401` thì xoá token và chuyển `/login` |
| `auth/` | `AuthProvider` nạp `/api/auth/me`, context cấp `me` và `logout` |
| `components/` | `AppLayout` (menu theo vai trò, header), `ProtectedRoute`, `RoleRoute` (chặn trang theo vai trò), skeleton, khung ảnh hợp đồng |
| `components/billing/` | `InvoiceDrawer`, `MeterModal`, `FeeRateModal` |
| `pages/` | `Login`, `Home`, `Houses`, `Persons`, `Contracts`, `Billing`, `Accounts` |
| `utils/format.ts` | Formatter tiền VND dùng chung |

- Menu và route theo vai trò: `USER` chỉ thấy Tổng quan, Hợp đồng, Hóa đơn; `MANAGER` thêm Nhà & phòng (chỉ đọc) và Người; `ADMIN` thêm Tài khoản. Truy cập sai vai trò thì `RoleRoute` chuyển về trang tổng quan.

- Trạng thái server quản bằng TanStack Query; mỗi trang tự `useQuery`.
- Route cấp trang dùng `React.lazy`, `AppLayout` bọc `<Outlet>` bằng `Suspense` với `PageSkeleton` khớp khung trang.
- `vite.config.ts` chia chunk `manualChunks` (antd, rc, react, router, query, axios, dayjs) để mọi chunk dưới 500 kB.
- Chuỗi hiển thị tiếng Việt, tên file và mã tiếng Anh. Mọi UI phải theo `docs/ui-checklist.md`: có trạng thái loading, rỗng, lỗi; form có validate; truy cập bàn phím và nhãn đúng; không emoji, không gạch ngang em.

## Thêm module mới

1. Migration mới `V<n>__*.sql` nếu cần bảng hoặc cột.
2. Package domain mới: entity kế thừa `BaseEntity`, repository, DTO record, service (validate nghiệp vụ, kiểm tra vai trò và phạm vi), controller với `@PreAuthorize` cho mọi thao tác ghi.
3. Nếu có file đính kèm thì dùng `FileStorageService`, không ghi thẳng đường dẫn tùy tiện.
4. Frontend: page trong `pages/`, component dùng chung trong `components/`, gọi qua `api` từ `api/client.ts`.
5. Chạy `.\dev.cmd backend verify` và `.\dev.cmd frontend lint`, `.\dev.cmd frontend build` trước khi giao.
