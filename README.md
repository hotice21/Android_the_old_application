# Eligo — 港人北上活动社交 App

> 华南师范大学南海校区 · 阿伯丁数据科学与人工智能学院 · 人工智能 1 班毕业设计

## 项目结构

```
Android_the_old_application/
├── eligo-new/          # 后端：Spring Boot 4 + Kotlin + MyBatis-Plus + MySQL + Redis + JWT
├── eligo-frontend/     # 前端：uni-app + Vue 3（H5 / 微信小程序 / App）
├── android-app/        # Android WebView 壳
└── README.md
```

---

## 🚀 启动代码（直接复制到 PowerShell 终端）

> 前置条件：已安装 **Docker Desktop** + **Node.js ≥ 18**，并已 `git clone` 到本机。
> 以下所有命令在 **PowerShell 5/7** 下验证通过，直接整行复制粘贴即可。

### ① 启动后端三件套（MySQL + Redis + API）

```powershell
cd eligo-new; docker compose up --build -d
```

> 首次构建约 3-5 分钟。验证成功：
> ```powershell
> docker compose ps
> ```
> `eligo-api` / `eligo-mysql` / `eligo-redis` 都显示 **healthy** ✅

### ② 灌入测试数据（仅首次 / 重置时需要）

```powershell
Get-Content "eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

灌入后 DB 里有：5 个用户 / 10 个活动 / 5 条帖子 / 4 条聊天会话 / 13 个好友关系。

### ③ 启动前端 H5 开发服务器

```powershell
cd eligo-frontend; if (-not (Test-Path "node_modules")) { npm install }; npm run dev:h5
```

启动后打开浏览器访问终端显示的地址（通常是 **http://localhost:3003**，若端口被占会自动递增如 3004/3005）。

### ④ （可选）Android 模拟器 / 真机 — 改后端地址

编辑 `eligo-frontend/.env` 里的 `VITE_API_BASE_URL`：

| 运行环境 | 地址 |
|---|---|
| H5 浏览器开发 | `http://localhost:8080`（当前已配好 ✅） |
| Android 模拟器 | `http://10.0.2.2:8080` |
| 真机（同局域网） | `http://电脑局域网IP:8080`（如 `http://192.168.1.10:8080`） |

改完重跑 `npm run dev:h5`。

---

## 常用速查表（复制粘贴）

### 改了后端代码 → 只重建 API 容器（最快）

```powershell
cd eligo-new; docker compose build --no-cache api; docker compose up -d api
```

### 改了后端代码 + DB migration 有变动 → 全重建

```powershell
cd eligo-new; docker compose down -v; docker compose up --build -d; Get-Content "seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

### 彻底重置（清 DB + 重 seed）

```powershell
cd eligo-new; docker compose down -v; docker compose up -d; Start-Sleep -Seconds 45; Get-Content "seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

### 停止所有服务

```powershell
cd eligo-new; docker compose down
```

### 验证后端是否健康

```powershell
docker exec eligo-api curl -s http://localhost:8080/actuator/health
# 期望返回 {"status":"UP",...}
```

### 验证核心业务接口

```powershell
# 活动列表（应返回 10 条）
docker exec eligo-api curl -s "http://localhost:8080/api/v1/activities?limit=3" | python -c "import sys,json; d=json.load(sys.stdin); [print(x['title']) for x in d['data']['items']]"

# 帖子列表（应返回 5 条）
docker exec eligo-api curl -s "http://localhost:8080/api/v1/posts" | python -c "import sys,json; d=json.load(sys.stdin); [print(x['content'][:30]) for x in d['data']['items']]"

# 聊天会话（应返回 3-4 条）
docker exec eligo-api curl -s "http://localhost:8080/api/v1/chat/conversations" | python -c "import sys,json; d=json.load(sys.stdin); print(f'会话数: {len(d[\"data\"])}')"
```

### 微信小程序开发

```powershell
cd eligo-frontend; npm run dev:mp-weixin
# 微信开发者工具 → 导入项目 → 选 eligo-frontend\dist\dev\mp-weixin
# AppID: wx7c7e4c0ca9710dca
# 勾：详情 → 本地设置 → 不校验合法域名
```

### 打包 Android APK

```powershell
cd android-app; .\build-apk.ps1
# 产物：android-app\app\build\outputs\apk\debug\app-debug.apk
```

---

## 端口 & 服务一览

| 服务 | 地址 | 说明 |
|---|---|---|
| 后端 API | http://localhost:8080 | Spring Boot 4.1.0 |
| H5 前端 dev | http://localhost:3003 | 端口冲突时自动递增 |
| MySQL 8.4 | localhost:3306 | root/root，DB: eligo |
| Redis 7.2 | localhost:6379 | 无密码 |
| API 健康检查 | GET `/actuator/health/readiness` | 返回 `{"status":"UP"}` |

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1.0、Kotlin 2.1.21、MyBatis-Plus 3.5.16、JDK 17、Flyway、JWT (HS256) |
| 数据库 | MySQL 8.4（utf8mb4）、Redis 7.2 |
| 前端 | uni-app (Vue 3)、Vite |
| 容器 | Docker Compose（MySQL + Redis + API 一键起） |

## 开发便利开关（已默认开启）

| 开关 | 位置 | 值 | 效果 |
|---|---|---|---|
| 后端免鉴权 | `application.properties` → docker profile | `eligo.security.enabled=false` | 所有请求跳过登录校验 |
| 默认身份 | 同上 | `eligo.security.dev-admin=true` | 自动注入 userId=1 管理员身份 |
| 前端跳过登录 | `eligo-frontend/.env` | `VITE_DEV_SKIP_LOGIN=true` | 所有页面自动视为已登录 |
| 后端地址 | `eligo-frontend/.env` | `VITE_API_BASE_URL=http://localhost:8080` | H5 浏览器直连 |
| CORS | 后端 SecurityConfig | 全开放 | 浏览器跨域无阻碍 |
| JVM 编码 | Dockerfile | `-Dfile.encoding=UTF-8` | 中文无乱码 |
| MySQL 编码 | docker-compose.yml | `SET NAMES utf8mb4` 永久生效 | 中文存/取无乱码 |

## 常见问题

### Q: `docker compose up` 拉镜像超时？
已配国内镜像源（1ms.run / rat.dev / 腾讯云），重启 Docker Desktop 生效。

### Q: MySQL 启动报密码错误？
之前 volume 有旧密码残留，清掉重建：
```powershell
cd eligo-new; docker compose down -v; docker compose up --build -d
```

### Q: 前端页面"加载失败" / 接口 ERR_FAILED？
打开浏览器 F12 → Console 看错误。99% 情况是 CORS 或后端没起。先确认：
```powershell
# 后端是否在跑 + healthy
docker compose ps
# .env 地址是否正确（H5 浏览器用 localhost:8080）
Get-Content eligo-frontend\.env
```

### Q: 页面中文显示乱码（èŽ²èŠ±å±± 这种）？
MySQL client 编码问题。清 DB 重 seed（带 utf8mb4 参数）：
```powershell
cd eligo-new; docker compose down -v; docker compose up -d; Start-Sleep -Seconds 45; Get-Content "seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo
```

### Q: Flyway 报 migration failed？
```powershell
cd eligo-new; docker compose down -v; docker compose up -d
```
删 volume 让 Flyway 完整重跑所有 migration。

### Q: 后端报 SQLSyntaxErrorException？
拉最新代码重建：
```powershell
cd eligo-new; git pull; docker compose build --no-cache api; docker compose up -d api
```

---

🔗 https://github.com/hotice21/Android_the_old_application
