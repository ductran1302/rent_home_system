-- Anh doi chieu tai san va anh truoc/sau khi sua chua
CREATE TABLE asset_photo (
    id            BIGSERIAL PRIMARY KEY,
    asset_id      BIGINT NOT NULL REFERENCES asset(id),
    repair_id     BIGINT REFERENCES asset_repair(id) ON DELETE CASCADE,
    stage         VARCHAR(10),
    file_path     VARCHAR(500) NOT NULL,
    original_name VARCHAR(255),
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (stage IS NULL OR stage IN ('TRUOC', 'SAU')),
    CHECK ((repair_id IS NULL AND stage IS NULL)
        OR (repair_id IS NOT NULL AND stage IS NOT NULL))
);
CREATE INDEX idx_asset_photo_asset ON asset_photo(asset_id);
CREATE INDEX idx_asset_photo_repair ON asset_photo(repair_id);
