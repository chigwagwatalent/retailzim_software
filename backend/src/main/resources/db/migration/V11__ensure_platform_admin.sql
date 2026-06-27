INSERT INTO saas_admins (username, email, password_hash, first_name, last_name, is_active)
VALUES (
  'platform',
  'platform@retailzw.co.zw',
  '$2a$12$so8jFtxbwndrb9U/en6o2eIOrbWLIyC/8Pz4LTSgbQZDQpT5yKxyW',
  'Platform',
  'Admin',
  TRUE
)
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  email = VALUES(email),
  password_hash = VALUES(password_hash),
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  is_active = TRUE;
