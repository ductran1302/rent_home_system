# API RuinHome

Tất cả endpoint nằm dưới `/api`, trả JSON, ngày tháng dạng `yyyy-MM-dd`, tiền là số nguyên VND.

- Dev backend: `http://localhost:8080/api`
- API Docker (host): `http://localhost:8081/api`
- Qua nginx (Docker) hoặc Vite dev server: `http://localhost/api` (frontend luôn gọi `/api`, không hardcode host)

## Xác thực

`POST /api/auth/login`, `POST /api/auth/register` và `GET /api/health` là các endpoint công khai. Các endpoint còn lại phải kèm header:

```
Authorization: Bearer <token>
```

| Vai trò | Quyền |
| --- | --- |
| `ADMIN` | Toàn quyền trong khu vực của mình (nhà, phòng, người, tài khoản, biểu giá); admin gốc thấy và sửa toàn bộ dữ liệu mọi khu vực |
| `MANAGER` | Ghi người, hợp đồng, hóa đơn, chỉ số trong phạm vi nhà mình là chủ hoặc quản lý; API ghi nhà/phòng và biểu giá trả `403` (chỉ đọc) |
| `USER` | Chỉ đọc số liệu của chính mình; `GET /api/houses` trả mảng rỗng, thao tác ghi trả `403` |

**Khu vực (`areaAdmin`)**: dữ liệu gắn với khu vực = tên đăng nhập chủ cho thuê. Admin không phải gốc chỉ liệt kê/xem/sửa dữ liệu trong khu vực của mình: nhà, hợp đồng, hóa đơn, chỉ số, ảnh ngoài khu vực trả `403`; người, tài khoản ngoài khu vực trả `404` (che sự tồn tại); thống kê và danh sách lọc theo khu vực. Admin gốc bỏ qua toàn bộ bộ lọc này. `MANAGER` thuộc khu vực của admin đã tạo nó.

Tài khoản không liên kết hồ sơ cá nhân mà gọi thao tác cần phạm vi sẽ nhận `403` với thông báo "Tài khoản chưa liên kết hồ sơ cá nhân".

Tài khoản `MANAGER` có thời hạn quản lý (`managerStartDate`, `managerEndDate`): chưa đến ngày bắt đầu hoặc đã hết ngày kết thúc thì đăng nhập trả `403`. Token kiểm tra lại trong DB mỗi request, nên tài khoản bị tắt (`enabled = false`) hay hết hạn có hiệu lực ngay, không cần chờ token hết hạn.

## Quy ước chung

**Phân trang** (person, contract, invoice): trả object bốn khóa, `size` tối đa 100.

```json
{ "items": [], "total": 0, "page": 0, "size": 20 }
```

**Kỳ (`period`)**: chuỗi `YYYY-MM`, ví dụ `2026-09`, tối đa 7 ký tự.

**Lỗi**: mọi lỗi trả `{ "status": <mã>, "message": "<tiếng Việt>" }`; lỗi validate thêm khóa `fields` ánh xạ tên trường sang thông báo.

| Mã | Ý nghĩa |
| --- | --- |
| 400 | Dữ liệu không hợp lệ, ảnh sai định dạng, ngày sai |
| 401 | Thiếu hoặc sai token, sai thông tin đăng nhập |
| 403 | Sai vai trò hoặc ngoài phạm vi nhà |
| 404 | Không tìm thấy bản ghi |
| 500 | Lỗi hệ thống, message dùng chung "Đã có lỗi xảy ra, vui lòng thử lại" |

## Endpoint

### Hệ thống và đăng nhập

| Method | Path | Yêu cầu | Body / query | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/health` | Không | | `{ "status": "OK" }` |
| POST | `/api/auth/login` | Không | `{ "username", "password" }` | `{ "token", "username", "role" }` |
| POST | `/api/auth/register` | Không | `{ "username", "password" }` | `201` |
| GET | `/api/auth/me` | JWT | | `{ "username", "role", "personId", "fullName" }` |
| GET | `/api/stats` | JWT | | `{ houseCount, roomCount, vacantRoomCount, activeContractCount, unpaidInvoiceCount, outstandingDebt }` |

`login` trả `403` khi tài khoản `MANAGER` chưa đến hoặc đã qua thời hạn quản lý. `stats` của `USER` chưa liên kết hồ sơ trả toàn số `0`.

`register` tạo tài khoản `ADMIN` (chủ cho thuê) bật sẵn, khu vực mang tên chính tài khoản đó, chưa phải admin gốc và chưa liên kết hồ sơ (liên kết sau qua trang Tài khoản). Tên đăng nhập gồm chữ, số, dấu chấm, gạch nối (`3` đến `100` ký tự), trùng trả `409`; mật khẩu `8` đến `32` ký tự; sai quy tắc trả `400`.

### Người

| Method | Path | Vai trò | Body / query | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/persons` | ADMIN, MANAGER | `q`, `page=0`, `size=20` | Phân trang |
| GET | `/api/persons/{id}` | ADMIN, MANAGER | | Person |
| POST | `/api/persons` | ADMIN, MANAGER | `{ fullName*, idNumber, phone, address }` | `201` + Person |
| PUT | `/api/persons/{id}` | ADMIN, MANAGER | như trên | Person |
| DELETE | `/api/persons/{id}` | ADMIN, MANAGER | | `{ "message": "Đã xoá người" }` (xoá mềm) |

Person trả về kèm `createdAt`, `updatedAt`, `createdBy`, `updatedBy` (ISO-8601). Hồ sơ mới tạo chưa từng cập nhật thì `updatedAt`, `updatedBy` là `null`. Xoá mềm trả `409` nếu người vẫn còn tài khoản đang bật, phải tắt tài khoản trước.

### Tài khoản

| Method | Path | Vai trò | Body | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/users` | ADMIN | | Mảng `{ id, username, role, personId, fullName, enabled, managerStartDate, managerEndDate, createdAt }` |
| POST | `/api/users` | ADMIN | `{ username*, password*, role*, personId, managerStartDate, managerEndDate, enabled }` | `201` + user |
| PUT | `/api/users/{id}` | ADMIN | `{ password, role*, personId, managerStartDate, managerEndDate, enabled }` | user |

- Không có `DELETE`: tắt tài khoản bằng `enabled = false`; không thể tự tắt tài khoản của chính mình.
- `role` khi tạo chỉ nhận `MANAGER` hoặc `USER` (admin chỉ tạo được qua `register`); không đổi được vai trò `ADMIN` và không tự đổi vai trò của mình.
- `MANAGER` bắt buộc có `personId` và `managerStartDate` (`yyyy-MM-dd`), `managerEndDate` tùy chọn (để trống là không giới hạn); quá thời hạn thì không đăng nhập được. Tài khoản tạo ra thuộc khu vực của người tạo.
- `ADMIN` khi sửa `personId` tùy chọn (để trống là gỡ liên kết hồ sơ). Không phải admin gốc chỉ sửa được tài khoản cùng khu vực, ngoài khu vực trả `404`.
- `username` duy nhất, `[A-Za-z0-9._-]{3,100}`; `password` tối thiểu 6 ký tự, bỏ trống khi sửa là giữ nguyên.

### Nhà và phòng

| Method | Path | Vai trò | Body | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/houses` | Đọc | | Mảng `House` (mỗi phần tử có `roomCount`); `USER` nhận mảng rỗng |
| GET | `/api/houses/{id}` | Đọc | | House |
| POST | `/api/houses` | ADMIN | `{ code*, name*, address*, ownerId*, managerId, note }` | `201` + House |
| PUT | `/api/houses/{id}` | ADMIN | như trên | House |
| DELETE | `/api/houses/{id}` | ADMIN | | `{ "message" }` (xoá mềm) |
| GET | `/api/rooms/by-house/{houseId}` | Đọc | | Mảng `Room` (có `occupied`) |
| GET | `/api/rooms/{id}` | Đọc | | Room |
| POST | `/api/rooms` | ADMIN | `{ houseId*, roomNumber*, areaM2, note }` | `201` + Room |
| PUT | `/api/rooms/{id}` | ADMIN | như trên | Room |
| DELETE | `/api/rooms/{id}` | ADMIN | | `{ "message" }` (xoá mềm) |

`note` là ghi chú tự do tối đa 500 ký tự cho nhà/phòng, gửi chuỗi rỗng hoặc `null` thì xoá ghi chú. House và Room trả về kèm `note`, `createdAt`, `updatedAt` (ISO-8601).

### Hợp đồng

| Method | Path | Vai trò | Body / query | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/contracts` | Đọc | `houseId`, `roomId`, `status`, `page`, `size` | Phân trang |
| GET | `/api/contracts/{id}` | Đọc | | Contract (kèm `tenants`, `feePrices`) |
| POST | `/api/contracts` | ADMIN, MANAGER | `{ roomId*, holderId*, monthlyRent*, startDate*, endDate*, tenantIds, feePrices, note }` | `201` + Contract |
| PUT | `/api/contracts/{id}` | ADMIN, MANAGER | `{ monthlyRent*, endDate*, tenantIds, feePrices, note }` | Contract |
| POST | `/api/contracts/{id}/terminate` | ADMIN, MANAGER | Không | Contract với `status = TERMINATED` |

`status` nhận `ACTIVE`, `EXPIRED`, `TERMINATED`. Tạo hợp đồng cho phòng đã có hợp đồng `ACTIVE` trả `400`.

`feePrices` là object `{ "DIEN": 3400, "NUOC": 21000, "MANG": 90000, "DICH_VU": 45000 }`, đơn giá VND theo đơn vị của loại phí. Không gửi khóa thì giữ nguyên giá hiện có; gửi object rỗng thì xoá hết; giá để `null` trong object thì bỏ qua khóa đó. Loại phí ngoài bốn mã trên trả `400`. Khi tính dòng điện nước (sinh hóa đơn hoặc `readings`) ưu tiên `fee_rate` của kỳ, không có thì lấy giá riêng của hợp đồng còn hiệu lực trong kỳ (hợp đồng bắt đầu giữa kỳ vẫn tính).

`note` là ghi chú tự do tối đa 500 ký tự (ký ngày, ngày dọn đến, tiền cọc...), gửi chuỗi rỗng hoặc `null` thì xoá ghi chú. Contract trả về kèm `houseCode` và `note`.

### Ảnh hợp đồng

| Method | Path | Vai trò | Tham số | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/contracts/{contractId}/photos` | Đọc | | Mảng `{ id, originalName, uploadedAt, contentUrl }` |
| POST | `/api/contracts/{contractId}/photos` | ADMIN, MANAGER | `file` (multipart) | `201` + ảnh như trên |
| GET | `/api/contracts/{contractId}/photos/{photoId}/content` | Đọc | | Bytes ảnh, `Content-Type` theo file |
| DELETE | `/api/contracts/{contractId}/photos/{photoId}` | ADMIN, MANAGER | | `200`, không nội dung |

Ràng buộc upload: tối đa 5 MB, chỉ `image/jpeg`, `image/png`, `image/webp`. File lưu trong thư mục `upload-dir` (Docker: volume `uploads`).

### Phí và biểu giá

| Method | Path | Vai trò | Body / query | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/billing/fee-types` | Đọc | | Mảng `{ id, code, name, unit, active }` |
| GET | `/api/billing/fee-rates` | Đọc | `feeTypeId` | Mảng `{ id, feeTypeId, feeCode, feeName, unit, period, price }` |
| PUT | `/api/billing/fee-rates` | ADMIN | `{ feeTypeId*, period*, price* }` | Biểu giá (tạo hoặc cập nhật theo `feeTypeId` + `period`) |

Biểu giá là cấu hình toàn hệ thống, vì vậy chỉ `ADMIN` được ghi.

### Chỉ số điện nước

| Method | Path | Vai trò | Body / query | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/billing/meters` | ADMIN, MANAGER | `period`, `roomId` | Mảng chỉ số (MANAGER chỉ thấy chỉ số của nhà mình là chủ hoặc quản lý) |
| POST | `/api/billing/meters` | ADMIN, MANAGER | `{ roomId*, feeTypeId*, period*, reading*, note }` | `201` + chỉ số |
| PUT | `/api/billing/meters/{id}` | ADMIN, MANAGER | `{ reading*, note }` | Chỉ số |

Chỉ số nhập ở đây được chép vào hóa đơn (`currentElectReading` / `currentWaterReading`) khi sinh hóa đơn; sau khi sinh vẫn sửa trực tiếp trên hóa đơn, không ghi ngược lại bảng này.

### Hóa đơn

| Method | Path | Vai trò | Body / query | Trả về |
| --- | --- | --- | --- | --- |
| GET | `/api/billing/invoices` | Đọc | `period`, `houseId`, `status`, `page`, `size` | Phân trang |
| GET | `/api/billing/invoices/{id}` | Đọc | | Hóa đơn kèm `lines`, `contractRent`, `roomPriceNote`, 4 trường chỉ số công tơ |
| POST | `/api/billing/invoices/generate` | ADMIN, MANAGER | `period` (query) | `{ created, skipped: [{ roomId, roomNumber, reason }] }` |
| POST | `/api/billing/invoices/{id}/publish` | ADMIN, MANAGER | Không | Hóa đơn `UNPAID` |
| POST | `/api/billing/invoices/{id}/payments` | ADMIN, MANAGER | `{ amount* }` (tối thiểu 1) | Hóa đơn sau khi cộng tiền |
| PUT | `/api/billing/invoices/{id}/room-price` | ADMIN, MANAGER | `{ amount*, note }` (tối đa 500 ký tự) | Hóa đơn với dòng `PHONG` đã đổi giá |
| PUT | `/api/billing/invoices/{id}/readings` | ADMIN, MANAGER | `{ preElectReading, currentElectReading, preWaterReading, currentWaterReading }` (số không âm, `null` là bỏ trống) | Hóa đơn với dòng điện/nước đã tính lại |
| POST | `/api/billing/invoices/{id}/lines` | ADMIN, MANAGER | `{ feeTypeId*, quantity*, unitPrice*, description }` | Dòng tiền |
| PUT | `/api/billing/invoices/{id}/lines/{lineId}` | ADMIN, MANAGER | `{ quantity*, unitPrice* }` | Dòng tiền |
| DELETE | `/api/billing/invoices/{id}/lines/{lineId}` | ADMIN, MANAGER | | Hóa đơn kèm `lines` |

Trạng thái hóa đơn: `DRAFT` (mới sinh) `publish` sang `UNPAID`, thu một phần thành `PARTIAL`, đủ tiền thành `PAID`. Khóa `totalAmount` tính lại theo các dòng tiền.

`room-price` đổi đơn giá dòng tiền phòng (dòng có `feeCode = PHONG`), ghi `note` vào `roomPriceNote`, tính lại `totalAmount` và trạng thái; hóa đơn `PAID` trả `409`. `contractRent` trong chi tiết là giá phòng theo hợp đồng phủ kỳ hóa đơn, dùng để so với giá đang áp dụng.

**Chỉ số công tơ**: hóa đơn giữ 4 trường `preElectReading`, `currentElectReading` (điện), `preWaterReading`, `currentWaterReading` (nước) là số công tơ đầu kỳ và cuối kỳ; tiêu thụ = cuối kỳ trừ đầu kỳ. `readings` cập nhật cả 4 trường trong một lần gọi, bắt buộc có đầu kỳ khi điền cuối kỳ, `current < pre` trả `400`. Tiêu thụ lớn hơn 0 thì tạo hoặc cập nhật dòng điện/nước (ưu tiên `fee_rate` của kỳ, không có thì giá ghi trong hợp đồng còn hiệu lực trong kỳ, thiếu cả hai trả `400` với thông báo `Chưa có cấu hình giá kỳ MM/yyyy`), bằng 0 thì xoá dòng đó, `current = null` thì chỉ ghi chỉ số không đụng dòng; tính lại `totalAmount` và trạng thái, hóa đơn `PAID` trả `409`.

Sinh hóa đơn (`generate`): với điện và nước, chụp chỉ số vào hóa đơn: đầu kỳ lấy `currentElectReading`/`currentWaterReading` của hóa đơn cùng phòng kỳ trước, không có thì lấy chỉ số đã nhập kỳ trước, không có gì thì `0`; cuối kỳ lấy chỉ số đã nhập qua `meters` (chưa nhập thì để trống, nhập sau bằng `readings`). Dòng tiền điện/nước tính bằng tiêu thụ (cuối kỳ trừ đầu kỳ) nhân giá (ưu tiên `fee_rate` của kỳ, không có thì giá ghi trong hợp đồng), cuối kỳ chưa nhập thì chưa có dòng; thiếu giá hoặc chỉ số cuối kỳ nhỏ hơn đầu kỳ ghi lý do trong `skipped`. Mạng và dịch vụ chung lấy giá theo cùng thứ tự ưu tiên đó, thiếu thì bỏ qua dòng đó.

## Ví dụ

```bash
# đăng nhập
curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# dùng token cho các lệnh sau
TOKEN=...
curl -s http://localhost/api/stats -H "Authorization: Bearer $TOKEN"

# sinh hóa đơn kỳ 2026-09
curl -s -X POST "http://localhost/api/billing/invoices/generate?period=2026-09" \
  -H "Authorization: Bearer $TOKEN"

# upload ảnh hợp đồng
curl -s -X POST http://localhost/api/contracts/1/photos \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@hop-dong.jpg;type=image/jpeg"
```
