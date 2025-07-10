CREATE TABLE agent_contexts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    context TEXT NOT NULL, -- YAML or any other string format
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE agent_launches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id TEXT NOT NULL,                         -- e.g., name or UUID of the launched agent
    context_id UUID NOT NULL REFERENCES agent_contexts(id),
    launched_by TEXT,                               -- who or what initiated it
    launch_parameters TEXT,                        -- optional overrides or launch args
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);