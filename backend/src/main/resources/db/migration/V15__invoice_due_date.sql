ALTER TABLE invoice ADD COLUMN due_date DATE;

UPDATE invoice SET due_date = created_at::date + INTERVAL '7 days';
