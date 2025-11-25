create table if not exists "configuration_options" (
   id BIGSERIAL PRIMARY KEY,
   "pod_name" character varying(255),
   "configuration_name" character varying(250) NOT NULL,
    "configuration_value" text NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_config_pod_name ON configuration_options (pod_name, configuration_name);