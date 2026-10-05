# Deploy lên server miễn phí (Oracle Cloud Always Free)

Mục tiêu: chạy nguyên `docker-compose.yml` trên một VPS Ubuntu miễn phí vĩnh viễn, không sửa code.

## Kiến trúc khi deploy

```
Internet -> Oracle VM (port 80, optional 443)
  web    : nginx, serve SPA + proxy /api -> api:8080
  api    : Spring Boot (chỉ nội bộ, host bind 127.0.0.1:8081)
  postgres: PostgreSQL 16 (chỉ nội bộ, host bind 127.0.0.1:5432, volume pgdata)
  cloudflared (optional): tunnel HTTPS qua Cloudflare
```

File mới dùng cho deploy:

| File | Vai trò |
| --- | --- |
| `deploy/setup.sh` | Cài Docker, bật ufw, sinh `.env` (JWT_SECRET ngẫu nhiên, mật khẩu admin) |
| `deploy/docker-compose.prod.yml` | Override: `restart: unless-stopped`, JVM theo RAM, service `cloudflared` (profile `tunnel`) |
| `deploy/backup.sh` | `pg_dump` định kỳ, giữ 14 bản gần nhất |

## Bước 1: Tạo VM trên Oracle Cloud

1. Đăng ký tại `cloud.oracle.com` (cần thẻ tín dụng để xác minh, luôn miễn phí, không bị trừ tiền).
2. Chọn region có chỗ cho Always Free: Tokyo (`ap-tokyo-1`) hoặc Singapore (`ap-singapore-1`).
3. Menu Compute -> Instances -> Create instance:
   - Image: Ubuntu 22.04 (hoặc 24.04) hoặc Debian 12.
   - Shape: chọn **VM.Standard.A1.Flex** (ARM), đặt 2 OCPU và 12 GB RAM (Always Free cho tối đa 4 OCPU / 24 GB). Không dùng AMD micro shape 1 GB vì không đủ cho Spring Boot.
   - Boot volume: 50 GB.
   - SSH key: tạo trên máy cá nhân bằng `ssh-keygen`, dán public key vào form.
4. Trong VCN Security List, mở inbound: TCP 22, 80, 443 (0.0.0.0/0).

## Bước 2: Cài đặt lên server

```bash
ssh ubuntu@<IP_VM>
git clone https://github.com/ductran1302/rent_home_system.git
cd rent_home_system
bash deploy/setup.sh
```

Repo riêng thì thay bằng một trong hai cách:

- Dùng Personal Access Token: `git clone https://<TOKEN>@github.com/ductran1302/rent_home_system.git`
- Chép từ máy local: `scp -r D:/PPC_DEV/workspace/RuinHome ubuntu@<IP_VM>:/home/ubuntu/rent_home_system`

`setup.sh` làm các việc: cài Docker (nếu chưa có), thêm user vào nhóm `docker`, bật `ufw` chỉ mở 22/80/443, tạo `.env` với `JWT_SECRET` sinh ngẫu nhiên 64 ký tự và mật khẩu admin in ra màn hình (ghi lại ngay).

Đăng nhập lại SSH một lần để nhóm `docker` có hiệu lực:

```bash
exit
ssh ubuntu@<IP_VM>
```

## Bước 3: Build và chạy

```bash
cd ~/rent_home_system
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
```

Lần đầu build mất vài phút (Maven tải dependency trên ARM). Kiểm tra:

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ps
curl http://localhost/api/health          # phải trả về {"status":"OK"}
```

Mở `http://<IP_VM>` từ máy cá nhân, đăng nhập `admin` với mật khẩu do `setup.sh` in ra.

Sau lần đăng nhập đầu, đổi mật khẩu ngay: trang **Quản lý -> Tài khoản -> Sửa** (mật khẩu để trống thì không đổi).

## Bước 4: HTTPS (khuyến nghị, miễn phí)

Cách đơn giản nhất là Cloudflare Tunnel: có HTTPS free, không cần mở port 443, không cần cert.

1. Tạo tài khoản Cloudflare (free), thêm một domain (hoặc dùng hostname con của domain đã có).
2. Dashboard -> Zero Trust -> Networks -> Tunnels -> Create a tunnel, chọn OS = Docker, copy token.
3. Đặt token vào `.env` trên server: thêm dòng `TUNNEL_TOKEN=<token>`.
4. Chạy kèm tunnel:

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml --profile tunnel up -d
```

5. Trong phần Public Hostname của tunnel, trỏ domain (ví dụ `ruinhome.example.com`) về `http://web:80`.
6. Truy cập `https://ruinhome.example.com`.

Không có domain thì bỏ bước này, dùng `http://<IP_VM>` (phù hợp khi chỉ nội bộ hoặc thử nghiệm).

## Bước 5: Sao lưu tự động

Chạy thử một lần:

```bash
bash deploy/backup.sh
```

Thêm cron (chạy lúc 03:00 mỗi ngày, giữ 14 bản):

```bash
crontab -e
```

Thêm dòng:

```
0 3 * * * /home/ubuntu/rent_home_system/deploy/backup.sh >> /home/ubuntu/ruinhome-backup.log 2>&1
```

Bản sao lưu nằm trong `rent_home_system/backups/`. Khôi phục (thay `<file>`):

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml exec -T postgres \
  psql -U ruinhome -d ruinhome < <(gunzip -c backups/<file>.sql.gz)
```

Lưu ý: ảnh và file upload nằm trong volume `uploads`, muốn backup đầy đủ thì thêm lệnh `docker run --rm -v ruinhome_uploads:/data -v $PWD/backups:/backup alpine tar czf /backup/uploads-$(date +%F).tgz -C /data .`.

## Bước 6: Cập nhật phiên bản mới

```bash
cd ~/rent_home_system
git pull
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build
```

Database tự cập nhật qua Flyway khi API khởi động lại.

## Bảo mật đã có sẵn

- `postgres` và `api` chỉ bind `127.0.0.1` trên host, không lộ ra Internet.
- `JWT_SECRET` sinh ngẫu nhiên trong `.env` (`.env` không nằm trong git).
- `ALLOW_SELF_REGISTER=false` (đăng ký công khai tắt).
- `ufw` chỉ mở 22, 80, 443.

## Sự cố thường gặp

| Triệu chứng | Xử lý |
| --- | --- |
| Build báo `exec format error` | Sai shape (phải là ARM A1.Flex, image Ubuntu/Debian chuẩn) |
| API restart liên tục, log báo heap | Tăng `-XX:MaxRAMPercentage` trong `deploy/docker-compose.prod.yml` |
| `port is already allocated` | `docker ps` xem còn container cũ, `docker compose down` rồi up lại |
| Muốn reset toàn bộ dữ liệu | `docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml down -v` rồi up lại (mất dữ liệu) |
| Xem log service | `docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f api` |
| VM hết RAM khi build | Dừng service khác, hoặc build trên máy local rồi `docker save` / `scp` image sang server |
