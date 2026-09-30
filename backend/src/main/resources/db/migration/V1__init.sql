-- RuinHome core schema
CREATE TABLE person (
    id          BIGSERIAL PRIMARY KEY,
    full_name      VARCHAR(200) NOT NULL,
    id_number        VARCHAR(20),
    phone         VARCHAR(20),
    address     VARCHAR(500),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE house (
    id          BIGSERIAL PRIMARY KEY,
    code      VARCHAR(50) NOT NULL UNIQUE,
    name     VARCHAR(200) NOT NULL,
    address     VARCHAR(500) NOT NULL,
    owner_id    BIGINT NOT NULL REFERENCES person(id),
    manager_id  BIGINT NOT NULL REFERENCES person(id),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_house_owner ON house(owner_id);
CREATE INDEX idx_house_manager ON house(manager_id);

CREATE TABLE room (
    id           BIGSERIAL PRIMARY KEY,
    house_id     BIGINT NOT NULL REFERENCES house(id),
    room_number     VARCHAR(20) NOT NULL,
    area_m2 NUMERIC(8,2) CHECK (area_m2 IS NULL OR area_m2 > 0),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (house_id, room_number)
);
CREATE INDEX idx_room_house ON room(house_id);

CREATE TABLE asset (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL REFERENCES room(id),
    code       VARCHAR(50) NOT NULL UNIQUE,
    name      VARCHAR(200) NOT NULL,
    price    BIGINT NOT NULL CHECK (price >= 0),
    purchase_date    DATE,
    condition  VARCHAR(30) NOT NULL,
    note     VARCHAR(1000),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_room ON asset(room_id);

CREATE TABLE contract (
    id                BIGSERIAL PRIMARY KEY,
    room_id           BIGINT NOT NULL REFERENCES room(id),
    holder_id         BIGINT NOT NULL REFERENCES person(id),
    monthly_rent   BIGINT NOT NULL CHECK (monthly_rent >= 0),
    start_date      DATE NOT NULL,
    end_date     DATE NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date > start_date)
);

-- 1 phong chi co 1 hop dong ACTIVE tai 1 thoi diem (truong hop chuyen phong)
CREATE UNIQUE INDEX uniq_contract_active_per_room
    ON contract (room_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_contract_room ON contract(room_id);
CREATE INDEX idx_contract_holder ON contract(holder_id);

CREATE TABLE contract_tenant (
    contract_id BIGINT NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    person_id   BIGINT NOT NULL REFERENCES person(id),
    PRIMARY KEY (contract_id, person_id)
);
CREATE INDEX idx_contract_tenant_person ON contract_tenant(person_id);

CREATE TABLE contract_photo (
    id             BIGSERIAL PRIMARY KEY,
    contract_id    BIGINT NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    file_path      VARCHAR(500) NOT NULL,
    original_name  VARCHAR(255),
    uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contract_photo_contract ON contract_photo(contract_id);

CREATE TABLE user_account (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20) NOT NULL,
    person_id      BIGINT REFERENCES person(id),
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_account_person ON user_account(person_id);
