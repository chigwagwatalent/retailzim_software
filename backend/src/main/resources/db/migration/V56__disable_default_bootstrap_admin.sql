-- Disable only the original known bootstrap credential. Preserve changed passwords.
UPDATE saas_admins
SET is_active = FALSE
WHERE username = 'superadmin'
  AND password_hash = '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lha';
