-- Số tài khoản ngân hàng Vietcombank của người dùng, dùng sinh mã QR chuyển tiền cho khách thuê.
ALTER TABLE user_account ADD COLUMN bank_account VARCHAR(30);
