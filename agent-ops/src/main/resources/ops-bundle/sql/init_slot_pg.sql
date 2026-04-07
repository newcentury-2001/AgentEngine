CREATE TABLE IF NOT EXISTS slot_definition (
  slot_key   VARCHAR(128) PRIMARY KEY,
  slot_name  VARCHAR(128) NOT NULL DEFAULT '',
  source     VARCHAR(64)  NOT NULL DEFAULT 'mcp_inferred',
  is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS skill_slot_binding (
  skill_name VARCHAR(128) NOT NULL,
  slot_key   VARCHAR(128) NOT NULL,
  created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (skill_name, slot_key),
  CONSTRAINT fk_skill_slot_binding_slot_key
    FOREIGN KEY (slot_key) REFERENCES slot_definition(slot_key)
);

CREATE TABLE IF NOT EXISTS tool_input_slot_binding (
  skill_name VARCHAR(128) NOT NULL,
  tool_name  VARCHAR(200) NOT NULL,
  slot_key   VARCHAR(128) NOT NULL,
  field_path VARCHAR(256) NOT NULL,
  field_type VARCHAR(64)  NOT NULL DEFAULT '',
  required   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (skill_name, tool_name, slot_key, field_path),
  CONSTRAINT fk_tool_input_slot_binding_slot_key
    FOREIGN KEY (slot_key) REFERENCES slot_definition(slot_key)
);

CREATE TABLE IF NOT EXISTS tool_output_slot_inferred (
  skill_name VARCHAR(128) NOT NULL,
  tool_name  VARCHAR(200) NOT NULL,
  slot_key   VARCHAR(128) NOT NULL,
  confidence VARCHAR(16)  NOT NULL,
  created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (skill_name, tool_name, slot_key),
  CONSTRAINT fk_tool_output_slot_inferred_slot_key
    FOREIGN KEY (slot_key) REFERENCES slot_definition(slot_key)
);

