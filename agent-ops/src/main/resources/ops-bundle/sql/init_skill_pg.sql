CREATE TABLE IF NOT EXISTS mcp_tool_semantic (
  skill_name VARCHAR(128) NOT NULL,
  tool_name VARCHAR(200) NOT NULL,
  tool_description TEXT NOT NULL DEFAULT '',
  input_schema TEXT NOT NULL DEFAULT '',
  tool_url TEXT NOT NULL DEFAULT '',
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (skill_name, tool_name)
);

ALTER TABLE mcp_tool_semantic
  ADD COLUMN IF NOT EXISTS tool_url TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS mcp_tool_vector (
  skill_name VARCHAR(128) NOT NULL,
  tool_name VARCHAR(200) NOT NULL,
  normalized_vector JSONB NOT NULL,
  recent_7d_count BIGINT NOT NULL,
  heat_weight DOUBLE PRECISION NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (skill_name, tool_name)
);

CREATE TABLE IF NOT EXISTS skill_vector_snapshot (
  skill_name VARCHAR(128) PRIMARY KEY,
  skill_description TEXT NOT NULL DEFAULT '',
  skill_vector JSONB NOT NULL,
  tool_package_vector JSONB NOT NULL,
  final_skill_vector JSONB NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_vector_snapshot_skill_name
  ON skill_vector_snapshot(skill_name);

