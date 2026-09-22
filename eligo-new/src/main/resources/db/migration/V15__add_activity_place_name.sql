ALTER TABLE activities
    ADD COLUMN place_name VARCHAR(100) NULL COMMENT '集合地点名称' AFTER region_code;
