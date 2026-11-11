CREATE SEQUENCE IF NOT EXISTS row_id_seq START 1;

CREATE TABLE IF NOT EXISTS ai_response_cache (
    cache_key VARCHAR PRIMARY KEY,
    operation VARCHAR NOT NULL,
    provider VARCHAR NOT NULL,
    model VARCHAR NOT NULL,
    prompt_version VARCHAR NOT NULL,
    response TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_response_cache_expiry ON ai_response_cache(expires_at);

CREATE TABLE IF NOT EXISTS novel (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    title VARCHAR(255),
    source TEXT
);

CREATE TABLE IF NOT EXISTS novel_character (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT NOT NULL REFERENCES novel(id),
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_character_novel ON novel_character(novel_id);

CREATE TABLE IF NOT EXISTS chunk_plan (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    chunk_limit INTEGER NOT NULL,
    confirmed BOOLEAN NOT NULL,
    advice TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chunk_plan_novel ON chunk_plan(novel_id);

CREATE TABLE IF NOT EXISTS chunk_plan_segments (
    chunk_plan_id BIGINT NOT NULL REFERENCES chunk_plan(id),
    segment_order INTEGER NOT NULL,
    segments TEXT,
    PRIMARY KEY (chunk_plan_id, segment_order)
);

CREATE TABLE IF NOT EXISTS chunk_plan_confirmation (
    chunk_plan_id BIGINT PRIMARY KEY REFERENCES chunk_plan(id)
);
INSERT INTO chunk_plan_confirmation (chunk_plan_id)
SELECT id FROM chunk_plan WHERE confirmed = TRUE
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS chunk (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    position INTEGER NOT NULL,
    content TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chunk_position ON chunk(novel_id, position);

CREATE TABLE IF NOT EXISTS panel_plan (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    chunk_id BIGINT,
    confirmed BOOLEAN NOT NULL,
    panel_count INTEGER NOT NULL,
    provenance VARCHAR(255)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_panel_plan_chunk ON panel_plan(chunk_id);

CREATE TABLE IF NOT EXISTS panel_plan_confirmation (
    panel_plan_id BIGINT PRIMARY KEY REFERENCES panel_plan(id),
    provenance VARCHAR(255)
);
ALTER TABLE panel_plan_confirmation ADD COLUMN IF NOT EXISTS provenance VARCHAR(255);
INSERT INTO panel_plan_confirmation (panel_plan_id, provenance)
SELECT id, provenance FROM panel_plan WHERE confirmed = TRUE
ON CONFLICT (panel_plan_id) DO UPDATE SET provenance = coalesce(panel_plan_confirmation.provenance, excluded.provenance);

CREATE TABLE IF NOT EXISTS panel_plan_descriptions (
    panel_plan_id BIGINT NOT NULL REFERENCES panel_plan(id),
    panel_order INTEGER NOT NULL,
    descriptions TEXT,
    PRIMARY KEY (panel_plan_id, panel_order)
);

CREATE TABLE IF NOT EXISTS panel (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    chunk_id BIGINT,
    chunk_position INTEGER NOT NULL,
    position INTEGER NOT NULL,
    description TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_panel_position ON panel(chunk_id, position);

CREATE TABLE IF NOT EXISTS studio_draft (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    first_panel_id BIGINT,
    second_panel_id BIGINT,
    narrative_order INTEGER NOT NULL,
    prompt TEXT,
    character_context TEXT,
    provenance VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS candidate (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    draft_id BIGINT,
    first_panel_id BIGINT,
    second_panel_id BIGINT,
    narrative_order INTEGER NOT NULL,
    prompt TEXT,
    state VARCHAR(255),
    image BLOB,
    media_type VARCHAR(255),
    provenance VARCHAR(255)
);

ALTER TABLE studio_draft ADD COLUMN IF NOT EXISTS ai_cache_key VARCHAR(255);

CREATE TABLE IF NOT EXISTS studio_draft_panels (
    studio_draft_id BIGINT NOT NULL,
    panel_order INTEGER NOT NULL,
    panel_id BIGINT NOT NULL,
    PRIMARY KEY (studio_draft_id, panel_order)
);

CREATE TABLE IF NOT EXISTS candidate_panels (
    candidate_id BIGINT NOT NULL,
    panel_order INTEGER NOT NULL,
    panel_id BIGINT NOT NULL,
    PRIMARY KEY (candidate_id, panel_order)
);

CREATE TABLE IF NOT EXISTS manga_entry (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL,
    novel_id BIGINT,
    candidate_id BIGINT,
    first_panel_id BIGINT,
    second_panel_id BIGINT,
    narrative_order INTEGER NOT NULL,
    filename VARCHAR(255),
    media_type VARCHAR(255),
    provenance VARCHAR(255),
    caption TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_entry_candidate ON manga_entry(candidate_id);
CREATE INDEX IF NOT EXISTS idx_reader_order ON manga_entry(novel_id, narrative_order, id);