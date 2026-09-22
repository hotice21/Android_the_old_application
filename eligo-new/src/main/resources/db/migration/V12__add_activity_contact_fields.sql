ALTER TABLE file_objects
    MODIFY COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'AVATAR'
        COMMENT '图片用途',
    DROP CHECK chk_file_purpose,
    ADD CONSTRAINT chk_file_purpose CHECK(
        purpose IN('AVATAR', 'ACTIVITY', 'ACTIVITY_CONTACT_QR', 'POST')
    );

ALTER TABLE activities
    ADD COLUMN registration_gender TINYINT NOT NULL DEFAULT 1
        COMMENT '报名性别限制：1不限，2男，3女'
        AFTER participant_count,
    ADD COLUMN organizer_phone_ciphertext VARBINARY(1024) NULL
        COMMENT '加密组织人手机号'
        AFTER registration_gender,
    ADD COLUMN organizer_wechat_ciphertext VARBINARY(1024) NULL
        COMMENT '加密组织人微信号'
        AFTER organizer_phone_ciphertext,
    ADD COLUMN organizer_wechat_qr_file_id BIGINT NULL
        COMMENT '组织人微信二维码文件编号'
        AFTER organizer_wechat_ciphertext,
    ADD KEY idx_activity_contact_qr_file(organizer_wechat_qr_file_id),
    ADD CONSTRAINT fk_activity_contact_qr_file
        FOREIGN KEY(organizer_wechat_qr_file_id) REFERENCES file_objects(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT chk_activity_registration_gender CHECK(
        registration_gender IN(1, 2, 3)
    );

ALTER TABLE file_objects
    MODIFY COLUMN access_level TINYINT NOT NULL COMMENT '访问级别：1公开，2私有';
