CREATE TABLE command_categories (
    id SERIAL PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    pattern TEXT NOT NULL, -- Store regex patterns
    priority INT NOT NULL DEFAULT 0 -- Optional: for matching precedence
);


CREATE INDEX idx_pattern ON command_categories (pattern);