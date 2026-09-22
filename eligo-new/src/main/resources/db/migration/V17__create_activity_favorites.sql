CREATE TABLE activity_favorites (
    id BIGINT NOT NULL COMMENT '收藏关系主键',
    user_id BIGINT NOT NULL COMMENT '收藏用户主键',
    activity_id BIGINT NOT NULL COMMENT '活动主键',
    favorited_at DATETIME(3) NOT NULL COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_favorite_pair(user_id, activity_id),
    KEY idx_activity_favorite_page(user_id, favorited_at, id),
    KEY idx_activity_favorite_activity(activity_id, id),
    CONSTRAINT fk_activity_favorite_user
        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_favorite_activity
        FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动收藏关系';
