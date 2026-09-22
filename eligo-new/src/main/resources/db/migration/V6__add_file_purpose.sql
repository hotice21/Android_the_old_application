ALTER TABLE file_objects
    ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'AVATAR' COMMENT '图片用途'
        AFTER uploader_id,
    ADD KEY idx_file_purpose_lifecycle(purpose, lifecycle_status, expires_at, id),
    ADD CONSTRAINT chk_file_purpose CHECK(purpose IN('AVATAR', 'ACTIVITY', 'POST'));
