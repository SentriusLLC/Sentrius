CREATE TABLE work_hours (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    day_of_week SMALLINT CHECK (day_of_week BETWEEN 0 AND 6), -- 0 = Sunday, 6 = Saturday
    start_time TIME NOT NULL,  -- Example: '09:00:00'
    end_time TIME NOT NULL  -- Example: '17:00:00'
);

-- Ensure fast lookups for checking dem hours
CREATE INDEX idx_work_hours ON work_hours (user_id, day_of_week);
