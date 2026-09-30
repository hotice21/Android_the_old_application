# Eligo 种子数据导入脚本
# 在 PowerShell 中直接执行（docker-compose 三件套启动后）

$mysql = "docker exec eligo-mysql mysql -uroot -proot eligo"

# 1. 用户（确保存在）
Invoke-Expression "$mysql -e \"INSERT IGNORE INTO users (id, status, version, created_at, updated_at) VALUES (1, 1, 0, NOW(), NOW());\"" | Out-Null

# 2. 用户资料
Invoke-Expression "$mysql -e \"INSERT INTO user_profiles (user_id, nickname, bio, gender_code, province_name, city_name, version, created_at, updated_at) VALUES (1, '港漂小明', '周末一起出去玩！喜欢徒步咖啡和摄影', 1, '广东省', '深圳市', 0, NOW(), NOW()) ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), bio=VALUES(bio), updated_at=NOW();\"" | Out-Null

# 3. 组织
Invoke-Expression "$mysql -e \"INSERT IGNORE INTO organizations (id, name, summary, province_code, province_name, city_code, city_name, district_code, district_name, address_detail, contact_phone_ciphertext, contact_phone_lookup_hash, contact_phone_last_four, status, version, created_at, updated_at) VALUES (1, '深港户外俱乐部', '连接香港与深圳的户外爱好者', '440000', '广东省', '440300', '深圳市', '440305', '南山区', '科技园南区T3栋1201', UNHEX(REPEAT('00', 32)), UNHEX(REPEAT('00', 32)), '8888', 1, 0, NOW(), NOW()), (2, '南山周末咖啡社', '精品咖啡爱好者的周末聚会', '440000', '广东省', '440300', '深圳市', '440305', '南山区', '华侨城创意园A5栋', UNHEX(REPEAT('00', 32)), UNHEX(REPEAT('00', 32)), '6666', 1, 0, NOW(), NOW());\"" | Out-Null

# 4. 兴趣标签
Invoke-Expression "$mysql -e \"INSERT IGNORE INTO interest_tags (id, tag_code, tag_name, sort_order, status, created_at, updated_at) VALUES (1, 'hiking', '徒步', 1, 1, NOW(), NOW()), (2, 'camping', '露营', 2, 1, NOW(), NOW()), (3, 'coffee', '咖啡', 3, 1, NOW(), NOW()), (4, 'photography', '摄影', 4, 1, NOW(), NOW()), (5, 'cycling', '骑行', 5, 1, NOW(), NOW()), (6, 'foodie', '美食', 6, 1, NOW(), NOW()), (7, 'boardgame', '桌游', 7, 1, NOW(), NOW()), (8, 'fitness', '健身', 8, 1, NOW(), NOW());\"" | Out-Null

# 5. 假的封面文件
Invoke-Expression "$mysql -e \"INSERT IGNORE INTO file_objects (id, uploader_type, uploader_id, purpose, storage_provider, bucket_name, object_key, original_filename, content_type, file_extension, size_bytes, sha256, access_level, scan_status, lifecycle_status, version, created_at, updated_at) VALUES (1, 1, 1, 'ACTIVITY', 'LOCAL', 'eligo-local', 'static/eligo/photos/baoyuan-eligo.png', 'baoyuan-eligo.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW());\"" | Out-Null

# 6. 活动（title <= 6 汉字，registration_ends_at <= starts_at，starts_at < ends_at）
# 活动1：莲花山
Invoke-Expression "$mysql -e \"INSERT INTO activities (id, owner_user_id, owner_organization_id, operator_user_id, status, title, category_code, description, region_code, place_name, address_detail, latitude, longitude, capacity, organizer_message, cover_file_id, published_at, starts_at, ends_at, registration_starts_at, registration_ends_at, created_at, updated_at) VALUES (1, 1, NULL, 1, 2, '莲花山徒步', 'hiking', '莲花山海拔106米深圳最适合周末徒步的山头之一。山顶俯瞰深圳中心区天际线傍晚还能看日落。难度新手友好集合早上8点30地铁4号线少年宫站A出口。', '440304', '莲花山公园', '深圳市福田区红荔路6032号', 22.5449000, 114.0678000, 20, '自备水和运动鞋', 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 DAY), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 12 HOUR), NOW(), NOW());\"" | Out-Null
Write-Host "活动1 莲花山 插入完成"

# 活动2：大鹏所城
Invoke-Expression "$mysql -e \"INSERT INTO activities (id, owner_user_id, owner_organization_id, operator_user_id, status, title, category_code, description, region_code, place_name, address_detail, latitude, longitude, capacity, organizer_message, cover_file_id, published_at, starts_at, ends_at, registration_starts_at, registration_ends_at, created_at, updated_at) VALUES (2, 1, NULL, 1, 2, '大鹏古城', 'culture', '大鹏所城始建于明朝是华南地区保存最完好的军事堡垒之一。漫步青石板街道感受600年海防历史。行程上午所城导览中午海鲜AA下午较场尾海滩。', '440312', '大鹏所城', '深圳市大鹏新区鹏城社区', 22.5928000, 114.5502000, 15, '一整天带零食防晒', 1, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 2 DAY), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY), NOW(), NOW());\"" | Out-Null
Write-Host "活动2 大鹏古城 插入完成"

# 活动3：海岸城咖啡
Invoke-Expression "$mysql -e \"INSERT INTO activities (id, owner_user_id, owner_organization_id, operator_user_id, status, title, category_code, description, region_code, place_name, address_detail, latitude, longitude, capacity, organizer_message, cover_file_id, published_at, starts_at, ends_at, registration_starts_at, registration_ends_at, created_at, updated_at) VALUES (3, 1, NULL, 1, 2, '海岸城咖啡', 'coffee', '南山海岸城新开SOE单品咖啡豆源埃塞俄比亚耶加雪菲水洗。店主分享几种冲煮方式对比一起品咖啡人均50-80元AA。', '440305', '海岸城购物中心', '深圳市南山区文心五路33号', 22.5333000, 113.9333000, 12, '咖啡AA', 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 DAY), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR), NOW(), NOW());\"" | Out-Null
Write-Host "活动3 海岸城咖啡 插入完成"

# 活动4：深圳湾夜骑
Invoke-Expression "$mysql -e \"INSERT INTO activities (id, owner_user_id, owner_organization_id, operator_user_id, status, title, category_code, description, region_code, place_name, address_detail, latitude, longitude, capacity, organizer_message, cover_file_id, published_at, starts_at, ends_at, registration_starts_at, registration_ends_at, created_at, updated_at) VALUES (4, 1, NULL, 1, 2, '深圳湾夜骑', 'cycling', '深圳湾海滨绿道约15公里沿途可见香港元朗后海天际线和欢乐港湾摩天轮夜景绝佳。路线深圳湾公园A入口欢乐港湾返回里程约20km难度中等。', '440305', '深圳湾公园', '深圳市南山区望海路', 22.5117000, 113.9497000, 25, '自备车灯头盔', 1, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 3 DAY), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), NOW(), NOW());\"" | Out-Null
Write-Host "活动4 深圳湾夜骑 插入完成"

# 活动5：梧桐山
Invoke-Expression "$mysql -e \"INSERT INTO activities (id, owner_user_id, owner_organization_id, operator_user_id, status, title, category_code, description, region_code, place_name, address_detail, latitude, longitude, capacity, organizer_message, cover_file_id, published_at, starts_at, ends_at, registration_starts_at, registration_ends_at, created_at, updated_at) VALUES (5, 1, NULL, 1, 2, '梧桐山登山', 'hiking', '梧桐山深圳第一峰944米泰山涧路线全程约8km爬升800m溯溪而上风景绝佳。装备登山鞋3L水登山杖头灯。', '440308', '梧桐山泰山涧', '深圳市罗湖区泰山涧登山口', 22.6156000, 114.1531000, 18, '体力好8点前出发', 1, NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 4 DAY), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), NOW(), NOW());\"" | Out-Null
Write-Host "活动5 梧桐山登山 插入完成"

# 活动6：桌游之夜
Invoke-Expression "$mysql -e \"INSERT INTO activities (id, owner_user_id, owner_organization_id, operator_user_id, status, title, category_code, description, region_code, place_name, address_detail, latitude, longitude, capacity, organizer_message, cover_file_id, published_at, starts_at, ends_at, registration_starts_at, registration_ends_at, created_at, updated_at) VALUES (6, 1, NULL, 1, 2, '桌游之夜', 'boardgame', '新一期桌游之夜准备了璀璨宝石卡坦岛狼人杀新手老玩家都欢迎。地点欢乐港湾桌游吧约50元含饮品。', '440305', '欢乐港湾', '深圳市南山区海天二路', 22.5126000, 113.9169000, 16, '无需经验', 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 DAY), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_ADD(NOW(), INTERVAL 8 HOUR), NOW(), NOW());\"" | Out-Null
Write-Host "活动6 桌游之夜 插入完成"

# 验证
Write-Host ""
Write-Host "=== 最终验证 ==="
Invoke-Expression "$mysql -e \"SELECT 'users' as t, COUNT(*) FROM users UNION ALL SELECT 'organizations', COUNT(*) FROM organizations UNION ALL SELECT 'activities', COUNT(*) FROM activities UNION ALL SELECT 'interest_tags', COUNT(*) FROM interest_tags UNION ALL SELECT 'file_objects', COUNT(*) FROM file_objects;\""
Invoke-Expression "$mysql -e \"SELECT id, title, status, capacity FROM activities ORDER BY id;\""
