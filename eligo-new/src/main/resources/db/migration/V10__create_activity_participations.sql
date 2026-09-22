CREATE TABLE activity_participations (
    id BIGINT NOT NULL COMMENT '活动参与关系编号',
    activity_id BIGINT NOT NULL COMMENT '活动编号',
    user_id BIGINT NOT NULL COMMENT '参与用户编号',
    status TINYINT NOT NULL COMMENT '状态：1有效，2主动取消，3终止',
    joined_at DATETIME(3) NOT NULL COMMENT '当前有效报名时间',
    cancelled_at DATETIME(3) NULL COMMENT '用户主动取消时间',
    terminated_at DATETIME(3) NULL COMMENT '活动取消或发起者移除时间',
    termination_reason TINYINT NULL COMMENT '终止原因：1活动取消，2发起者移除',
    version INT NOT NULL DEFAULT 0 COMMENT '版本',
    created_at DATETIME(3) NOT NULL COMMENT '首次创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_activity_participation_activity_user(activity_id, user_id),
    KEY idx_activity_participant_page(activity_id, status, joined_at, user_id),
    KEY idx_user_participation_page(user_id, status, joined_at, id),
    CONSTRAINT fk_activity_participation_activity
        FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_participation_user
        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_activity_participation_status CHECK(status IN(1, 2, 3)),
    CONSTRAINT chk_activity_participation_reason CHECK(
        termination_reason IS NULL OR termination_reason IN(1, 2)
    ),
    CONSTRAINT chk_activity_participation_state CHECK(
        (status=1
            AND cancelled_at IS NULL
            AND terminated_at IS NULL
            AND termination_reason IS NULL)
        OR
        (status=2
            AND cancelled_at IS NOT NULL
            AND terminated_at IS NULL
            AND termination_reason IS NULL)
        OR
        (status=3
            AND cancelled_at IS NULL
            AND terminated_at IS NOT NULL
            AND termination_reason IS NOT NULL)
    ),
    CONSTRAINT chk_activity_participation_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动参与关系及状态历史';
