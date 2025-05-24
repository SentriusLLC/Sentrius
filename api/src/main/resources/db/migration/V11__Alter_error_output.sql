ALTER TABLE error_output
ALTER COLUMN error_type TYPE TEXT,
    ALTER COLUMN error_location TYPE TEXT,
    ALTER COLUMN error_hash TYPE VARCHAR(256);
