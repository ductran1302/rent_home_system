-- Khu vuc cho mot chu cho thue (area_admin = username chu cho thue)
ALTER TABLE house ADD COLUMN area_admin VARCHAR(100);
ALTER TABLE person ADD COLUMN area_admin VARCHAR(100);
ALTER TABLE user_account ADD COLUMN area_admin VARCHAR(100);

-- Admin goc thay duoc toan bo, cac admin dang ky chi thay khu vuc minh
ALTER TABLE user_account ADD COLUMN is_root BOOLEAN NOT NULL DEFAULT FALSE;

-- Du lieu cu thuoc ve admin goc
UPDATE house SET area_admin = 'admin';
UPDATE person SET area_admin = 'admin';
UPDATE user_account SET area_admin = 'admin';
UPDATE user_account SET is_root = TRUE WHERE username = 'admin';
