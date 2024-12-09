create table if not exists "configuration_options" (
   id BIGSERIAL PRIMARY KEY,
   "configuration_name" character varying(250) NOT NULL,
    "configuration_value" text NOT NULL
    );