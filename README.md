# Eligo — 港人北上活动社交 App

> 华南师范大学南海校区 · 阿伯丁数据科学与人工智能学院 · 人工智能 1 班毕业设计

## 项目结构

```
Android_the_old_application-main/   ← 根目录（README.md 就在这里）
├── eligo-new/          # 后端：Spring Boot 4 + Kotlin + MySQL + Redis + JWT
├── eligo-frontend/     # 前端：uni-app + Vue 3（H5 / 微信小程序 / App）
├── android-app/        # Android WebView 壳
└── README.md
```

---

## 🚀 启动代码（**确保 PowerShell 当前在根目录**，每条命令直接复制粘贴）

> 前置条件：**已安装 Docker Desktop** + **Node.js ≥ 18**。
> 第一次打开终端先 `cd` 到根目录（README.md 所在位置），之后全程不换目录。
> 所有命令已在 Windows PowerShell 5 下验证通过。

### 0. 进入根目录（如果你还没在这里）

```powershell
cd Android_the_old_application-main
```

### ① 启动后端三件套（MySQL + Redis + API）

```powershell
docker compose -f .\eligo-new\docker-compose.yml up --build -d
```

首次构建约 3-5 分钟。验证：

```powershell
docker compose -f .\eligo-new\docker-compose.yml ps
```

三个服务都显示 **healthy** 就成功 ✅（eligo-api / eligo-mysql / eligo-redis）。

### ② 灌入测试数据（仅首次 / 重置时）

```powershell
Get-Content ".\eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

灌入后 DB 里有：5 用户 / 10 活动 / 5 帖子 / 4 聊天会话 / 13 好友关系。

### ③ 启动前端 H5 开发服务器

```powershell
cd .\eligo-frontend; if (-not (Test-Path "node_modules")) { npm install }; npm run dev:h5
```

启动后浏览器打开终端显示的地址（通常是 **http://localhost:3003**，端口冲突会自动递增到 3004/3005）。

> 这条命令会占用终端。需要同时跑前后端？**开两个 PowerShell 窗口**，都先 `cd Android_the_old_application-main`，一个跑后端、一个跑前端。

### ④ （可选）Android 模拟器 / 真机 — 改后端地址

编辑 `.\eligo-frontend\.env` 里的 `VITE_API_BASE_URL`：

| 运行环境 | 地址 |
|---|---|
| H5 浏览器（当前已配好） | `http://localhost:8080` ✅ |
| Android 模拟器 | `http://10.0.2.2:8080` |
| 真机（同局域网） | `http://电脑IP:8080`（如 `http://192.168.1.10:8080`） |

改完重跑 ③ 的命令。

---

## 📋 常用速查表（全部在根目录执行，直接复制）

### 改了后端代码 → 只重建 API 容器（最快）

```powershell
docker compose -f .\eligo-new\docker-compose.yml build --no-cache api; docker compose -f .\eligo-new\docker-compose.yml up -d api
```

### 改了后端代码 + DB migration 有变动 → 全重建

```powershell
docker compose -f .\eligo-new\docker-compose.yml down -v; docker compose -f .\eligo-new\docker-compose.yml up --build -d; Start-Sleep -Seconds 45; Get-Content ".\eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

### 彻底重置（清 DB + 重 seed）

```powershell
docker compose -f .\eligo-new\docker-compose.yml down -v; docker compose -f .\eligo-new\docker-compose.yml up -d; Start-Sleep -Seconds 45; Get-Content ".\eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

### 停止所有服务

```powershell
docker compose -f .\eligo-new\docker-compose.yml down
```

### 验证后端健康

```powershell
docker exec eligo-api curl -s http://localhost:8080/actuator/health
# 期望返回 {"status":"UP",...}
```

### 验证核心业务接口

```powershell
# 活动列表（应返回 10 条，中文标题正确）
docker exec eligo-api curl -s "http://localhost:8080/api/v1/activities?limit=3"

# 帖子列表（应返回 5 条）
docker exec eligo-api curl -s "http://localhost:8080/api/v1/posts"

# 聊天会话（应返回 3-4 条）
docker exec eligo-api curl -s "http://localhost:8080/api/v1/chat/conversations"
```

### 微信小程序开发

```powershell
cd .\eligo-frontend; npm run dev:mp-weixin
# 微信开发者工具 → 导入项目 → 选 eligo-frontend\dist\dev\mp-weixin
# AppID: wx7c7e4c0ca9710dca
# 勾：详情 → 本地设置 → 不校验合法域名
```

### 打包 Android APK

```powershell
cd .\android-app; .\build-apk.ps1
# 产物：android-app\app\build\outputs\apk\debug\app-debug.apk
```

---

## 端口 & 服务一览

| 服务 | 地址 | 说明 |
|---|---|---|
| 后端 API | http://localhost:8080 | Spring Boot 4.1.0 |
| H5 前端 dev | http://localhost:3003 | 端口冲突时自动递增 |
| MySQL 8.4 | localhost:3306 | root/root，DB: eligo，**utf8mb4** |
| Redis 7.2 | localhost:6379 | 无密码 |
| API 健康检查 | `GET /actuator/health/readiness` | 返回 `{"status":"UP"}` |

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1.0、Kotlin 2.1.21、MyBatis-Plus 3.5.16、JDK 17、Flyway、JWT (HS256) |
| 数据库 | MySQL 8.4（utf8mb4 强制）、Redis 7.2 |
| 前端 | uni-app (Vue 3)、Vite |
| 容器 | Docker Compose（MySQL + Redis + API 一键起） |

## 开发便利开关（已默认开启）

| 开关 | 位置 | 值 | 效果 |
|---|---|---|---|
| 后端免鉴权 | `eligo-new/src/main/resources/application.properties` | `eligo.security.enabled=false` | 所有请求跳过登录校验 |
| 默认身份 | 同上 | `eligo.security.dev-admin=true` | 自动注入 userId=1 管理员 |
| 前端跳过登录 | `eligo-frontend/.env` | `VITE_DEV_SKIP_LOGIN=true` | 所有页面视为已登录 |
| 后端地址 | `eligo-frontend/.env` | `VITE_API_BASE_URL=http://localhost:8080` | H5 浏览器直连 |
| CORS | 后端 SecurityConfig | 全开放 | 浏览器跨域无阻碍 |
| JVM 编码 | `eligo-new/Dockerfile` | `-Dfile.encoding=UTF-8` | 中文无乱码 |
| MySQL 编码 | `eligo-new/docker-compose.yml` | `SET NAMES utf8mb4` 永久生效 | 中文存/取无乱码 |

## 常见问题

### Q: `docker compose up` 拉镜像超时？
已配国内镜像源（1ms.run / rat.dev / 腾讯云），重启 Docker Desktop 生效。

### Q: MySQL 报密码错误？
旧 volume 里有残留，清掉重建：
```powershell
docker compose -f .\eligo-new\docker-compose.yml down -v; docker compose -f .\eligo-new\docker-compose.yml up --build -d
```

### Q: 前端页面"加载失败" / ERR_FAILED？
打开浏览器 F12 → Console 看错误。99% 是 CORS 或后端没起。确认：
```powershell
docker compose -f .\eligo-new\docker-compose.yml ps
Get-Content .\eligo-frontend\.env
```

### Q: 中文乱码（èŽ²èŠ±å±± 这种）？
MySQL client 编码问题。清 DB 重 seed：
```powershell
docker compose -f .\eligo-new\docker-compose.yml down -v; docker compose -f .\eligo-new\docker-compose.yml up -d; Start-Sleep -Seconds 45; Get-Content ".\eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

### Q: Flyway migration failed？
```powershell
docker compose -f .\eligo-new\docker-compose.yml down -v; docker compose -f .\eligo-new\docker-compose.yml up -d
```
删 volume 让 Flyway 完整重跑所有 migration。

### Q: 后端 SQLSyntaxErrorException？
```powershell
git pull; docker compose -f .\eligo-new\docker-compose.yml build --no-cache api; docker compose -f .\eligo-new\docker-compose.yml up -d api
```

---

🔗 https://github.com/hotice21/Android_the_old_application
