-- Du lieu mau demo: nguoi, nha, phong, hop dong, bieu gia, chi so, hoa don, tai khoan

-- 10 nguoi
INSERT INTO person (full_name, id_number, phone, address) VALUES
    ('Trần Văn Chủ', '000100500001', '0901000001', '12/3 Trần Nguyên Hãn, Lê Chân, Hải Phòng'),
    ('Nguyễn Văn Quản', '000100500002', '0901000002', '88 Hai Bà Trưng, Hồng Bàng, Hải Phòng'),
    ('Lê Thị Lan', '000100500003', '0901000003', '45 Võ Thị Sáu, Ngô Quyền, Hải Phòng'),
    ('Phạm Văn Hùng', '000100500004', '0901000004', '19 Hàng Đào, Hoàn Kiếm, Hà Nội'),
    ('Vũ Thị Mai', '000100500005', '0901000005', '27 Lý Thường Kiệt, Hải Phòng'),
    ('Đỗ Văn Nam', '000100500006', '0901000006', '3 Nguyễn Bỉnh Khiêm, Hải Phòng'),
    ('Hoàng Thị Hương', '000100500007', '0901000007', '91 Trần Phú, Hải Phòng'),
    ('Ngô Văn Tuấn', '000100500008', '0901000008', '14 Cầu Đất, Ngô Quyền, Hải Phòng'),
    ('Đặng Thị Oanh', '000100500009', '0901000009', '62 Điện Biên Phủ, Hải Phòng'),
    ('Lý Văn Khánh', '000100500010', '0901000010', '5 Phan Bội Châu, Hải Phòng');

-- 2 nha
INSERT INTO house (code, name, address, owner_id, manager_id) VALUES
    ('H001', 'Nhà trọ Mai Linh', '12/3 Trần Nguyên Hãn, Lê Chân, Hải Phòng',
     (SELECT id FROM person WHERE full_name = 'Trần Văn Chủ'),
     (SELECT id FROM person WHERE full_name = 'Nguyễn Văn Quản')),
    ('H002', 'Nhà trọ An Bình', '45 Võ Thị Sáu, Ngô Quyền, Hải Phòng',
     (SELECT id FROM person WHERE full_name = 'Trần Văn Chủ'),
     (SELECT id FROM person WHERE full_name = 'Lê Thị Lan'));

-- 8 phong
INSERT INTO room (house_id, room_number, area_m2)
SELECT h.id, v.room_number, v.area_m2
FROM (VALUES
    ('H001', 'A101', 25.00),
    ('H001', 'A102', 20.00),
    ('H001', 'A103', 30.00),
    ('H001', 'A104', 18.00),
    ('H002', 'B101', 22.00),
    ('H002', 'B102', 25.00),
    ('H002', 'B201', 28.00),
    ('H002', 'B202', 20.00)
) AS v(house_code, room_number, area_m2)
JOIN house h ON h.code = v.house_code;

-- tai san theo phong
INSERT INTO asset (room_id, code, name, price, purchase_date, condition, note)
SELECT r.id, v.code, v.name, v.price, v.purchase_date::date, v.condition, v.note
FROM (VALUES
    ('A101', 'H001', 'TS-A101-01', 'Giường đôi 1m6', 4500000, '2025-01-10', 'GOOD', NULL),
    ('A101', 'H001', 'TS-A101-02', 'Máy lạnh 1.5HP', 8200000, '2025-01-10', 'USED', 'Đã dùng 2 năm'),
    ('A102', 'H001', 'TS-A102-01', 'Giường đơn 1m2', 2800000, '2024-11-05', 'GOOD', NULL),
    ('A103', 'H001', 'TS-A103-01', 'Giường đôi 1m6', 4000000, '2024-08-20', 'NEEDS_REPAIR', 'Cần thay nệm'),
    ('B101', 'H002', 'TS-B101-01', 'Tủ lạnh 90 lít', 3500000, '2025-03-15', 'USED', NULL),
    ('B201', 'H002', 'TS-B201-01', 'Máy lạnh 2HP', 12000000, '2025-06-20', 'GOOD', NULL),
    ('B201', 'H002', 'TS-B201-02', 'Bàn học', 900000, '2025-06-20', 'GOOD', NULL)
) AS v(room_number, house_code, code, name, price, purchase_date, condition, note)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code);

-- 6 hop dong: 4 ACTIVE, 1 EXPIRED, 1 TERMINATED
INSERT INTO contract (room_id, holder_id, monthly_rent, start_date, end_date, status)
SELECT r.id, p.id, v.monthly_rent, v.start_date::date, v.end_date::date, v.status
FROM (VALUES
    ('A101', 'H001', 'Phạm Văn Hùng', 3500000, '2026-01-15', '2027-01-14', 'ACTIVE'),
    ('A102', 'H001', 'Hoàng Thị Hương', 2800000, '2026-03-01', '2027-02-28', 'ACTIVE'),
    ('A104', 'H001', 'Đỗ Văn Nam', 3000000, '2025-01-01', '2025-12-31', 'EXPIRED'),
    ('B101', 'H002', 'Ngô Văn Tuấn', 2500000, '2026-02-01', '2027-01-31', 'ACTIVE'),
    ('B201', 'H002', 'Đỗ Văn Nam', 3200000, '2026-01-01', '2026-12-31', 'ACTIVE'),
    ('B202', 'H002', 'Lý Văn Khánh', 2400000, '2025-06-01', '2026-05-31', 'TERMINATED')
) AS v(room_number, house_code, holder, monthly_rent, start_date, end_date, status)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code)
JOIN person p ON p.full_name = v.holder;

-- nguoi cung thue
INSERT INTO contract_tenant (contract_id, person_id)
SELECT c.id, p.id
FROM (VALUES
    ('A101', 'H001', 'Vũ Thị Mai'),
    ('B201', 'H002', 'Đặng Thị Oanh')
) AS v(room_number, house_code, person_name)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code)
JOIN contract c ON c.room_id = r.id AND c.status = 'ACTIVE'
JOIN person p ON p.full_name = v.person_name;

-- bieu gia cac ky
INSERT INTO fee_rate (fee_type_id, period, price)
SELECT ft.id, v.period, v.price
FROM (VALUES
    ('DIEN', '2026-08', 3500),
    ('NUOC', '2026-08', 22000),
    ('MANG', '2026-08', 100000),
    ('DICH_VU', '2026-08', 50000),
    ('DIEN', '2026-09', 3500),
    ('NUOC', '2026-09', 22000),
    ('MANG', '2026-09', 100000),
    ('DICH_VU', '2026-09', 50000)
) AS v(code, period, price)
JOIN fee_type ft ON ft.code = v.code;

-- chi so dong ho 4 phong dang thue, ky 07 lam so dau
INSERT INTO meter_reading (room_id, fee_type_id, period, reading)
SELECT r.id, ft.id, v.period, v.reading
FROM (VALUES
    ('A101', 'H001', 'DIEN', '2026-07', 1180),
    ('A101', 'H001', 'NUOC', '2026-07', 60),
    ('A101', 'H001', 'DIEN', '2026-08', 1250),
    ('A101', 'H001', 'NUOC', '2026-08', 66),
    ('A101', 'H001', 'DIEN', '2026-09', 1318),
    ('A101', 'H001', 'NUOC', '2026-09', 74),
    ('A102', 'H001', 'DIEN', '2026-07', 820),
    ('A102', 'H001', 'NUOC', '2026-07', 42),
    ('A102', 'H001', 'DIEN', '2026-08', 890),
    ('A102', 'H001', 'NUOC', '2026-08', 48),
    ('A102', 'H001', 'DIEN', '2026-09', 941),
    ('A102', 'H001', 'NUOC', '2026-09', 55),
    ('B101', 'H002', 'DIEN', '2026-07', 585),
    ('B101', 'H002', 'NUOC', '2026-07', 26),
    ('B101', 'H002', 'DIEN', '2026-08', 640),
    ('B101', 'H002', 'NUOC', '2026-08', 30),
    ('B101', 'H002', 'DIEN', '2026-09', 682),
    ('B101', 'H002', 'NUOC', '2026-09', 36),
    ('B201', 'H002', 'DIEN', '2026-07', 950),
    ('B201', 'H002', 'NUOC', '2026-07', 47),
    ('B201', 'H002', 'DIEN', '2026-08', 1020),
    ('B201', 'H002', 'NUOC', '2026-08', 52),
    ('B201', 'H002', 'DIEN', '2026-09', 1085),
    ('B201', 'H002', 'NUOC', '2026-09', 61)
) AS v(room_number, house_code, code, period, reading)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code)
JOIN fee_type ft ON ft.code = v.code;

-- hoa don ky 2026-08 da thu het, ky 2026-09 moi dang trang thai
INSERT INTO invoice (room_id, period, total_amount, paid_amount, status)
SELECT r.id, v.period, v.total_amount, v.paid_amount, v.status
FROM (VALUES
    ('A101', 'H001', '2026-08', 4027000, 4027000, 'PAID'),
    ('A102', 'H001', '2026-08', 3327000, 3327000, 'PAID'),
    ('B101', 'H002', '2026-08', 2930500, 2930500, 'PAID'),
    ('B201', 'H002', '2026-08', 3705000, 3705000, 'PAID'),
    ('A101', 'H001', '2026-09', 4064000, 4064000, 'PAID'),
    ('A102', 'H001', '2026-09', 3282500, 1400000, 'PARTIAL'),
    ('B101', 'H002', '2026-09', 2929000, 0, 'UNPAID'),
    ('B201', 'H002', '2026-09', 3775500, 0, 'DRAFT')
) AS v(room_number, house_code, period, total_amount, paid_amount, status)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code);

-- dong tien cua cac hoa don
INSERT INTO invoice_line (invoice_id, fee_type_id, description, quantity, unit_price, amount)
SELECT i.id, ft.id, v.description, v.quantity, v.unit_price, v.amount
FROM (VALUES
    ('A101', 'H001', '2026-08', 'PHONG', 'Tiền phòng tháng 2026-08', 1, 3500000, 3500000),
    ('A101', 'H001', '2026-08', 'DIEN', 'Điện tháng 2026-08', 70, 3500, 245000),
    ('A101', 'H001', '2026-08', 'NUOC', 'Nước tháng 2026-08', 6, 22000, 132000),
    ('A101', 'H001', '2026-08', 'MANG', 'Internet tháng 2026-08', 1, 100000, 100000),
    ('A101', 'H001', '2026-08', 'DICH_VU', 'Dịch vụ tháng 2026-08', 1, 50000, 50000),
    ('A102', 'H001', '2026-08', 'PHONG', 'Tiền phòng tháng 2026-08', 1, 2800000, 2800000),
    ('A102', 'H001', '2026-08', 'DIEN', 'Điện tháng 2026-08', 70, 3500, 245000),
    ('A102', 'H001', '2026-08', 'NUOC', 'Nước tháng 2026-08', 6, 22000, 132000),
    ('A102', 'H001', '2026-08', 'MANG', 'Internet tháng 2026-08', 1, 100000, 100000),
    ('A102', 'H001', '2026-08', 'DICH_VU', 'Dịch vụ tháng 2026-08', 1, 50000, 50000),
    ('B101', 'H002', '2026-08', 'PHONG', 'Tiền phòng tháng 2026-08', 1, 2500000, 2500000),
    ('B101', 'H002', '2026-08', 'DIEN', 'Điện tháng 2026-08', 55, 3500, 192500),
    ('B101', 'H002', '2026-08', 'NUOC', 'Nước tháng 2026-08', 4, 22000, 88000),
    ('B101', 'H002', '2026-08', 'MANG', 'Internet tháng 2026-08', 1, 100000, 100000),
    ('B101', 'H002', '2026-08', 'DICH_VU', 'Dịch vụ tháng 2026-08', 1, 50000, 50000),
    ('B201', 'H002', '2026-08', 'PHONG', 'Tiền phòng tháng 2026-08', 1, 3200000, 3200000),
    ('B201', 'H002', '2026-08', 'DIEN', 'Điện tháng 2026-08', 70, 3500, 245000),
    ('B201', 'H002', '2026-08', 'NUOC', 'Nước tháng 2026-08', 5, 22000, 110000),
    ('B201', 'H002', '2026-08', 'MANG', 'Internet tháng 2026-08', 1, 100000, 100000),
    ('B201', 'H002', '2026-08', 'DICH_VU', 'Dịch vụ tháng 2026-08', 1, 50000, 50000),
    ('A101', 'H001', '2026-09', 'PHONG', 'Tiền phòng tháng 2026-09', 1, 3500000, 3500000),
    ('A101', 'H001', '2026-09', 'DIEN', 'Điện tháng 2026-09', 68, 3500, 238000),
    ('A101', 'H001', '2026-09', 'NUOC', 'Nước tháng 2026-09', 8, 22000, 176000),
    ('A101', 'H001', '2026-09', 'MANG', 'Internet tháng 2026-09', 1, 100000, 100000),
    ('A101', 'H001', '2026-09', 'DICH_VU', 'Dịch vụ tháng 2026-09', 1, 50000, 50000),
    ('A102', 'H001', '2026-09', 'PHONG', 'Tiền phòng tháng 2026-09', 1, 2800000, 2800000),
    ('A102', 'H001', '2026-09', 'DIEN', 'Điện tháng 2026-09', 51, 3500, 178500),
    ('A102', 'H001', '2026-09', 'NUOC', 'Nước tháng 2026-09', 7, 22000, 154000),
    ('A102', 'H001', '2026-09', 'MANG', 'Internet tháng 2026-09', 1, 100000, 100000),
    ('A102', 'H001', '2026-09', 'DICH_VU', 'Dịch vụ tháng 2026-09', 1, 50000, 50000),
    ('B101', 'H002', '2026-09', 'PHONG', 'Tiền phòng tháng 2026-09', 1, 2500000, 2500000),
    ('B101', 'H002', '2026-09', 'DIEN', 'Điện tháng 2026-09', 42, 3500, 147000),
    ('B101', 'H002', '2026-09', 'NUOC', 'Nước tháng 2026-09', 6, 22000, 132000),
    ('B101', 'H002', '2026-09', 'MANG', 'Internet tháng 2026-09', 1, 100000, 100000),
    ('B101', 'H002', '2026-09', 'DICH_VU', 'Dịch vụ tháng 2026-09', 1, 50000, 50000),
    ('B201', 'H002', '2026-09', 'PHONG', 'Tiền phòng tháng 2026-09', 1, 3200000, 3200000),
    ('B201', 'H002', '2026-09', 'DIEN', 'Điện tháng 2026-09', 65, 3500, 227500),
    ('B201', 'H002', '2026-09', 'NUOC', 'Nước tháng 2026-09', 9, 22000, 198000),
    ('B201', 'H002', '2026-09', 'MANG', 'Internet tháng 2026-09', 1, 100000, 100000),
    ('B201', 'H002', '2026-09', 'DICH_VU', 'Dịch vụ tháng 2026-09', 1, 50000, 50000)
) AS v(room_number, house_code, period, code, description, quantity, unit_price, amount)
JOIN room r ON r.room_number = v.room_number
    AND r.house_id = (SELECT id FROM house WHERE code = v.house_code)
JOIN invoice i ON i.room_id = r.id AND i.period = v.period
JOIN fee_type ft ON ft.code = v.code;

-- tai khoan demo kem theo admin
INSERT INTO user_account (username, password_hash, role, person_id) VALUES
    ('quanly', '$2b$10$NK0Rv87HXOKQsZuKl2w9luaSssQDVeRlTWsUI1L53lf4erNYsLimK', 'MANAGER',
     (SELECT id FROM person WHERE full_name = 'Nguyễn Văn Quản')),
    ('nguoidung', '$2b$10$3hbr620v/BkkyGp/Gd294uFyWBoOj/Tb4aZlqvP9LfbZNNO2R63/y', 'USER',
     (SELECT id FROM person WHERE full_name = 'Phạm Văn Hùng'));
