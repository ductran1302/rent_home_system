-- Billing: phi tinh tien, chi so dong ho, hoa don
CREATE TABLE fee_type (
    id          BIGSERIAL PRIMARY KEY,
    code          VARCHAR(30) NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    unit      VARCHAR(20) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fee_rate (
    id           BIGSERIAL PRIMARY KEY,
    fee_type_id  BIGINT NOT NULL REFERENCES fee_type(id),
    period           VARCHAR(7) NOT NULL,
    price     BIGINT NOT NULL CHECK (price >= 0),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (fee_type_id, period)
);

CREATE TABLE meter_reading (
    id           BIGSERIAL PRIMARY KEY,
    room_id      BIGINT NOT NULL REFERENCES room(id),
    fee_type_id  BIGINT NOT NULL REFERENCES fee_type(id),
    period           VARCHAR(7) NOT NULL,
    reading       NUMERIC(12,2) NOT NULL CHECK (reading >= 0),
    note      VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (room_id, fee_type_id, period)
);
CREATE INDEX idx_meter_room_period ON meter_reading(room_id, period);

CREATE TABLE invoice (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL REFERENCES room(id),
    period          VARCHAR(7) NOT NULL,
    total_amount   BIGINT NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    paid_amount     BIGINT NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    status  VARCHAR(20) NOT NULL,
    note     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (room_id, period)
);
CREATE INDEX idx_invoice_period ON invoice(period);
CREATE INDEX idx_invoice_status ON invoice(status);

CREATE TABLE invoice_line (
    id           BIGSERIAL PRIMARY KEY,
    invoice_id   BIGINT NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    fee_type_id  BIGINT NOT NULL REFERENCES fee_type(id),
    description        VARCHAR(200) NOT NULL,
    quantity     NUMERIC(12,2) NOT NULL DEFAULT 1,
    unit_price      BIGINT NOT NULL CHECK (unit_price >= 0),
    amount   BIGINT NOT NULL CHECK (amount >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoice_line_invoice ON invoice_line(invoice_id);

-- seed kho phi
INSERT INTO fee_type (code, name, unit) VALUES
    ('PHONG', 'Tiền phòng', 'THANG'),
    ('DIEN', 'Điện', 'KWH'),
    ('NUOC', 'Nước', 'M3'),
    ('MANG', 'Internet', 'THANG'),
    ('DICH_VU', 'Dịch vụ', 'THANG');
