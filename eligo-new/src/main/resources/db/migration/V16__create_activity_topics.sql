CREATE TABLE activity_topics (
    id BIGINT NOT NULL COMMENT '话题主键',
    normalized_name VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '小写规范名称',
    display_name VARCHAR(20) NOT NULL COMMENT '首次创建时的展示名称',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_topic_normalized(normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动话题';

CREATE TABLE activity_topic_relations (
    id BIGINT NOT NULL COMMENT '关系主键',
    activity_id BIGINT NOT NULL COMMENT '活动主键',
    topic_id BIGINT NOT NULL COMMENT '话题主键',
    sort_order TINYINT UNSIGNED NOT NULL COMMENT '活动内展示顺序',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_topic_pair(activity_id, topic_id),
    UNIQUE KEY uk_activity_topic_sort(activity_id, sort_order),
    KEY idx_activity_topic_filter(topic_id, activity_id),
    CONSTRAINT fk_activity_topic_relation_activity
        FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_topic_relation_topic
        FOREIGN KEY (topic_id) REFERENCES activity_topics(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动话题关系';
