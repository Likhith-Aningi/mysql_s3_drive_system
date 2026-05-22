-- password for all users: "password"  ($2a$10$ BCrypt, 10 rounds)
INSERT INTO users (email, name, password, created_at) VALUES
  ('alice@example.com', 'Alice', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '2024-01-01 00:00:00'),
  ('bob@example.com',   'Bob',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '2024-01-02 00:00:00');

-- alice owns files 1 and 2, bob owns file 3
INSERT INTO file_metadata (original_name, s3_key, content_type, file_size, owner_id, uploaded_at) VALUES
  ('report.pdf', 'users/1/abc-001/report.pdf', 'application/pdf', 204800,  1, '2024-01-10 10:00:00'),
  ('photo.jpg',  'users/1/abc-002/photo.jpg',  'image/jpeg',      1048576, 1, '2024-01-11 11:00:00'),
  ('notes.txt',  'users/2/abc-003/notes.txt',  'text/plain',      512,     2, '2024-01-12 09:00:00');
