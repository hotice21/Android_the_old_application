CREATE TABLE activities (
    id BIGINT NOT NULL COMMENT '活动编号',
    owner_user_id BIGINT NULL COMMENT '个人发起者编号',
    owner_organization_id BIGINT NULL COMMENT '企业发起者编号',
    operator_user_id BIGINT NOT NULL COMMENT '实际操作用户编号',
    status TINYINT NOT NULL COMMENT '状态：1草稿，2已发布，3已取消，4已结束，5已隐藏',
    title VARCHAR(100) NOT NULL COMMENT '活动标题',
    category_code VARCHAR(32) NULL COMMENT '活动分类代码',
    description TEXT NULL COMMENT '活动介绍',
    cover_file_id BIGINT NULL COMMENT '封面文件编号',
    registration_starts_at DATETIME(3) NULL COMMENT '报名开始时间',
    registration_ends_at DATETIME(3) NULL COMMENT '报名截止时间',
    starts_at DATETIME(3) NULL COMMENT '活动开始时间',
    ends_at DATETIME(3) NULL COMMENT '活动结束时间',
    region_code VARCHAR(32) NULL COMMENT '地区编码',
    address_detail VARCHAR(512) NULL COMMENT '详细地址',
    capacity INT NULL COMMENT '人数上限',
    participant_count INT NOT NULL DEFAULT 0 COMMENT '当前有效报名数',
    signup_details TEXT NULL COMMENT '个人活动报名说明',
    organizer_message TEXT NULL COMMENT '个人发起者补充说明',
    published_at DATETIME(3) NULL COMMENT '首次发布时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY(id),
    KEY idx_activity_public_start(status, starts_at, id),
    KEY idx_activity_public_category_start(status, category_code, starts_at, id),
    KEY idx_activity_owner_user_created(owner_user_id, created_at, id),
    KEY idx_activity_owner_organization_created(owner_organization_id, created_at, id),
    KEY idx_activity_operator(operator_user_id, id),
    KEY idx_activity_cover_file(cover_file_id, id),
    CONSTRAINT fk_activity_owner_user
        FOREIGN KEY(owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_owner_organization
        FOREIGN KEY(owner_organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_operator_user
        FOREIGN KEY(operator_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_cover_file
        FOREIGN KEY(cover_file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    CONSTRAINT chk_activity_owner CHECK(
        (owner_user_id IS NOT NULL AND owner_organization_id IS NULL)
        OR
        (owner_user_id IS NULL AND owner_organization_id IS NOT NULL)
    ),
    CONSTRAINT chk_activity_personal_operator CHECK(
        owner_user_id IS NULL OR owner_user_id=operator_user_id
    ),
    CONSTRAINT chk_activity_status CHECK(status IN(1, 2, 3, 4, 5)),
    CONSTRAINT chk_activity_title CHECK(
        CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 100
    ),
    CONSTRAINT chk_activity_category CHECK(
        category_code IS NULL
        OR CHAR_LENGTH(TRIM(category_code)) BETWEEN 1 AND 32
    ),
    CONSTRAINT chk_activity_description CHECK(
        description IS NULL
        OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 5000
    ),
    CONSTRAINT chk_activity_region CHECK(
        region_code IS NULL
        OR CHAR_LENGTH(TRIM(region_code)) BETWEEN 1 AND 32
    ),
    CONSTRAINT chk_activity_address CHECK(
        address_detail IS NULL
        OR CHAR_LENGTH(TRIM(address_detail)) BETWEEN 1 AND 512
    ),
    CONSTRAINT chk_activity_signup_details CHECK(
        signup_details IS NULL
        OR CHAR_LENGTH(TRIM(signup_details)) BETWEEN 1 AND 2000
    ),
    CONSTRAINT chk_activity_organizer_message CHECK(
        organizer_message IS NULL
        OR CHAR_LENGTH(TRIM(organizer_message)) BETWEEN 1 AND 1000
    ),
    CONSTRAINT chk_activity_organization_fields CHECK(
        owner_organization_id IS NULL
        OR (signup_details IS NULL AND organizer_message IS NULL)
    ),
    CONSTRAINT chk_activity_time_order CHECK(
        (registration_starts_at IS NULL OR registration_ends_at IS NULL
            OR registration_starts_at<registration_ends_at)
        AND
        (registration_ends_at IS NULL OR starts_at IS NULL
            OR registration_ends_at<=starts_at)
        AND
        (starts_at IS NULL OR ends_at IS NULL OR starts_at<ends_at)
    ),
    CONSTRAINT chk_activity_capacity CHECK(capacity IS NULL OR capacity>0),
    CONSTRAINT chk_activity_participant_count CHECK(
        participant_count>=0
        AND (capacity IS NULL OR participant_count<=capacity)
    ),
    CONSTRAINT chk_activity_published_at CHECK(
        (status=1 AND published_at IS NULL)
        OR
        (status IN(2, 3, 4, 5) AND published_at IS NOT NULL)
    ),
    CONSTRAINT chk_activity_publish_ready CHECK(
        status=1
        OR (
            category_code IS NOT NULL
            AND description IS NOT NULL
            AND cover_file_id IS NOT NULL
            AND registration_starts_at IS NOT NULL
            AND registration_ends_at IS NOT NULL
            AND starts_at IS NOT NULL
            AND ends_at IS NOT NULL
            AND region_code IS NOT NULL
            AND address_detail IS NOT NULL
            AND capacity IS NOT NULL
        )
    ),
    CONSTRAINT chk_activity_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人和企业活动主体';

CREATE TABLE activity_media (
    id BIGINT NOT NULL COMMENT '活动媒体关系编号',
    activity_id BIGINT NOT NULL COMMENT '活动编号',
    file_id BIGINT NOT NULL COMMENT '文件编号',
    sort_order TINYINT NOT NULL COMMENT '附加图片顺序',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_activity_media_file(activity_id, file_id),
    UNIQUE KEY uk_activity_media_sort(activity_id, sort_order),
    KEY idx_activity_media_file(file_id, activity_id),
    CONSTRAINT fk_activity_media_activity
        FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_media_file
        FOREIGN KEY(file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    CONSTRAINT chk_activity_media_sort CHECK(sort_order BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动附加轮播图片';

CREATE TABLE activity_lifecycle_events (
    id BIGINT NOT NULL COMMENT '活动生命周期事件编号',
    activity_id BIGINT NOT NULL COMMENT '活动编号',
    from_status TINYINT NULL COMMENT '原状态',
    to_status TINYINT NOT NULL COMMENT '目标状态',
    operator_user_id BIGINT NULL COMMENT '操作用户编号',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY(id),
    KEY idx_activity_event_activity_created(activity_id, created_at, id),
    KEY idx_activity_event_operator(operator_user_id, created_at, id),
    CONSTRAINT fk_activity_event_activity
        FOREIGN KEY(activity_id) REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_event_operator
        FOREIGN KEY(operator_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_activity_event_from CHECK(
        from_status IS NULL OR from_status IN(1, 2, 3, 4, 5)
    ),
    CONSTRAINT chk_activity_event_to CHECK(to_status IN(1, 2, 3, 4, 5)),
    CONSTRAINT chk_activity_event_change CHECK(
        from_status IS NULL OR from_status<>to_status
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动状态转换历史';
