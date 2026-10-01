-- Chi so cong to tren hoa don: pre = chi so dau ky, current = chi so cuoi ky
ALTER TABLE invoice ADD COLUMN pre_elect_reading NUMERIC(12, 2);
ALTER TABLE invoice ADD COLUMN current_elect_reading NUMERIC(12, 2);
ALTER TABLE invoice ADD COLUMN pre_water_reading NUMERIC(12, 2);
ALTER TABLE invoice ADD COLUMN current_water_reading NUMERIC(12, 2);

-- Backfill hoa don da co chi so ki nay va dong tien tuong ung:
-- current = chi so do duoc, pre = current - so luong dong tien
UPDATE invoice i
SET current_elect_reading = m.reading,
    pre_elect_reading = m.reading - l.quantity
FROM meter_reading m, invoice_line l, fee_type ft
WHERE m.room_id = i.room_id
  AND m.period = i.period
  AND ft.code = 'DIEN'
  AND m.fee_type_id = ft.id
  AND l.invoice_id = i.id
  AND l.fee_type_id = ft.id;

UPDATE invoice i
SET current_water_reading = m.reading,
    pre_water_reading = m.reading - l.quantity
FROM meter_reading m, invoice_line l, fee_type ft
WHERE m.room_id = i.room_id
  AND m.period = i.period
  AND ft.code = 'NUOC'
  AND m.fee_type_id = ft.id
  AND l.invoice_id = i.id
  AND l.fee_type_id = ft.id;
