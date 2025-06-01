ALTER TABLE notification_recipients
DROP CONSTRAINT notification_recipients_user_id_fkey;

ALTER TABLE notification_recipients
    ADD CONSTRAINT notification_recipients_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
