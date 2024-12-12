CREATE TABLE known_hosts (
         id SERIAL PRIMARY KEY,
         hostname VARCHAR(255) NOT NULL,
         key_type VARCHAR(50) NOT NULL,
         key_value TEXT NOT NULL,
         UNIQUE (hostname, key_type)
);
