# RuinHome — Agent Instructions

## Dự án

Quản lý nhà cho thuê. Monorepo:

- `backend/` — Spring Boot 3.5, Java 17 (local) / 21 (Docker), PostgreSQL, Flyway, JWT
- `frontend/` — Vite + React 19 + TypeScript + Ant Design
- `docker-compose.yml` — postgres, api, web
- `docs/ui-checklist.md` — UI rules (trích từ taste-skill, MIT)
- `README.md` — cách chạy (Docker và dev), lệnh thường dùng, quy ước rút gọn
- `docs/api.md` — danh sách endpoint, phân trang, lỗi, vai trò
- `docs/architecture.md` — kiến trúc backend, database, luồng hóa đơn, frontend

## TRƯỚC MỌI TASK FRONTEND

**Đọc `docs/ui-checklist.md`.** Đây là quy tắc UI cố định của dự án (states, form, a11y, format tiền VNĐ, copy tiếng Việt, cấm em-dash, anti-AI-tells). Nguồn đầy đủ (nếu cần tra chi tiết): `D:\tools\taste-skill\skills\taste-skill\SKILL.md`.

## Java — KHÔNG đổi config hệ thống

- `JAVA_HOME` system đang là JDK 8 (dự án khác cần) → **không được sửa**, không đổi PATH.
- Local dev: Corretto 17 tại `C:\Users\Admin\.jdks\corretto-17.0.17`, set per-session qua `.\dev.cmd`.
- Docker: build `maven:3.9-eclipse-temurin-21`, runtime `eclipse-temurin:21-jre`.
- Luôn dùng Maven Wrapper qua `dev.cmd`, không gọi `mvn` global.
- **ExecutionPolicy chặn mọi `.ps1`** trên máy này → chỉ dùng `dev.cmd` (batch). File `.bat/.cmd` bắt buộc CRLF.

## Commands (PowerShell)

```powershell
# Backend
.\dev.cmd backend spring-boot:run      # chạy app (port 8080)
.\dev.cmd backend verify               # build + test
.\dev.cmd backend flyway:info          # kiểm tra migration

# Frontend (dev.cmd đã goi npm.cmd, không lo ExecutionPolicy)
.\dev.cmd frontend dev                 # Vite dev server (port 5173, proxy /api -> 8080)
.\dev.cmd frontend build               # production build
.\dev.cmd frontend lint                # ESLint
```

## Quy ước

- **Tên file/code**: tiếng Anh. **UI string**: tiếng Việt (xem checklist §5).
- **Flyway**: chỉ thêm `V<n>__*.sql` mới, không sửa file đã apply. Local dev reset: `docker compose down -v` rồi chạy lại.
- **Tiền**: cột `BIGINT` (VND không thập phân), hiển thị qua formatter `vi-VN` chung (frontend) — không format rải rác.
- **Hợp đồng**: 1 phòng chỉ 1 hợp đồng `ACTIVE` (partial unique index ở DB) — mọi thay đổi trạng thái phải qua service, không update trực tiếp repo.
- **Soft delete**: `active = false` cho house/room/person/asset. Không `DELETE` row có FK lịch sử (contract, invoice).
- **Module backend**: package theo domain (`person`, `house`, `room`, `asset`, `contract`, `billing`, `auth`, `user`, `file`, `common`) — không import chéo ngang, controller không truy cập repository trực tiếp.
- **Không commit** khi chưa được yêu cầu.
