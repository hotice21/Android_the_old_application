CREATE TABLE post_recommendation_index_jobs (
    post_id BIGINT NOT NULL COMMENT '动态编号',
    generation BIGINT NOT NULL COMMENT '期望状态世代编号',
    desired_action TINYINT NOT NULL COMMENT '1写入向量，2删除向量',
    content_fingerprint CHAR(64) NULL COMMENT '动态推荐文本摘要',
    task_status TINYINT NOT NULL COMMENT '1待处理，2处理中，3成功，4失败',
    attempt_count INT NOT NULL COMMENT '已尝试次数',
    next_attempt_at DATETIME(3) NOT NULL COMMENT '下次可领取时间',
    processing_started_at DATETIME(3) NULL COMMENT '本次领取时间',
    indexed_model_version VARCHAR(64) NULL COMMENT '成功写入的模型版本',
    last_error_code VARCHAR(64) NULL COMMENT '最近错误类型',
    last_error_summary VARCHAR(512) NULL COMMENT '脱敏错误摘要',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY(post_id),
    KEY idx_recommendation_job_due(task_status, next_attempt_at, post_id),
    CONSTRAINT fk_recommendation_job_post
        FOREIGN KEY(post_id) REFERENCES posts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_recommendation_job_generation CHECK(generation>=1),
    CONSTRAINT chk_recommendation_job_action CHECK(desired_action IN(1,2)),
    CONSTRAINT chk_recommendation_job_status CHECK(task_status IN(1,2,3,4)),
    CONSTRAINT chk_recommendation_job_attempt CHECK(attempt_count>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态推荐向量索引任务';
