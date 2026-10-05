#!/usr/bin/env bash
# Cai dat moi truong tren server Ubuntu/Debian.
# Su dung: bash deploy/setup.sh [thu muc repo]
set -euo pipefail

REPO_DIR="${1:-$HOME/rent_home_system}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Dang cai Docker..."
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"
echo "Da them user $(whoami) vao nhom docker (dang nhap lai SSH de co hieu luc)."

if command -v ufw >/dev/null 2>&1; then
  sudo ufw allow OpenSSH
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  sudo ufw --force enable
  echo "Da bat ufw, chi mo port 22/80/443."
fi

cd "$REPO_DIR"

if [ ! -f .env ]; then
  JWT_SECRET=$(python3 -c "import secrets; print(secrets.token_hex(32))")
  ADMIN_PW=$(python3 -c "import secrets, string; print(''.join(secrets.choice(string.ascii_letters + string.digits) for _ in range(12)))")
  sed -e "s|^JWT_SECRET=.*|JWT_SECRET=${JWT_SECRET}|" \
      -e "s|^SEED_ADMIN_PASSWORD=.*|SEED_ADMIN_PASSWORD=${ADMIN_PW}|" \
      -e "s|^ALLOW_SELF_REGISTER=.*|ALLOW_SELF_REGISTER=false|" \
      .env.example > .env
  echo "Da tao .env."
  echo "  JWT_SECRET: sinh ngau nhien (64 ky tu)"
  echo "  Mat khau admin lan dau: ${ADMIN_PW}   (ghi lai, doi ngay sau khi dang nhap)"
else
  echo ".env da ton tai, bo qua."
fi

echo ""
echo "Xong. Buoc tiep theo:"
echo "  docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d --build"
echo "  curl http://localhost/api/health"
