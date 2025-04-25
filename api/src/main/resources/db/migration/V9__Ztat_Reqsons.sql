-- VXX__Alter_ztat_reasons_make_text.sql
ALTER TABLE ztat_reasons
ALTER COLUMN command_need TYPE TEXT,
    ALTER COLUMN reason_identifier TYPE TEXT,
    ALTER COLUMN url TYPE TEXT;
