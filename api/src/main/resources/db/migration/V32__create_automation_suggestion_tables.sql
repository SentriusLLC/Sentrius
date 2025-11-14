-- Create automation_suggestions table
CREATE TABLE IF NOT EXISTS automation_suggestions (
    id BIGSERIAL PRIMARY KEY,
    session_ids TEXT,
    suggested_script TEXT NOT NULL,
    description TEXT,
    script_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    confidence_score DOUBLE PRECISION,
    pattern_frequency INTEGER,
    suggested_for_user_id BIGINT,
    target_system VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    automation_id BIGINT,
    metadata TEXT,
    CONSTRAINT fk_automation_suggestion_user FOREIGN KEY (suggested_for_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_automation_suggestion_automation FOREIGN KEY (automation_id) REFERENCES automation(id) ON DELETE SET NULL
);

-- Create automation_suggestion_reviews table
CREATE TABLE IF NOT EXISTS automation_suggestion_reviews (
    id BIGSERIAL PRIMARY KEY,
    suggestion_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NOT NULL,
    decision VARCHAR(50) NOT NULL,
    review_comments TEXT,
    reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_script TEXT,
    CONSTRAINT fk_review_suggestion FOREIGN KEY (suggestion_id) REFERENCES automation_suggestions(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_user FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_automation_suggestions_status ON automation_suggestions(status);
CREATE INDEX IF NOT EXISTS idx_automation_suggestions_confidence ON automation_suggestions(confidence_score DESC);
CREATE INDEX IF NOT EXISTS idx_automation_suggestions_created_at ON automation_suggestions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_automation_suggestions_user ON automation_suggestions(suggested_for_user_id);
CREATE INDEX IF NOT EXISTS idx_automation_suggestion_reviews_suggestion ON automation_suggestion_reviews(suggestion_id);
CREATE INDEX IF NOT EXISTS idx_automation_suggestion_reviews_user ON automation_suggestion_reviews(reviewed_by_user_id);
