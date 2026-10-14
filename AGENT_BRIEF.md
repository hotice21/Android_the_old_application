# Eligo 项目 Brief（给另一个 AI 的完整交接文档）

> 本文档供另一个 AI Agent 快速理解项目并接手修复/开发工作。
> 所有路径均以仓库根目录（即本文件所在目录）为基准。

---

## 一、项目简介

**Eligo** — 适老化活动社交平台（毕业设计）。

核心功能：
- 活动广场（徒步、广场舞、太极拳、乐队、书法、象棋等老年活动）
- 活动详情（报名/取消、收藏、评论、关注主办方、分享）
- 动态/发帖（标题+正文+图片+分类标签）
- 即时聊天（一对一会话，支持文本 + 语音消息）
- 个人中心（资料、手机号绑定、我的报名、我的收藏、我的好友、账号设置、退出登录、注销账号）
- 地图（附近活动）

**GitHub**: https://github.com/hotice21/Android_the_old_application
**主分支**: `main`
**当前最新 commit**: `47998a2`

---

## 二、目录结构

```
Android_the_old_application-main/
├── README.md                          # 项目说明 + 一键启动命令
├── eligo-new/                         # 后端（Spring Boot + Kotlin）
│   ├── build.gradle.kts               # Gradle KTS 构建文件
│   ├── settings.gradle.kts
│   ├── gradlew.bat                    # Windows Gradle Wrapper
│   ├── Dockerfile                     # API 镜像构建
│   ├── docker-compose.yml             # MySQL + Redis + API + Web 四件套
│   ├── seed_data.sql                  # 13 张表种子数据
│   └── src/main/
│       ├── kotlin/com/eligo/server/   # 后端源码（22 个 Controller）
│       └── resources/
│           ├── application*.properties
│           └── db/migration/          # Flyway 22 个迁移脚本 V1→V22
├── eligo-frontend/                    # 前端（uni-app + Vue 3）
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── pages.json                 # 路由注册
│       ├── api/                       # 前端 API 封装（request.js + 各模块）
│       ├── stores/                    # Pinia 状态管理（session.js, activity.js）
│       ├── config/runtime.js          # API_BASE_URL 运行时配置
│       ├── components/                # ActivityCard, BottomNavigation
│       └── pages/                     # 18 个 .vue 页面
└── [可能还有 android/ 原生壳，暂时不关键]
```

---

## 三、技术栈（版本号精确）

| 层 | 技术 | 版本 |
|---|---|---|
| **后端框架** | Spring Boot | **4.1.0** |
| **后端语言** | Kotlin | **2.1.21** |
| **JDK** | OpenJDK | **17** |
| **ORM** | MyBatis-Plus | (Spring Boot 4.1 内带) |
| **数据库** | MySQL | **8.4** |
| **缓存** | Redis | **7.2** |
| **迁移** | Flyway | (Spring Boot 内带) |
| **容器** | Docker Compose | (三件套: mysql + redis + api) |
| **前端框架** | uni-app (Vue 3) | `@dcloudio/uni-app` 3.0.0-5010520260709002 |
| **前端语言** | Vue | **3.4.21** |
| **构建** | Vite | **5.2.8** |
| **目标** | H5 + 微信小程序 | (本项目主要跑 H5) |

---

## 四、快速启动（Windows PowerShell 5，一条一条复制执行）

### 前置条件
- Docker Desktop 已启动
- Node.js >= 18（前端需要）
- **PowerShell 5**（不是 PowerShell 7，不要用 `&&` 和 `||`）

### 4 条核心命令（根目录下直接跑）

```powershell
# ① 启动后端三件套（MySQL + Redis + API）
docker compose -f .\eligo-new\docker-compose.yml up --build -d

# ② 灌入测试种子数据（必须等 mysql healthy 后再跑）
Get-Content ".\eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo

# ③ 启动前端 H5 dev server（首次自动 npm install）
cd .\eligo-frontend; if (-not (Test-Path "node_modules")) { npm.cmd install }; npm.cmd run dev:h5

# ④ 验证后端健康（新终端）
docker exec eligo-api curl.exe -s http://localhost:8080/actuator/health
# 预期: {"status":"UP"}
```

### 访问地址
- 前端 H5: **http://localhost:3003** 或 **http://localhost:3004** 或 **http://localhost:3005**（Vite 自动分配）
- 后端 API: **http://localhost:8080**
- MySQL: `localhost:3306` root/root
- Redis: `localhost:6379`（无密码）

### 其他常用命令

```powershell
# 查容器状态
docker compose -f .\eligo-new\docker-compose.yml ps

# 查看 API 日志
docker logs eligo-api --tail 50

# 重 build 并重启 API（改了 Kotlin 代码必须跑）
docker compose -f .\eligo-new\docker-compose.yml up --build -d api

# 停所有服务
docker compose -f .\eligo-new\docker-compose.yml down

# 清掉 MySQL 数据卷（等于重置数据库）
docker compose -f .\eligo-new\docker-compose.yml down -v
# 然后重新 seed

# Gradle 编译检查（本地）
cd .\eligo-new; .\gradlew.bat compileKotlin --no-daemon
```

### Dev 登录（不需要真实账号）

启动后打开 H5 → 显示 brand 页 → 显示 onboarding 页 → **点「匿名登录（测试）」按钮** → 自动跳转到活动广场（dev 模式下 API 自动注入 userId=1，所有接口直接可用）

---

## 五、后端 API 清单（精确路径 + 关键 DTO 字段）

所有 API 前缀 `/api/v1`，dev 模式下自动鉴权（userId=1）。

### 活动

| 方法 | 路径 | 返回 unwrap 后 data |
|---|---|---|
| GET | `/api/v1/activities?limit=10&cursor=` | `{ items: [{ activityId, title, categoryCode, cover:{fileId,url}, owner:{ownerId,displayName,avatar}, startsAt, endsAt, participantCount, capacity, registrationStatus, placeName, addressDetail, feeType, regionCode, latitude, longitude }], nextCursor, hasMore }` |
| GET | `/api/v1/activities/{activityId}` | 完整对象（比列表多 description, media[], organizer） |
| POST | `/api/v1/users/me/activities` | 创建活动（当前后端 Controller 完整） |
| GET | `/api/v1/activities/map` | 地图附近活动 |
| PUT | `/api/v1/activities/{id}/registration` | 报名/取消 |

### 活动评论

| 方法 | 路径 | DTO |
|---|---|---|
| GET | `/api/v1/activities/{id}/comments` | `{ items: [{ id, authorName, content, createdAt }] }` |
| POST | `/api/v1/activities/{id}/comments` | body: `{ content: string }` |

### 活动收藏

| 方法 | 路径 |
|---|---|
| PUT | `/api/v1/activities/{id}/favorite` |
| DELETE | `/api/v1/activities/{id}/favorite` |
| GET | `/api/v1/users/me/favorite-activities` | 返回 `{ items: [ { activityId, ...嵌套完整 activity } ] }` |

### 活动报名

| 方法 | 路径 |
|---|---|
| GET | `/api/v1/users/me/participations` | 返回 `{ items: [ { participationId, activity:{ activityId, title, cover, ... } } ] }` |

### 帖子 / 动态

| 方法 | 路径 | DTO |
|---|---|---|
| GET | `/api/v1/posts` | `{ items: [{ postId, title, content, author:{authorId,displayName}, publishedAt, activity:{嵌套}, media:[] }] }` |
| GET | `/api/v1/feed/recommended` | 推荐 feed |
| GET | `/api/v1/feed/following` | 关注 feed |
| POST | `/api/v1/users/me/posts` | body: `{ title, content, activityId? }` + header `Idempotency-Key` |
| POST | `/api/v1/posts/{id}/like` | 点赞 |
| DELETE | `/api/v1/posts/{id}/like` | 取消点赞 |

### 聊天

| 方法 | 路径 | DTO |
|---|---|---|
| GET | `/api/v1/chat/conversations` | `List<ChatConversationEntity>` |
| GET | `/api/v1/chat/conversations/{convId}/messages?cursor=&limit=` | `{ items: [{ id, conversationId, senderUserId, content?, messageType(1文本/2图片/3语音), fileId?, voiceFileId?, voiceDurationSeconds?, createdAt }], nextCursor, hasMore }` |
| POST | `/api/v1/chat/conversations/{convId}/messages` | body: `{ content?, messageType:Int(1/2/3), fileId?, voiceFileId?, voiceDurationSeconds? }` |

### 文件上传

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| POST | `/api/v1/files/images` | multipart: `file` + `purpose`(AVATAR/ACTIVITY/POST/VOICE) | `{ fileId, url, purpose, contentType, sizeBytes }` |

**重要**: VOICE purpose 支持 mp3/wav/m4a/aac/ogg/webm/mp4/amr/silk（V22 migration 已固化）。

### 好友 / 关注

| 方法 | 路径 |
|---|---|
| GET | `/api/v1/users/me/friends` |
| POST | `/api/v1/friends/{targetId}/request` |
| PUT | `/api/v1/follows/users/{userId}` |
| DELETE | `/api/v1/follows/users/{userId}` |
| GET | `/api/v1/users/me/following` |
| GET | `/api/v1/users/me/followers` |

### 个人

| 方法 | 路径 |
|---|---|
| GET | `/api/v1/users/me/profile` |
| PUT | `/api/v1/users/me/profile` |
| PUT | `/api/v1/users/me/avatar` | (传 multipart file) |
| POST | `/api/v1/auth/logout` | (后端 SessionController) |
| POST | `/api/v1/account/data-exports` |
| POST | `/api/v1/account/deactivation` |
| POST | `/api/v1/account/phone-binding/request` |

### 鉴权 / 通用

| 方法 | 路径 |
|---|---|
| GET | `/actuator/health` | 健康检查 |
| GET | `/api/v1/system/ping` | |

---

## 六、前端 API 封装（src/api/）

所有 API 封装都调用 `src/api/request.js` 的 `request()` 函数。

**⚠️ 关键：request.js 有 unwrap 逻辑，会从 `{ code, message, data }` 里直接返回 `data`！**

前端页面里 `const res = await getActivities()` 拿到的就是 `{ items: [...], nextCursor, hasMore }`，**不要**再写 `res?.data?.items`！

封装列表：
```
src/api/request.js        → request() + upload() 通用请求
src/api/activity.js      → getActivities / getActivityDetail / joinActivity / favoriteActivity / getActivityFavoriteStatus
src/api/chat.js          → getMyConversations / getConversationMessages / sendMessage
src/api/dynamic.js       → getRecommendedFeed / getFollowingFeed / toggleDynamicLike
src/api/post.js          → createPost / uploadImage
src/api/profile.js       → getMyProfile / updateMyProfile / updateAvatar / bindPhone
src/api/friend.js        → followUser / unfollowUser / getMyFollowings / getMyFollowers
src/api/comment.js      → getActivityComments / postComment
```

---

## 七、前端页面清单（src/pages/）

| 路径 | 功能 | 状态 |
|---|---|---|
| `launch/index.vue` | 启动加载页 | ✅ 正常 |
| `brand/index.vue` | 品牌/欢迎页 | ✅ 正常 |
| `onboarding/index.vue` | 登录/引导/匿名登录 | ✅ 正常 |
| `plaza/index.vue` | **活动广场**（Tab 1） | ✅ 接真实 API |
| `dynamic/index.vue` | **发帖/动态列表**（Tab 0） | ✅ 接真实 API |
| `post-create/index.vue` | **创建帖子**（浮动+号跳转） | ✅ 接真实 API + 语音转文字 |
| `detail/index.vue` | **活动详情** | ✅ 报名/收藏/评论/关注/分享全接 |
| `chat/index.vue` | **会话列表**（Tab 2） | ✅ 接真实 API |
| `chat-detail/index.vue` | **聊天详情** | ✅ 文本+语音完整链路 |
| `mine/index.vue` | **我的主页**（Tab 3） | ✅ 接真实 API |
| `mine/participations/index.vue` | 我报名的活动 | ✅ 接真实 API |
| `mine/favorites/index.vue` | 我收藏的活动 | ✅ 接真实 API |
| `mine/friends/index.vue` | 我的好友（可添加）| ✅ 接真实 API |
| `mine/account-settings/index.vue` | 账号设置（退出/注销/导出）| ✅ 接真实 API |
| `mine/profile-edit/index.vue` | 资料编辑+手机号 11 位验证 | ✅ |
| `friend/index.vue` | 好友发现（关注/粉丝）| ✅ 接真实 API |
| `map/index.vue` | 地图（附近活动）| ⚠️ 有 placeholder 样式残留 |

底部导航 4 tab: **发帖 → 活动 → 聊天 → 我的**

---

## 八、种子数据（seed_data.sql）

13 张表，已通过 Flyway V1→V21 + V22 migration。

| 表 | 数量 |
|---|---|
| users | 5（港漂小明/南山阿杰/宝安小丽/龙华大壮/龙岗阿雯） |
| user_profiles | 5 |
| activities | 10 |
| activity_participations | 21 |
| activity_favorites | 13 |
| activity_comments | 12 |
| user_follows | 13 |
| chat_conversations | 4 |
| chat_messages | 9 |
| posts | 5 |
| post_likes | 16 |
| sensitive_words | 3 |
| user_locations | 5 |

**注意**：MySQL 重建容器后 seed 会丢失，必须手动重灌（见启动命令 ②）。

---

## 九、前端 UI 问题已知清单（让另一个 AI 直接修）

### A. 视觉问题
1. **ActivityCard 渐变背景 placeholder**：当后端返回的 `cover.url` 为 null 或空时，显示 placeholder 渐变而不是真实封面。后端 activities 一定有 cover（seed 数据里每条都有），但如果某条缺失，前端的 fallback 样式要改得好看。
2. **mine 头像**：profile.avatar 后端返回 null，所以 mine/index.vue 用昵称首字占位。如果要显示真实头像，需要后端改 profileService 返回默认头像，或前端接 `/api/v1/files/1/content` 兜底。
3. **plaza scroll-view**：活动列表只渲染 3 张（视口高度），滚到底才有更多。前端是 `scroll-view` 不是 `list-view`，没有上拉加载更多。

### B. 体验问题
4. **语音功能 H5 浏览器支持**：`web Speech API SpeechRecognition` 在 Chrome/Safari 桌面版可用，但 Edge 有时不行，且必须在 `https://` 或 `localhost` 下。如果浏览器不支持，`toggleCommentVoice()` 和 `togglePostVoice()` 会 toast "不支持"。这是已知的 H5 限制，毕设可接受。
5. **聊天 peerName 显示 "用户2"**：chat/index.vue 里直接拼了 `"用户" + peerId`。可以接 `/api/v1/users/me/friends` 做好友 ID→昵称映射，但目前可接受。
6. **发帖没有图片真正上传**：当前 `post-create/index.vue` 选了图只存 tempFilePaths，没调 `uploadImage("/api/v1/files/images")` 拿到 fileId 再提交帖子（后端 PostController 可能不强制，或需要单独关联 post_media 表）。

### C. 后端数据结构限制（不是 bug，是设计）
7. **活动评论没有语音字段**：`activity_comments` 表只有 `content VARCHAR(500)`，没有 voice_file_id。所以评论区的语音按钮只能用 Web Speech API 转文字，不能存真正的音频文件。
8. **帖子正文也没有语音字段**：同理，发帖页的语音也是转文字填回 content。
9. **Ollama 推荐引擎没开**：后端 `recommendation/` 包存在但 Docker 没启 Ollama 容器，feed 推荐接口 fallback 到简单排序。毕设演示阶段可忽略。

### D. Vite proxy / CORS
10. 当前开发环境用 **后端 CORS 全开放**（SecurityConfig.kt 里加了 `.cors()`），前端直接 `API_BASE_URL=http://localhost:8080`。如果要部署到生产，需要配 Nginx 反向代理。

---

## 十、后端 Controller → 前端 API 路径对齐速查

| 前端 API 文件 | 前端路径 | 后端 Controller | 后端实际路径 |
|---|---|---|---|
| activity.js | `/api/v1/activities` | ActivityController | ✅ 对齐 |
| | `/api/v1/activities/{id}/participation` | ParticipationController | ✅ 对齐 |
| | `/api/v1/activities/{id}/favorite` | ActivityFavoriteController | ✅ 对齐 |
| comment.js | `/api/v1/activities/{id}/comments` | ActivityCommentController | ✅ 对齐 |
| friend.js | `/api/v1/follows/users/{id}` | FollowController | ✅ 对齐（注意是 `/users/{id}` 不是 `/{id}`） |
| | `/api/v1/friends/{id}/request` | FriendController | ✅ 对齐 |
| chat.js | `/api/v1/chat/conversations` | ChatController | ✅ 对齐 |
| | `/api/v1/chat/conversations/{id}/messages` | ChatController | ✅ 对齐 |
| post.js | `/api/v1/users/me/posts` | PostController | ✅ 对齐（注意带 `/users/me/`） |
| post.js | `/api/v1/files/images` | FileController | ✅ 对齐（支持 VOICE purpose） |
| profile.js | `/api/v1/users/me/profile` | ProfileController | ✅ 对齐 |
| account-settings | `/api/v1/auth/logout` | AuthController | ✅ 对齐 |
| account-settings | `/api/v1/account/data-exports` | AccountDataController | ✅ 对齐 |
| account-settings | `/api/v1/account/deactivation` | AccountDataController | ✅ 对齐 |

**⚠️ 历史坑**：之前 friend.js 写的是 `/api/v1/follows/{id}`（错），后端实际是 `/api/v1/follows/users/{userId}`（对）。已经修过但提醒一下不要改回去。

---

## 十一、之前踩过的致命坑（让另一个 AI 不要重踩）

| 坑 | 根因 | 修复 |
|---|---|---|
| LambdaQueryWrapper lambda cache 报错 | Entity 从 data class 改成普通 class 后 KFunction 签名变 | **全项目改用 QueryWrapper + 字符串列名** `"column_name"` |
| activities seed 被 chk_activity_time_order 约束拒 | `NOW()` 全部相等违反严格递增 | 用 `DATE_ADD(NOW(), INTERVAL N HOUR)` 造时间差 |
| activities title "Data too long" | `VARCHAR(20) utf8mb4` 限 6 汉字 | 所有 seed title ≤ 6 字 |
| DELETE activities 被 FK 挡 | `activity_comments → activities FK ON DELETE RESTRICT` | `SET FOREIGN_KEY_CHECKS=0` 先删 comments 再删 activities |
| 前端 `.data.items` 套两层壳 | request.js unwrap 已经剥了一层 | 全改 `res?.items`，不要再 `.data.items` |
| Chat API curl 10006 | 路径缺 `/chat` 前缀 | 正确路径 `/api/v1/chat/conversations` |
| Detail participationStatus 永远 false | ref 变量名写错：`detail?.participationStatus` → `detailRes?.participationStatus` | 修变量名 |
| VOICE purpose 上传 500 | FileService 三层白名单：purpose 白 + contentType 白 + LocalFileStorage 正则白 + inspector 魔数扫描 | purpose 加 VOICE、contentType 放行 audio/*、storage 正则加 voices/、inspector 跳过 VOICE |
| MySQL unhealthy | healthcheck 用了 `${MYSQL_ROOT_PASSWORD}` 变量但 compose 模板不展开 | 硬编码 `-proot` |
| LocalFileStorage "对象键非法" | validObjectKey 正则只匹配 avatars/activities/posts + jpg/png | 加 voices/ + activity-contact-qrs/ 前缀 + 音频扩展名 |

---

## 十二、数据库 CHECK 约束速查（避免 INSERT 静默失败）

| 表 | 约束 | 说明 |
|---|---|---|
| activities | `chk_activity_time_order` | `registration_starts_at < registration_ends_at <= starts_at < ends_at` |
| activities | `chk_activity_publish_ready` | PUBLISHED 必须有 cover_file_id, 所有时间非空 |
| activities | `title VARCHAR(20) utf8mb4` | **最多 6 个汉字** |
| chat_messages | `chk_msg_type` | `message_type IN (1, 2, 3)` |
| chat_messages | `chk_msg_content_mode` | 1文本→content必填；2图片→file_id必填；3语音→voice_file_id必填 |
| file_objects | `chk_file_purpose` | `purpose IN ('AVATAR','ACTIVITY','ACTIVITY_CONTACT_QR','POST','VOICE')` |
| activity_comments | `request_fingerprint CHAR(64)` | 纯 hex 字符串，不是 BINARY |

---

## 十三、Git 历史关键 commit

```
1eea66b  feat: 全栈语音功能 - chat-detail完整发送 + detail评论/发帖语音转文字 + VOICE purpose后端完整链
ff53b2c  feat: 补全一二三梯队功能 - detail评论/关注/收藏/发帖API/friends添加/ActivityCard真实封面
c00eeb4  feat: account-settings接真实后端接口 + 扩展功能(注销账号/数据导出)
f89c08b  feat: CORS + chat/onMounted/dotenv localhost + 大量bug修复
67c06ee  fix: MySQL永久utf8mb4 (docker-compose加init-connect)
8dac263  fix: LambdaQueryWrapper全改QueryWrapper + 前端初对接
43af299  docs: README一键启动命令重写
```

---

## 十四、给另一个 AI 的建议工作流

1. **从根目录启动**（README.md 所在目录），不要 cd 进 eligo-new 或 eligo-frontend 再跑
2. **每改完一个后端文件**必须 `docker compose up --build -d api`（Kotlin 热重载不好使）
3. **每改完一个前端文件** H5 dev server 会自动 HMR，浏览器刷新即可
4. **curl 验证**后端接口再写前端对接代码，不要猜 DTO 字段名
5. **MySQL 每次 down -v 后**必须重 seed
6. **遇到 500 错误**立刻 `docker logs eligo-api --tail 50` 看完整 stack trace
7. **改数据库约束**别忘写 Flyway migration 文件（V23, V24...），否则下次重建 DB 会丢
8. **前端 `import { request, upload } from "../../api/request"`** —— upload 是 multipart/form-data 用的

---

Good luck! 🚀
