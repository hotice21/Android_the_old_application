-- V20: Re-create empty shell tables for entities still referenced by Java code
-- but marked as "obsolete" in the simplified schema.
-- These tables exist solely to avoid SQL errors; they return empty data.

CREATE TABLE IF NOT EXISTS activity_topics (
    id BIGINT NOT NULL PRIMARY KEY,
    normalized_name VARCHAR(64) COLLATE utf8mb4_bin NULL,
    display_name VARCHAR(20) NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='activity topics (shell)';

CREATE TABLE IF NOT EXISTS activity_topic_relations (
    id BIGINT NOT NULL PRIMARY KEY,
    activity_id BIGINT NULL,
    topic_id BIGINT NULL,
    sort_order TINYINT UNSIGNED NULL,
    created_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='activity topic relations (shell)';
