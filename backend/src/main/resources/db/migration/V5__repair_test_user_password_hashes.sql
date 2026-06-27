-- Repair seeded TEST001 users with a valid BCrypt hash for Admin@1234.
-- Earlier seed data used a placeholder value that Spring Security rejects.
UPDATE users
SET password_hash = '$2a$12$1pbm6SKa/uSobDPUe0bVDOy1Iwep5a23.pZ86.gE7Hu.au3TI1sD2'
WHERE tenant_id = 1
  AND username IN ('admin', 'manager', 'stockclerk', 'cashier1', 'accountant', 'custservice')
  AND password_hash = '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lha';
