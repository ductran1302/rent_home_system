-- Thong bao quan trong cho nguoi thue, hien thi tren header (mau do, chay)
CREATE TABLE important_notice (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    house_id   BIGINT REFERENCES house(id),
    starts_at  TIMESTAMPTZ,
    ends_at    TIMESTAMPTZ,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_important_notice_house ON important_notice(house_id);
