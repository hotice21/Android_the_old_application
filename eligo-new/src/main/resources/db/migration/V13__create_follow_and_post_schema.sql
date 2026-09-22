CREATE TABLE user_follows (
    id BIGINT NOT NULL COMMENT '关注关系编号',
    follower_user_id BIGINT NOT NULL COMMENT '发起关注的用户编号',
    followed_user_id BIGINT NOT NULL COMMENT '被关注的用户编号',
    followed_at DATETIME(3) NOT NULL COMMENT '最近建立关注时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_user_follow_pair(follower_user_id, followed_user_id),
    KEY idx_user_following_page(follower_user_id, followed_at, id),
    KEY idx_user_follower_page(followed_user_id, followed_at, id),
    CONSTRAINT fk_user_follow_follower
        FOREIGN KEY(follower_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_follow_target
        FOREIGN KEY(followed_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_follow_not_self
        CHECK(follower_user_id <> followed_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系';

CREATE TABLE organization_follows (
    id BIGINT NOT NULL COMMENT '关注关系编号',
    follower_user_id BIGINT NOT NULL COMMENT '发起关注的用户编号',
    organization_id BIGINT NOT NULL COMMENT '被关注的企业编号',
    followed_at DATETIME(3) NOT NULL COMMENT '最近建立关注时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_organization_follow_pair(follower_user_id, organization_id),
    KEY idx_organization_following_page(follower_user_id, followed_at, id),
    KEY idx_organization_follower_page(organization_id, followed_at, id),
    CONSTRAINT fk_organization_follow_follower
        FOREIGN KEY(follower_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_organization_follow_target
        FOREIGN KEY(organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业关注关系';

CREATE TABLE posts (
    id BIGINT NOT NULL COMMENT '动态编号',
    author_user_id BIGINT NULL COMMENT '个人发布主体用户编号',
    author_organization_id BIGINT NULL COMMENT '企业发布主体编号',
    operator_user_id BIGINT NOT NULL COMMENT '创建草稿的实际用户编号',
    status TINYINT NOT NULL COMMENT '状态：1草稿，2已发布，3已删除，4平台隐藏',
    visibility TINYINT NOT NULL COMMENT '可见范围：1公开，2仅关注者，3私密',
    title VARCHAR(20) NULL COMMENT '动态标题',
    content TEXT NULL COMMENT '动态正文',
    activity_id BIGINT NULL COMMENT '可选活动引用',
    create_idempotency_scope VARCHAR(160) NULL COMMENT '创建幂等范围',
    create_idempotency_key VARCHAR(128) NULL COMMENT '创建幂等键',
    create_request_fingerprint CHAR(64) NULL COMMENT '规范化创建请求摘要',
    create_idempotency_expires_at DATETIME(3) NULL COMMENT '创建幂等窗口到期时间',
    published_at DATETIME(3) NULL COMMENT '首次发布时间',
    hidden_at DATETIME(3) NULL COMMENT '最近平台隐藏时间',
    deleted_at DATETIME(3) NULL COMMENT '软删除时间',
    version INT NOT NULL COMMENT '乐观锁版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_post_create_idempotency(
        create_idempotency_scope,
        create_idempotency_key
    ),
    KEY idx_post_idempotency_expire(create_idempotency_expires_at, id),
    KEY idx_post_public_page(status, visibility, published_at, id),
    KEY idx_post_user_public_page(author_user_id, status, published_at, id),
    KEY idx_post_organization_public_page(
        author_organization_id,
        status,
        published_at,
        id
    ),
    KEY idx_post_user_managed_page(author_user_id, status, updated_at, id),
    KEY idx_post_organization_managed_page(
        author_organization_id,
        status,
        updated_at,
        id
    ),
    KEY idx_post_activity_page(activity_id, status, published_at, id),
    CONSTRAINT fk_post_author_user
        FOREIGN KEY(author_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_author_organization
        FOREIGN KEY(author_organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_operator_user
        FOREIGN KEY(operator_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_activity
        FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT chk_post_single_author CHECK(
        (author_user_id IS NOT NULL AND author_organization_id IS NULL)
        OR
        (author_user_id IS NULL AND author_organization_id IS NOT NULL)
    ),
    CONSTRAINT chk_post_personal_operator CHECK(
        author_user_id IS NULL OR author_user_id=operator_user_id
    ),
    CONSTRAINT chk_post_status CHECK(status IN(1, 2, 3, 4)),
    CONSTRAINT chk_post_visibility CHECK(visibility IN(1, 2, 3)),
    CONSTRAINT chk_post_title CHECK(
        title IS NULL OR CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 20
    ),
    CONSTRAINT chk_post_content CHECK(
        content IS NULL OR CHAR_LENGTH(content) <= 5000
    ),
    CONSTRAINT chk_post_idempotency_group CHECK(
        (
            create_idempotency_scope IS NULL
            AND create_idempotency_key IS NULL
            AND create_request_fingerprint IS NULL
            AND create_idempotency_expires_at IS NULL
        )
        OR
        (
            create_idempotency_scope IS NOT NULL
            AND create_idempotency_key IS NOT NULL
            AND create_request_fingerprint IS NOT NULL
            AND create_idempotency_expires_at IS NOT NULL
        )
    ),
    CONSTRAINT chk_post_state_times CHECK(
        (
            status=1
            AND published_at IS NULL
            AND hidden_at IS NULL
            AND deleted_at IS NULL
        )
        OR
        (
            status=2
            AND published_at IS NOT NULL
            AND hidden_at IS NULL
            AND deleted_at IS NULL
        )
        OR
        (
            status=3
            AND deleted_at IS NOT NULL
            AND (hidden_at IS NULL OR published_at IS NOT NULL)
        )
        OR
        (
            status=4
            AND published_at IS NOT NULL
            AND hidden_at IS NOT NULL
            AND deleted_at IS NULL
        )
    ),
    CONSTRAINT chk_post_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人与企业动态';

CREATE TABLE post_media (
    id BIGINT NOT NULL COMMENT '动态媒体关系编号',
    post_id BIGINT NOT NULL COMMENT '动态编号',
    file_id BIGINT NOT NULL COMMENT 'POST 用途图片编号',
    sort_order TINYINT NOT NULL COMMENT '展示顺序：0至4',
    created_at DATETIME(3) NOT NULL COMMENT '绑定时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_post_media_sort(post_id, sort_order),
    UNIQUE KEY uk_post_media_file(file_id),
    KEY idx_post_media_post(post_id, id),
    CONSTRAINT fk_post_media_post
        FOREIGN KEY(post_id) REFERENCES posts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_media_file
        FOREIGN KEY(file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    CONSTRAINT chk_post_media_order CHECK(sort_order BETWEEN 0 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态图片顺序与文件绑定';

CREATE TABLE post_status_events (
    id BIGINT NOT NULL COMMENT '动态状态事件编号',
    post_id BIGINT NOT NULL COMMENT '动态编号',
    from_status TINYINT NOT NULL COMMENT '变更前状态',
    to_status TINYINT NOT NULL COMMENT '目标状态',
    actor_type TINYINT NOT NULL COMMENT '执行者类型：1用户，2系统',
    actor_user_id BIGINT NULL COMMENT '用户执行者编号',
    reason_code VARCHAR(64) NULL COMMENT '平台处置或系统原因',
    created_at DATETIME(3) NOT NULL COMMENT '事件时间',
    PRIMARY KEY(id),
    KEY idx_post_status_event(post_id, created_at, id),
    CONSTRAINT fk_post_status_event_post
        FOREIGN KEY(post_id) REFERENCES posts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_status_event_actor
        FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_post_event_from_status CHECK(from_status IN(1, 2, 3, 4)),
    CONSTRAINT chk_post_event_to_status CHECK(to_status IN(1, 2, 3, 4)),
    CONSTRAINT chk_post_event_transition CHECK(
        (from_status=1 AND to_status IN(2, 3))
        OR (from_status=2 AND to_status IN(3, 4))
        OR (from_status=4 AND to_status IN(2, 3))
    ),
    CONSTRAINT chk_post_event_actor_type CHECK(actor_type IN(1, 2)),
    CONSTRAINT chk_post_event_actor CHECK(
        (actor_type=1 AND actor_user_id IS NOT NULL)
        OR
        (actor_type=2 AND actor_user_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态状态审计事件';
