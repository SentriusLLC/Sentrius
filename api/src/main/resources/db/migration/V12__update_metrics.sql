ALTER TABLE terminal_behavior_metrics
ALTER COLUMN max_idle_time
TYPE NUMERIC(21,0)
USING EXTRACT(EPOCH FROM max_idle_time)::NUMERIC(21,0);
