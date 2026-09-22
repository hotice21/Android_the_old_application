ALTER TABLE activities
    ADD COLUMN latitude DECIMAL(9, 7) NULL COMMENT '集合地点纬度' AFTER address_detail,
    ADD COLUMN longitude DECIMAL(10, 7) NULL COMMENT '集合地点经度' AFTER latitude,
    ADD CONSTRAINT chk_activity_coordinate_pair CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    ADD CONSTRAINT chk_activity_latitude CHECK (
        latitude IS NULL OR latitude BETWEEN -90 AND 90
    ),
    ADD CONSTRAINT chk_activity_longitude CHECK (
        longitude IS NULL OR longitude BETWEEN -180 AND 180
    ),
    ADD KEY idx_activity_public_map (
        status, latitude, longitude, starts_at, id
    );
