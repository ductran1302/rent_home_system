-- Gia phi di kem hop dong (uu tien hon gia chung khi sinh hoa don)
CREATE TABLE contract_fee_price (
    id           BIGSERIAL PRIMARY KEY,
    contract_id  BIGINT NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    fee_code     VARCHAR(30) NOT NULL,
    price        BIGINT NOT NULL CHECK (price >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (contract_id, fee_code)
);
CREATE INDEX idx_contract_fee_price_code ON contract_fee_price(fee_code);

-- Ly do sua gia phong cua mot hoa don ky
ALTER TABLE invoice ADD COLUMN room_price_note VARCHAR(500);

-- Mau: moi hop dong mot khuyen mai khac nhau, de trong thi lay gia chung ky do
INSERT INTO contract_fee_price (contract_id, fee_code, price)
SELECT c.id, v.fee_code, v.price
FROM (VALUES
    ('A101', 'H001', 'DIEN', 3400),
    ('A101', 'H001', 'NUOC', 21000),
    ('A101', 'H001', 'MANG', 90000),
    ('A101', 'H001', 'DICH_VU', 45000),
    ('B101', 'H002', 'DIEN', 3400),
    ('B101', 'H002', 'NUOC', 21000),
    ('B201', 'H002', 'MANG', 100000),
    ('B201', 'H002', 'DICH_VU', 40000)
) AS v(room_number, house_code, fee_code, price)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code)
JOIN contract c ON c.room_id = r.id AND c.status = 'ACTIVE';
