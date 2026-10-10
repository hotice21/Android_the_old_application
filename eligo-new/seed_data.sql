-- ========================================
-- Eligo 测试种子数据 v3 (时间用 HOUR 差避开约束)
-- 5 用户 + 10 活动 + 帖子 + 评论 + 好友 + 聊天 + 点赞 + 收藏 + 报名
-- ========================================

SET FOREIGN_KEY_CHECKS=0;
DELETE FROM chat_messages;
DELETE FROM chat_conversations;
DELETE FROM activity_comments;
DELETE FROM activity_participations;
DELETE FROM activity_favorites;
DELETE FROM user_follows;
DELETE FROM posts;
DELETE FROM post_likes;
DELETE FROM user_blocks;
DELETE FROM sensitive_words;
DELETE FROM user_locations;
DELETE FROM activities;
DELETE FROM file_objects;
DELETE FROM user_profiles;
DELETE FROM users;
SET FOREIGN_KEY_CHECKS=1;

-- 1. 用户
INSERT INTO users (id, status, version, created_at, updated_at) VALUES
(1, 1, 0, NOW(), NOW()),
(2, 1, 0, NOW(), NOW()),
(3, 1, 0, NOW(), NOW()),
(4, 1, 0, NOW(), NOW()),
(5, 1, 0, NOW(), NOW());

-- 2. 用户资料
INSERT INTO user_profiles (user_id, nickname, gender_code, province_name, city_name, district_name, bio, completed_at, version, created_at, updated_at) VALUES
(1, '港漂小明', 1, '广东省', '深圳市', '福田区', '周末一起出去玩！徒步爱好者', NOW(), 0, NOW(), NOW()),
(2, '南山阿杰', 1, '广东省', '深圳市', '南山区', '程序员，爱撸咖啡', NOW(), 0, NOW(), NOW()),
(3, '宝安小丽', 2, '广东省', '深圳市', '宝安区', '瑜伽+摄影，约起来！', NOW(), 0, NOW(), NOW()),
(4, '龙华大壮', 1, '广东省', '深圳市', '龙华区', '篮球场上见！', NOW(), 0, NOW(), NOW()),
(5, '龙岗阿雯', 2, '广东省', '深圳市', '龙岗区', '烘焙达人', NOW(), 0, NOW(), NOW());

-- 3. file_objects
INSERT INTO file_objects (id, uploader_type, uploader_id, purpose, storage_provider, bucket_name, object_key, original_filename, content_type, file_extension, size_bytes, sha256, access_level, scan_status, lifecycle_status, version, created_at, updated_at) VALUES
(1, 1, 1, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/hiking.png', 'hiking.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW()),
(2, 1, 2, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/coffee.png', 'coffee.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW()),
(3, 1, 3, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/yoga.png', 'yoga.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW()),
(4, 1, 4, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/basketball.png', 'basketball.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW()),
(5, 1, 5, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/baking.png', 'baking.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW()),
(6, 1, 1, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/movie.png', 'movie.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW()),
(7, 1, 2, 'ACTIVITY', 'LOCAL', 'eligo-local', 'covers/photo.png', 'photo.png', 'image/png', 'png', 1024, UNHEX(REPEAT('00', 32)), 1, 1, 1, 0, NOW(), NOW());

-- 4. 活动 (时间用 HOUR 差避开约束, title ≤ 6汉字)
INSERT INTO activities (id, owner_user_id, operator_user_id, status, title, category_code, cover_file_id, registration_starts_at, registration_ends_at, starts_at, ends_at, region_code, place_name, address_detail, latitude, longitude, capacity, participant_count, description, signup_details, organizer_message, registration_gender, published_at, version, created_at, updated_at) VALUES
(1, 1, 1, 2, '莲花山徒步', 'hiking', 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440304', '莲花山', '福田区红荔路', 22.54, 114.06, 20, 3, '深圳最适合徒步的山头，登顶可俯瞰中心区。', '穿运动鞋带水', '山顶等大家', 1, NOW(), 0, NOW(), NOW()),
(2, 2, 2, 2, '科技园咖啡', 'coffee', 2, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440305', '瑞幸咖啡', '南山区科技园', 22.54, 113.94, 10, 5, '程序员咖啡小聚，聊聊技术。', 'AA人均40', '到了发微信', 1, NOW(), 0, NOW(), NOW()),
(3, 3, 3, 2, '宝安瑜伽课', 'yoga', 3, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440306', '瑜伽馆', '宝安区创业路', 22.55, 114.08, 12, 2, '新手友好流瑜伽课。', '自备瑜伽垫', NULL, 1, NOW(), 0, NOW(), NOW()),
(4, 4, 4, 2, '周末篮球约', 'basketball', 4, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440309', '篮球场', '龙华区民治', 22.57, 114.03, 16, 8, '5v5全场打完吃饭。', '自带篮球', NULL, 1, NOW(), 0, NOW(), NOW()),
(5, 5, 5, 2, '烘焙入门课', 'baking', 5, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440307', '烘焙室', '龙岗区南湾', 22.54, 114.12, 8, 1, '做戚风蛋糕新手也来。', '围裙我提供', '蛋糕可带走', 1, NOW(), 0, NOW(), NOW()),
(6, 1, 1, 2, '一起看电影', 'movie', 6, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440304', '万达影城', '福田区京基', 22.55, 114.08, 6, 4, '新片IMAX看完聊感想。', '电影票自理', '门口集合', 1, NOW(), 0, NOW(), NOW()),
(7, 2, 2, 2, '街拍深圳湾', 'photography', 7, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440305', '深圳湾', '南山区深圳湾', 22.51, 113.95, 8, 0, '深圳湾晚霞新手友好。', '带相机或手机', NULL, 1, NOW(), 0, NOW(), NOW()),
(8, 3, 3, 2, '美食探店日', 'food', 2, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440306', '渔寿司', '宝安区壹方城', 22.55, 114.08, 6, 3, '新开日料性价比高。', 'AA提前点菜', '门口集合', 1, NOW(), 0, NOW(), NOW()),
(9, 4, 4, 2, '东湖夜跑团', 'running', 1, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440303', '东湖公园', '罗湖区东湖', 22.55, 114.14, 15, 6, '每周二固定夜跑5公里。', '穿跑鞋带水壶', NULL, 1, NOW(), 0, NOW(), NOW()),
(10, 5, 5, 2, '桌游之夜', 'boardgame', 3, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 3 HOUR), '440307', '桌游吧', '龙岗区坂田', 22.56, 114.07, 10, 2, '狼人杀阿瓦隆新手有人带。', '场地费AA', '我订位置', 1, NOW(), 0, NOW(), NOW());

-- 5. 报名
INSERT INTO activity_participations (id, activity_id, user_id, status, joined_at, version, created_at, updated_at) VALUES
(1, 1, 2, 1, NOW(), 0, NOW(), NOW()),
(2, 1, 3, 1, NOW(), 0, NOW(), NOW()),
(3, 1, 4, 1, NOW(), 0, NOW(), NOW()),
(4, 2, 1, 1, NOW(), 0, NOW(), NOW()),
(5, 2, 3, 1, NOW(), 0, NOW(), NOW()),
(6, 2, 4, 1, NOW(), 0, NOW(), NOW()),
(7, 2, 5, 1, NOW(), 0, NOW(), NOW()),
(8, 3, 1, 1, NOW(), 0, NOW(), NOW()),
(9, 4, 1, 1, NOW(), 0, NOW(), NOW()),
(10, 4, 2, 1, NOW(), 0, NOW(), NOW()),
(11, 6, 2, 1, NOW(), 0, NOW(), NOW()),
(12, 6, 3, 1, NOW(), 0, NOW(), NOW()),
(13, 6, 4, 1, NOW(), 0, NOW(), NOW()),
(14, 6, 5, 1, NOW(), 0, NOW(), NOW()),
(15, 8, 1, 1, NOW(), 0, NOW(), NOW()),
(16, 8, 4, 1, NOW(), 0, NOW(), NOW()),
(17, 8, 5, 1, NOW(), 0, NOW(), NOW()),
(18, 9, 1, 1, NOW(), 0, NOW(), NOW()),
(19, 9, 2, 1, NOW(), 0, NOW(), NOW()),
(20, 9, 3, 1, NOW(), 0, NOW(), NOW()),
(21, 9, 5, 1, NOW(), 0, NOW(), NOW());

-- 6. 收藏
INSERT INTO activity_favorites (id, user_id, activity_id, favorited_at) VALUES
(1, 1, 2, NOW()),
(2, 1, 6, NOW()),
(3, 1, 9, NOW()),
(4, 2, 1, NOW()),
(5, 2, 4, NOW()),
(6, 2, 7, NOW()),
(7, 3, 1, NOW()),
(8, 3, 5, NOW()),
(9, 3, 10, NOW()),
(10, 4, 2, NOW()),
(11, 4, 6, NOW()),
(12, 5, 3, NOW()),
(13, 5, 8, NOW());

-- 7. 评论
INSERT INTO activity_comments (id, activity_id, author_user_id, content, message_type, status, idempotency_key, request_fingerprint, created_at) VALUES
(1, 1, 2, '好活动周末有空', 1, 1, 's-c1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', NOW()),
(2, 1, 3, '莲花山我熟可以带路', 1, 1, 's-c2', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', NOW()),
(3, 1, 4, '集合点是南门吗', 1, 1, 's-c3', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', NOW()),
(4, 2, 3, '软件产业基地哪口出', 1, 1, 's-c4', 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', NOW()),
(5, 2, 4, 'AA预算大概多少', 1, 1, 's-c5', 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', NOW()),
(6, 2, 5, '报名了报名了', 1, 1, 's-c6', 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', NOW()),
(7, 6, 2, '看什么片', 1, 1, 's-c7', '1111111111111111111111111111111111111111111111111111111111111111', NOW()),
(8, 6, 5, 'IMAX确实爽', 1, 1, 's-c8', '2222222222222222222222222222222222222222222222222222222222222222', NOW()),
(9, 9, 2, '今晚几点', 1, 1, 's-c9', '3333333333333333333333333333333333333333333333333333333333333333', NOW()),
(10, 9, 3, '七点西门见', 1, 1, 's-c10', '4444444444444444444444444444444444444444444444444444444444444444', NOW()),
(11, 8, 1, '刺身据说很新鲜', 1, 1, 's-c11', '5555555555555555555555555555555555555555555555555555555555555555', NOW()),
(12, 4, 1, '缺人差一个', 1, 1, 's-c12', '6666666666666666666666666666666666666666666666666666666666666666', NOW());

-- 8. 好友
INSERT INTO user_follows (id, follower_user_id, followed_user_id, status, remark_name, followed_at, accepted_at) VALUES
(1, 1, 2, 1, '南山阿杰', NOW(), NOW()),
(2, 2, 1, 1, '港漂小明', NOW(), NOW()),
(3, 1, 3, 1, '宝安小丽', NOW(), NOW()),
(4, 3, 1, 1, '港漂小明', NOW(), NOW()),
(5, 1, 4, 1, '龙华大壮', NOW(), NOW()),
(6, 4, 1, 1, '港漂小明', NOW(), NOW()),
(7, 2, 3, 1, NULL, NOW(), NOW()),
(8, 3, 2, 1, NULL, NOW(), NOW()),
(9, 2, 5, 1, NULL, NOW(), NOW()),
(10, 5, 2, 1, NULL, NOW(), NOW()),
(11, 3, 5, 0, NULL, NOW(), NULL),
(12, 4, 2, 1, NULL, NOW(), NOW()),
(13, 2, 4, 1, NULL, NOW(), NOW());

-- 9. 聊天会话
INSERT INTO chat_conversations (id, user_a_id, user_b_id, last_message_preview, last_message_type, last_message_at, user_a_unread_count, user_b_unread_count, created_at, updated_at) VALUES
(1, 1, 2, '周末一起去吧', 1, NOW(), 0, 1, NOW(), NOW()),
(2, 1, 3, '莲花山我熟到时带路', 1, NOW(), 0, 0, NOW(), NOW()),
(3, 1, 4, '篮球差一个人', 1, NOW(), 1, 0, NOW(), NOW()),
(4, 2, 3, '咖啡局来不来', 1, NOW(), 0, 0, NOW(), NOW());

-- 10. 聊天消息
INSERT INTO chat_messages (id, conversation_id, sender_user_id, content, message_type, sent_at) VALUES
(1, 1, 1, '嗨周末有空吗', 1, NOW()),
(2, 1, 2, '周六下午可以', 1, NOW()),
(3, 1, 1, '那就一起去吧', 1, NOW()),
(4, 2, 1, '莲花山徒步报了吗', 1, NOW()),
(5, 2, 3, '报了到时带路', 1, NOW()),
(6, 3, 4, '篮球差一个人', 1, NOW()),
(7, 3, 1, '在的我来', 1, NOW()),
(8, 4, 2, '咖啡局来不来', 1, NOW()),
(9, 4, 3, '必须来', 1, NOW());

-- 11. 帖子
INSERT INTO posts (id, author_user_id, operator_user_id, activity_id, status, visibility, title, content, like_count, comment_count, published_at, version, created_at, updated_at) VALUES
(1, 2, 2, 2, 2, 1, '咖啡笔记', '今天去了科技园那家咖啡店，手冲肯尼亚AA真的绝了，回甘带点蓝莓味。下次搞杯测会！', 5, 2, NOW(), 0, NOW(), NOW()),
(2, 1, 1, 1, 2, 1, '徒步路线图', '简单画了莲花山徒步路线，南门上山山顶广场北门下来，大概3小时。带好水！', 8, 3, NOW(), 0, NOW(), NOW()),
(3, 3, 3, 3, 2, 1, '瑜伽初体验', '第一次练流瑜伽，老师超温柔，很多动作做不到但是很放松。推荐给想解压的小伙伴。', 3, 1, NOW(), 0, NOW(), NOW()),
(4, 4, 4, 4, 2, 1, '篮球缺一人', '5v5篮球差一个人！周六下午民治，水平不限来凑数也行，打完吃饭。', 12, 6, NOW(), 0, NOW(), NOW()),
(5, 5, 5, 5, 2, 1, '戚风蛋糕教程', '分享几个关键点：蛋白硬性发泡、翻拌不画圈、150度60分钟。下次活动带大家实操！', 15, 8, NOW(), 0, NOW(), NOW());

-- 12. 帖子点赞
INSERT INTO post_likes (id, user_id, post_id, liked_at) VALUES
(1, 1, 1, NOW()),
(2, 1, 2, NOW()),
(3, 1, 4, NOW()),
(4, 1, 5, NOW()),
(5, 2, 2, NOW()),
(6, 2, 4, NOW()),
(7, 2, 5, NOW()),
(8, 3, 1, NOW()),
(9, 3, 3, NOW()),
(10, 3, 5, NOW()),
(11, 4, 1, NOW()),
(12, 4, 2, NOW()),
(13, 4, 4, NOW()),
(14, 5, 2, NOW()),
(15, 5, 3, NOW()),
(16, 5, 4, NOW());

-- 13. 敏感词
INSERT INTO sensitive_words (id, category_code, word_pattern, severity, replacement_text, status, hit_count, version, created_at, updated_at) VALUES
(1, 'custom', '傻逼|垃圾|废物', 1, '***', 1, 0, 0, NOW(), NOW()),
(2, 'custom', '加微信|vx|私聊|代刷', 2, NULL, 1, 0, 0, NOW(), NOW()),
(3, 'insult', '妈.*逼|去死|草', 1, '***', 1, 0, 0, NOW(), NOW());

-- 14. 用户定位
INSERT INTO user_locations (user_id, province_name, city_name, district_name, street_name, latitude, longitude, location_source, updated_at, created_at) VALUES
(1, '广东省', '深圳市', '福田区', '莲花街道', 22.5431, 114.0578, 3, NOW(), NOW()),
(2, '广东省', '深圳市', '南山区', '科技园', 22.5433, 113.9440, 3, NOW(), NOW()),
(3, '广东省', '深圳市', '宝安区', '新安街道', 22.5550, 114.0850, 0, NOW(), NOW()),
(4, '广东省', '深圳市', '龙华区', '民治街道', 22.5700, 114.0300, 1, NOW(), NOW()),
(5, '广东省', '深圳市', '龙岗区', '南湾街道', 22.5350, 114.1150, 0, NOW(), NOW());
