-- Rename the table
ALTER TABLE user_theme RENAME TO user_settings;

-- Add the json_config column
ALTER TABLE user_settings ADD COLUMN json_config TEXT;