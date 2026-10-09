-- ============================================================================
-- V19 Schema Simplification + Social Features
-- 1) DROP 15 obsolete tables
-- 2) ALTER 3 existing tables (user_follows, activity_comments, posts)
-- 3) CREATE 6 new tables (user_locations, user_blocks, sensitive_words,
--    chat_conversations, chat_messages, post_likes)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- STEP 1: DROP tables (reverse FK dependency order)
-- ----------------------------------------------------------------------------

-- 审计/合规 6 张
DROP TABLE IF EXISTS agreement_consents;
DROP TABLE IF EXISTS agreements;
DROP TABLE IF EXISTS account_security_events;
DROP TABLE IF EXISTS user_data_request_events;
DROP TABLE IF EXISTS user_data_requests;
DROP TABLE IF EXISTS user_profile_change_logs;

-- 设备/会话 2 张（FK → user_login_sessions 被 account_security_events 引用，已先删）
DROP TABLE IF EXISTS user_login_sessions;
DROP TABLE IF EXISTS user_devices;

-- 企业相关 2 张
DROP TABLE IF EXISTS organization_follows;
DROP TABLE IF EXISTS organization_members;

-- 话题/标签 4 张
DROP TABLE IF EXISTS activity_topic_relations;
DROP TABLE IF EXISTS activity_topics;
DROP TABLE IF EXISTS user_interest_tags;
DROP TABLE IF EXISTS interest_tags;

-- 审计事件 2 张
DROP TABLE IF EXISTS activity_lifecycle_events;
DROP TABLE IF EXISTS post_status_events;

-- post idempotency tombstone（Java 里的 ActivityCreateIdempotencyTombstoneEntity 用到）
DROP TABLE IF EXISTS activity_create_idempotency_tombstones;

-- ----------------------------------------------------------------------------
-- STEP 2: ALTER existing tables
-- ----------------------------------------------------------------------------

-- 2a) user_follows → 双向好友 + 备注
--     原结构：id, follower_user_id, followed_user_id, followed_at, created_at
ALTER TABLE user_follows
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0 AFTER followed_at,
    ADD COLUMN accepted_at DATETIME(3) NULL AFTER status,
    ADD COLUMN rejected_at DATETIME(3) NULL AFTER accepted_at,
    ADD COLUMN remark_name VARCHAR(64) NULL AFTER rejected_at,
    ADD CONSTRAINT chk_follow_status CHECK(status IN (0, 1, 2)),
    ADD CONSTRAINT chk_follow_state CHECK(
        (status = 0 AND accepted_at IS NULL AND rejected_at IS NULL)
        OR (status = 1 AND accepted_at IS NOT NULL AND rejected_at IS NULL)
        OR (status = 2 AND rejected_at IS NOT NULL AND accepted_at IS NULL)
    );

-- 2b) activity_comments → 留言板（去掉嵌套回复）
--     原结构有 parent_comment_id FK，改成平铺 + 加语音/图片
ALTER TABLE activity_comments
    DROP FOREIGN KEY fk_activity_comment_parent;
ALTER TABLE activity_comments
    DROP COLUMN parent_comment_id;

ALTER TABLE activity_comments
    ADD COLUMN message_type TINYINT NOT NULL DEFAULT 1 CHECK(message_type IN (1, 2, 3)) AFTER content,
    ADD COLUMN voice_file_id BIGINT NULL AFTER message_type,
    ADD COLUMN voice_duration_seconds INT NULL AFTER voice_file_id,
    ADD COLUMN image_file_id BIGINT NULL AFTER voice_duration_seconds,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER deleted_at,
    ADD CONSTRAINT fk_comment_voice_file FOREIGN KEY(voice_file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_comment_image_file FOREIGN KEY(image_file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_comment_content_mode CHECK(
        (message_type = 1 AND content IS NOT NULL AND voice_file_id IS NULL AND image_file_id IS NULL)
        OR (message_type = 2 AND content IS NULL AND voice_file_id IS NULL AND image_file_id IS NOT NULL)
        OR (message_type = 3 AND content IS NULL AND voice_file_id IS NOT NULL AND image_file_id IS NULL)
    ),
    ADD CONSTRAINT chk_comment_voice_duration CHECK(
        voice_duration_seconds IS NULL OR voice_duration_seconds BETWEEN 1 AND 60
    ),
    DROP CONSTRAINT chk_activity_comment_state,
    ADD CONSTRAINT chk_comment_state CHECK(
        (status = 1 AND content IS NOT NULL AND deleted_at IS NULL)
        OR (status = 2 AND content IS NULL AND deleted_at IS NOT NULL)
    );

-- 2c) posts → 加冗余计数
ALTER TABLE posts
    ADD COLUMN like_count INT NOT NULL DEFAULT 0 AFTER content,
    ADD COLUMN comment_count INT NOT NULL DEFAULT 0 AFTER like_count,
    ADD CONSTRAINT chk_post_like_count CHECK(like_count >= 0),
    ADD CONSTRAINT chk_post_comment_count CHECK(comment_count >= 0);

-- ----------------------------------------------------------------------------
-- STEP 3: CREATE new tables
-- ----------------------------------------------------------------------------

-- 3a) user_locations — 定位（1:1 with users）
CREATE TABLE user_locations (
    user_id BIGINT NOT NULL COMMENT '用户编号',
    province_name VARCHAR(64) NULL COMMENT '省名',
    city_name VARCHAR(64) NULL COMMENT '市名',
    district_name VARCHAR(64) NULL COMMENT '区县名',
    street_name VARCHAR(128) NULL COMMENT '街道/镇名',
    latitude DECIMAL(10, 7) NULL COMMENT '纬度(GCJ-02)',
    longitude DECIMAL(10, 7) NULL COMMENT '经度(GCJ-02)',
    location_source TINYINT NOT NULL DEFAULT 0 COMMENT '定位来源: 0手动 1GPS 2IP 3微信',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY(user_id),
    KEY idx_location_region(province_name, city_name, district_name),
    CONSTRAINT fk_location_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_location_source CHECK(location_source IN (0, 1, 2, 3)),
    CONSTRAINT chk_location_lat CHECK(latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT chk_location_lng CHECK(longitude IS NULL OR (longitude BETWEEN -180 AND 180))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户当前定位';

-- 3b) user_blocks — 拉黑（单向）
CREATE TABLE user_blocks (
    id BIGINT NOT NULL COMMENT '拉黑编号',
    blocker_user_id BIGINT NOT NULL COMMENT '发起拉黑的用户',
    blocked_user_id BIGINT NOT NULL COMMENT '被拉黑的用户',
    blocked_at DATETIME(3) NOT NULL COMMENT '拉黑时间',
    reason VARCHAR(255) NULL COMMENT '拉黑原因',
    PRIMARY KEY(id),
    UNIQUE KEY uk_block_pair(blocker_user_id, blocked_user_id),
    KEY idx_blocked_user(blocked_user_id, blocked_at),
    CONSTRAINT fk_blocker FOREIGN KEY(blocker_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_blocked FOREIGN KEY(blocked_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_block_not_self CHECK(blocker_user_id <> blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户拉黑关系';

-- 3c) sensitive_words — 敏感词库（正则模式）
CREATE TABLE sensitive_words (
    id BIGINT NOT NULL COMMENT '敏感词编号',
    category_code VARCHAR(32) NOT NULL COMMENT '分类: politics/porn/violence/insult/spam/custom',
    word_pattern VARCHAR(512) NOT NULL COMMENT '正则模式(PCRE)',
    severity TINYINT NOT NULL COMMENT '严重度: 1替换 2拦截 3标记',
    replacement_text VARCHAR(64) NULL COMMENT 'severity=1 时的替换文本',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1启用 2停用',
    hit_count INT NOT NULL DEFAULT 0 COMMENT '历史命中次数',
    version INT NOT NULL DEFAULT 0 COMMENT '版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY(id),
    KEY idx_sensitive_category_status(category_code, status),
    KEY idx_sensitive_status_severity(status, severity),
    CONSTRAINT chk_sensitive_category CHECK(
        category_code IN ('politics', 'porn', 'violence', 'insult', 'spam', 'custom')
    ),
    CONSTRAINT chk_sensitive_severity CHECK(severity IN (1, 2, 3)),
    CONSTRAINT chk_sensitive_status CHECK(status IN (1, 2)),
    CONSTRAINT chk_sensitive_hit CHECK(hit_count >= 0),
    CONSTRAINT chk_sensitive_version CHECK(version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词正则库';

-- 3d) chat_conversations — 1:1 聊天会话
CREATE TABLE chat_conversations (
    id BIGINT NOT NULL COMMENT '会话编号',
    user_a_id BIGINT NOT NULL COMMENT '会话用户A(较小ID)',
    user_b_id BIGINT NOT NULL COMMENT '会话用户B(较大ID)',
    last_message_preview VARCHAR(200) NULL COMMENT '最后消息摘要',
    last_message_type TINYINT NULL COMMENT '最后消息类型: 1文本 2图片 3语音',
    last_message_at DATETIME(3) NULL COMMENT '最后消息时间',
    user_a_unread_count INT NOT NULL DEFAULT 0 COMMENT 'A未读数',
    user_b_unread_count INT NOT NULL DEFAULT 0 COMMENT 'B未读数',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_conversation_pair(user_a_id, user_b_id),
    KEY idx_conv_a(user_a_id, updated_at),
    KEY idx_conv_b(user_b_id, updated_at),
    CONSTRAINT fk_conv_a FOREIGN KEY(user_a_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_b FOREIGN KEY(user_b_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_conv_order CHECK(user_a_id < user_b_id),
    CONSTRAINT chk_conv_unread_a CHECK(user_a_unread_count >= 0),
    CONSTRAINT chk_conv_unread_b CHECK(user_b_unread_count >= 0),
    CONSTRAINT chk_conv_last_message_type CHECK(
        last_message_type IS NULL OR last_message_type IN (1, 2, 3)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='1:1 聊天会话';

-- 3e) chat_messages — 聊天消息
CREATE TABLE chat_messages (
    id BIGINT NOT NULL COMMENT '消息编号',
    conversation_id BIGINT NOT NULL COMMENT '会话编号',
    sender_user_id BIGINT NOT NULL COMMENT '发送者编号',
    content VARCHAR(2000) NULL COMMENT '文本内容',
    message_type TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型: 1文本 2图片 3语音',
    file_id BIGINT NULL COMMENT '图片文件编号(type=2)',
    voice_file_id BIGINT NULL COMMENT '语音文件编号(type=3)',
    voice_duration_seconds INT NULL COMMENT '语音时长(秒)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '消息状态: 1已发送 2已读',
    sent_at DATETIME(3) NOT NULL COMMENT '发送时间',
    PRIMARY KEY(id),
    KEY idx_msg_conv_sent(conversation_id, sent_at, id),
    KEY idx_msg_sender(sender_user_id, sent_at),
    CONSTRAINT fk_msg_conv FOREIGN KEY(conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY(sender_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_file FOREIGN KEY(file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_msg_voice_file FOREIGN KEY(voice_file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    CONSTRAINT chk_msg_type CHECK(message_type IN (1, 2, 3)),
    CONSTRAINT chk_msg_status CHECK(status IN (1, 2)),
    CONSTRAINT chk_msg_content_mode CHECK(
        (message_type = 1 AND content IS NOT NULL AND file_id IS NULL AND voice_file_id IS NULL)
        OR (message_type = 2 AND content IS NULL AND file_id IS NOT NULL AND voice_file_id IS NULL)
        OR (message_type = 3 AND content IS NULL AND file_id IS NULL AND voice_file_id IS NOT NULL)
    ),
    CONSTRAINT chk_msg_voice_duration CHECK(
        voice_duration_seconds IS NULL OR voice_duration_seconds BETWEEN 1 AND 60
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息';

-- 3f) post_likes — 帖子点赞
CREATE TABLE post_likes (
    id BIGINT NOT NULL COMMENT '点赞编号',
    user_id BIGINT NOT NULL COMMENT '点赞用户',
    post_id BIGINT NOT NULL COMMENT '被点赞帖子',
    liked_at DATETIME(3) NOT NULL COMMENT '点赞时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_post_like_pair(user_id, post_id),
    KEY idx_like_post(post_id, liked_at),
    CONSTRAINT fk_like_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_like_post FOREIGN KEY(post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子点赞';

-- ----------------------------------------------------------------------------
-- STEP 4: Seed sensitive word defaults
-- ----------------------------------------------------------------------------
INSERT INTO sensitive_words (id, category_code, word_pattern, severity, replacement_text, status, version, created_at, updated_at)
VALUES
    (1, 'insult', '[傻b]|傻逼|操你', 1, '***', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, 'spam', '加微信|代.?理|私聊我|V信', 1, '***', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (3, 'porn', '[黄.色]|约.?炮|裸.?聊', 2, NULL, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);
