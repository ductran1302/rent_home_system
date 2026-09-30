-- Audit cho person: nguoi tao/cap nhat, cho phep updated_at NULL khi chua tung sua
ALTER TABLE person ADD COLUMN created_by VARCHAR(100);
ALTER TABLE person ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE person ALTER COLUMN updated_at DROP NOT NULL;

-- Thoi han quan ly cua tai khoan quan ly
ALTER TABLE user_account ADD COLUMN manager_start_date DATE;
ALTER TABLE user_account ADD COLUMN manager_end_date DATE;
UPDATE user_account SET manager_start_date = created_at::date WHERE role = 'MANAGER';

-- Nhung ho so chua tung cap nhat: de trong de hien "chua cap nhat"
UPDATE person SET updated_at = NULL WHERE updated_at = created_at;
