CREATE TEMPORARY TABLE activity_title_v9_guard (
    invalid_count BIGINT NOT NULL,
    CONSTRAINT chk_activity_title_v9_guard CHECK(invalid_count=0)
);

INSERT INTO activity_title_v9_guard(invalid_count)
SELECT COUNT(*)
FROM activities
WHERE CHAR_LENGTH(title)>20;

DROP TEMPORARY TABLE activity_title_v9_guard;

ALTER TABLE activities
    DROP CHECK chk_activity_title,
    MODIFY title VARCHAR(20) NOT NULL COMMENT '活动标题',
    ADD CONSTRAINT chk_activity_title CHECK(
        CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 20
    ),
    ADD KEY idx_activity_public_region_start(status, region_code, starts_at, id),
    ADD KEY idx_activity_owner_user_updated(owner_user_id, updated_at, id),
    ADD KEY idx_activity_owner_organization_updated(
        owner_organization_id, updated_at, id
    );
