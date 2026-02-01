-- Add NON_PERSON_ENTITY to the target_type check constraint for attribute_assignments
-- This allows agents and service accounts to have attribute assignments

-- Drop the existing constraint
ALTER TABLE attribute_assignments DROP CONSTRAINT IF EXISTS attribute_assignments_target_type_check;

-- Add the updated constraint with NON_PERSON_ENTITY included
ALTER TABLE attribute_assignments ADD CONSTRAINT attribute_assignments_target_type_check
    CHECK (target_type IN ('USER', 'NON_PERSON_ENTITY', 'ROLE', 'GROUP', 'ENDPOINT', 'DATA_ENTITY', 'OPERATION', 'SYSTEM'));

