CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- This migration adds a new column `communication_id` to the `agent_communications` table.
ALTER TABLE agent_communications
    ADD COLUMN communication_id UUID NOT NULL DEFAULT gen_random_uuid();