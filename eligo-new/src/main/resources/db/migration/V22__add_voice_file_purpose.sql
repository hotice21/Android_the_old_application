-- V22: 文件用途增加 VOICE（语音聊天消息用）
-- 原约束: purpose IN ('AVATAR','ACTIVITY','ACTIVITY_CONTACT_QR','POST')
ALTER TABLE file_objects DROP CHECK chk_file_purpose;
ALTER TABLE file_objects ADD CONSTRAINT chk_file_purpose
    CHECK (purpose IN ('AVATAR','ACTIVITY','ACTIVITY_CONTACT_QR','POST','VOICE'));
