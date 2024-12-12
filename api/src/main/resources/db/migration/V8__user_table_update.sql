ALTER TABLE users RENAME COLUMN image_url TO user_id;
ALTER TABLE users ADD CONSTRAINT unique_user_id UNIQUE (user_id);
