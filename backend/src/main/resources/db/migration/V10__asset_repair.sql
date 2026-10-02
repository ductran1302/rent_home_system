-- Quan ly tai san: nhom tai san, ban giao theo hop dong, lich su sua chua
ALTER TABLE asset ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'KHAC';

-- Doan lai nhom cho du lieu mau theo ten tai san
UPDATE asset SET category = CASE
    WHEN name ILIKE '%Giường%' THEN 'GIUONG'
    WHEN name ILIKE '%Tủ lạnh%' THEN 'TU_LANH'
    WHEN name ILIKE '%Máy lạnh%' OR name ILIKE '%Điều hòa%' THEN 'DIEU_HOA'
    WHEN name ILIKE '%Bình nóng lạnh%' THEN 'BINH_NONG_LANH'
    WHEN name ILIKE '%Bàn%' THEN 'BAN_GHE'
    WHEN name ILIKE '%Tủ%' THEN 'TU'
    ELSE 'KHAC'
END;

-- Ban giao / thu hoi tai san cua tung hop dong
CREATE TABLE contract_asset (
    id                 BIGSERIAL PRIMARY KEY,
    contract_id        BIGINT NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    asset_id           BIGINT NOT NULL REFERENCES asset(id),
    handover_condition VARCHAR(30) NOT NULL,
    return_condition   VARCHAR(30),
    handover_note      VARCHAR(500),
    returned_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (contract_id, asset_id)
);
CREATE INDEX idx_contract_asset_contract ON contract_asset(contract_id);
CREATE INDEX idx_contract_asset_asset ON contract_asset(asset_id);

-- Lich su sua chua tai san, lam can cu khau hao khi hop dong ket thuc
CREATE TABLE asset_repair (
    id           BIGSERIAL PRIMARY KEY,
    asset_id     BIGINT NOT NULL REFERENCES asset(id),
    reported_at  DATE NOT NULL,
    description  VARCHAR(500) NOT NULL,
    cost         BIGINT NOT NULL DEFAULT 0 CHECK (cost >= 0),
    status       VARCHAR(20) NOT NULL,
    done_at      DATE,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_repair_asset ON asset_repair(asset_id);
CREATE INDEX idx_asset_repair_status ON asset_repair(status);
CREATE INDEX idx_asset_repair_reported_at ON asset_repair(reported_at);

-- Mau sua chua
INSERT INTO asset_repair (asset_id, reported_at, description, cost, status, done_at, note)
SELECT a.id, v.reported_at::date, v.description, v.cost, v.status, v.done_at::date, v.note
FROM (VALUES
    ('TS-A103-01', '2026-08-12', 'Thay nệm giường bị xệ', 650000, 'PENDING', NULL, 'Chờ đặt nệm mới'),
    ('TS-A101-02', '2026-09-03', 'Vệ sinh máy lạnh định kỳ', 300000, 'DONE', '2026-09-04', NULL),
    ('TS-B201-01', '2026-09-20', 'Thay block máy lạnh', 1800000, 'DONE', '2026-09-22', 'Bảo hành 6 tháng')
) AS v(asset_code, reported_at, description, cost, status, done_at, note)
JOIN asset a ON a.code = v.asset_code;
