ALTER TABLE user_profiles
    ADD COLUMN email_ciphertext VARBINARY(1024) NULL COMMENT '加密邮箱' AFTER birth_date_ciphertext,
    ADD COLUMN email_lookup_hash BINARY(32) NULL COMMENT '邮箱查找摘要' AFTER email_ciphertext,
    ADD UNIQUE KEY uk_profile_email_lookup_hash(email_lookup_hash),
    ADD CONSTRAINT chk_profile_email_pair CHECK(
        (email_ciphertext IS NULL AND email_lookup_hash IS NULL)
        OR
        (email_ciphertext IS NOT NULL AND email_lookup_hash IS NOT NULL)
    );

CREATE TABLE organizations (
    id BIGINT NOT NULL COMMENT '企业编号',
    name VARCHAR(256) NOT NULL COMMENT '企业名称',
    avatar_file_id BIGINT NULL COMMENT '企业头像文件编号',
    summary VARCHAR(1000) NULL COMMENT '企业简介',
    province_code VARCHAR(32) NOT NULL COMMENT '省编码',
    province_name VARCHAR(64) NOT NULL COMMENT '省名',
    city_code VARCHAR(32) NOT NULL COMMENT '市编码',
    city_name VARCHAR(64) NOT NULL COMMENT '市名',
    district_code VARCHAR(32) NOT NULL COMMENT '区县编码',
    district_name VARCHAR(64) NOT NULL COMMENT '区县名',
    address_detail VARCHAR(512) NOT NULL COMMENT '详细地址',
    contact_phone_ciphertext VARBINARY(1024) NOT NULL COMMENT '加密联系电话',
    contact_phone_lookup_hash BINARY(32) NOT NULL COMMENT '联系电话查找摘要',
    contact_phone_last_four CHAR(4) NOT NULL COMMENT '联系电话后四位',
    status TINYINT NOT NULL COMMENT '状态：1有效，2停用',
    version INT NOT NULL COMMENT '版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY(id),
    KEY idx_organization_status_created(status, created_at, id),
    KEY idx_organization_region(
        status,
        province_code,
        city_code,
        district_code,
        id
    ),
    CONSTRAINT fk_organization_avatar
        FOREIGN KEY(avatar_file_id) REFERENCES file_objects(id) ON DELETE RESTRICT,
    CONSTRAINT chk_organization_name
        CHECK(CHAR_LENGTH(TRIM(name)) BETWEEN 2 AND 256),
    CONSTRAINT chk_organization_status CHECK(status IN(1, 2)),
    CONSTRAINT chk_organization_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业主体';

CREATE TABLE organization_members (
    id BIGINT NOT NULL COMMENT '企业成员关系编号',
    organization_id BIGINT NOT NULL COMMENT '企业编号',
    user_id BIGINT NOT NULL COMMENT '用户编号',
    role_code TINYINT NOT NULL COMMENT '角色：1负责人',
    status TINYINT NOT NULL COMMENT '状态：1开发启用，2开发停用',
    enabled_at DATETIME(3) NOT NULL COMMENT '启用时间',
    disabled_at DATETIME(3) NULL COMMENT '停用时间',
    version INT NOT NULL COMMENT '版本',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    active_owner TINYINT GENERATED ALWAYS AS(
        IF(role_code=1 AND status=1, 1, NULL)
    ) STORED COMMENT '有效负责人唯一标记',
    active_user TINYINT GENERATED ALWAYS AS(
        IF(role_code=1 AND status=1, 1, NULL)
    ) STORED COMMENT '有效用户唯一标记',
    PRIMARY KEY(id),
    UNIQUE KEY uk_organization_active_owner(organization_id, active_owner),
    UNIQUE KEY uk_organization_member_active_user(user_id, active_user),
    KEY idx_organization_member_org_status(
        organization_id,
        status,
        user_id,
        id
    ),
    KEY idx_organization_member_user_status(
        user_id,
        status,
        organization_id,
        id
    ),
    CONSTRAINT fk_organization_member_organization
        FOREIGN KEY(organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_organization_member_user
        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_organization_member_role CHECK(role_code=1),
    CONSTRAINT chk_organization_member_status CHECK(status IN(1, 2)),
    CONSTRAINT chk_organization_member_disabled CHECK(
        (status=1 AND disabled_at IS NULL)
        OR
        (status=2 AND disabled_at IS NOT NULL)
    ),
    CONSTRAINT chk_organization_member_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业账号负责人关系';

INSERT INTO interest_tags (
    id, tag_code, tag_name, sort_order, status, created_at, updated_at
) VALUES
    (1900000000000001004, 'TRAVEL', '旅行', 40, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1900000000000001005, 'MUSIC', '音乐', 50, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1900000000000001006, 'MOVIE', '电影', 60, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1900000000000001007, 'READING', '阅读', 70, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1900000000000001008, 'PHOTOGRAPHY', '摄影', 80, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1900000000000001009, 'GAMING', '游戏', 90, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (1900000000000001010, 'PETS', '宠物', 100, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
