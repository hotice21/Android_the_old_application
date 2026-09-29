# Eligo — 港人北上活动社交 App

> 华南师范大学南海校区 · 阿伯丁数据科学与人工智能学院 · 人工智能 1 班毕业设计

## 项目结构

```
Android_the_old_application/
├── eligo-new/          # 后端：Spring Boot 4 + Kotlin + MyBatis-Plus + MySQL + Redis + JWT
├── eligo-frontend/     # 前端：uni-app + Vue 3（一套代码多端运行：H5 / 微信小程序 / App）
├── android-app/        # Android WebView 壳：加载前端 H5 产物
├── docs/               # API 文档等
└── README.md
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1.0、Kotlin 2.1.21、MyBatis-Plus 3.5.16、JDK 17、Flyway、JWT (HS256) |
| 数据库 | MySQL 8.4、Redis 7.2 |
| 前端 | uni-app (Vue 3)、Pinia、Vite、UView UI |
| Android | Kotlin、AGP 8.5、minSdk 21 |
| 容器 | Docker Compose（后端三件套一键起） |

## 前置条件

开发前请确认本机已安装：

| 工具 | 版本 | 检查命令 |
|---|---|---|
| Docker Desktop | 最新稳定版 | `docker info` |
| Node.js | ≥ 18（推荐 20 LTS） | `node -v` |
| JDK | 17（仅本地跑后端需要，Docker 方式不用） | `java -version` |
| Android Studio | Hedgehog 及以上（仅打包 Android APK 需要） | — |

## 快速启动（5 分钟跑起来）

### 1. 克隆仓库

```powershell
git clone https://github.com/hotice21/Android_the_old_application.git
cd Android_the_old_application
```

### 2. 启动后端（Docker Compose，推荐）

```powershell
cd eligo-new

# 一键构建 + 启动 MySQL / Redis / API（首次约 3-5 分钟）
docker compose up --build -d

# 验证：三个服务都 healthy 就成功
docker compose ps
# NAME          STATUS
# eligo-api     Up (healthy)    ← http://localhost:8080
# eligo-mysql   Up (healthy)    ← localhost:3306
# eligo-redis   Up (healthy)    ← localhost:6379
```

或者**本地直接跑后端**（不推荐，需要手动装 MySQL 8.4 + Redis）：
```powershell
cd eligo-new
.\gradlew.bat bootRun --args="--spring.profiles.active=local-dev"
```

### 3. 启动前端（三选一）

#### A. H5 浏览器开发（最快）

```powershell
cd eligo-frontend
npm install          # 首次
npm run dev:h5       # 浏览器打开 http://localhost:3003
```

#### B. 微信小程序

```powershell
cd eligo-frontend
npm run dev:mp-weixin
# 微信开发者工具 → 导入项目 → 选 eligo-frontend\dist\dev\mp-weixin
# AppID: wx7c7e4c0ca9710dca
# 记得勾：详情 → 本地设置 → 不校验合法域名
```

#### C. Android 壳

```powershell
# 方式 1：Android Studio 直接运行
# File → Open → 选 android-app → Run ▶

# 方式 2：命令行一键打包 APK
cd android-app
.\build-apk.ps1
# 产物：android-app\app\build\outputs\apk\debug\app-debug.apk
```

## 停止服务

```powershell
# 停后端（保留数据库数据）
cd eligo-new
docker compose down

# 彻底重置（清掉数据库）
docker compose down -v

# 停前端：在前端终端按 Ctrl + C
```

## 改了代码之后

| 改动 | 操作 |
|---|---|
| 后端代码 | `cd eligo-new && docker compose up --build -d api`（MySQL/Redis 不用重起） |
| 前端 H5 / 小程序 | `dev` 模式自动热重载，保存即可 |
| 前端 H5 → Android APK | `cd android-app && .\build-apk.ps1`（内部自动 build:h5 + sync-assets + assembleDebug） |

## 端口与环境变量

| 服务 | 端口 | 配置位置 |
|---|---|---|
| 后端 API | `localhost:8080` | `eligo-new/docker-compose.yml` |
| H5 前端 (dev) | `localhost:3003` | Vite 默认 |
| MySQL | `localhost:3306` | docker-compose，root 密码 `root`，应用账号 `eligo/eligo123` |
| Redis | `localhost:6379` | docker-compose，无密码 |
| API 健康检查 | `GET /actuator/health/readiness` | 返回 `{"status":"UP"}` |

## 开发便利开关（已默认开启）

| 开关 | 位置 | 值 | 效果 |
|---|---|---|---|
| 后端免鉴权 | `eligo-new/src/main/resources/application-docker.properties` | `eligo.security.enabled=false` | 所有请求跳过登录校验 |
| 默认身份 | 同上 | `eligo.security.dev-admin=true` | 注入 userId=1 的管理员身份 |
| 前端跳过登录拦截 | `eligo-frontend/.env` | `VITE_DEV_SKIP_LOGIN=true` | 所有页面自动视为已登录 |
| 后端地址 | 同上 | `VITE_API_BASE_URL=http://10.0.2.2:8080` | Android 模拟器访问宿主机用这个 |

**真机测试后端**：把 `eligo-frontend/.env` 的 `VITE_API_BASE_URL` 改成电脑局域网 IP，例如 `http://192.168.1.10:8080`，然后重新构建。

## 后端健康检查

```powershell
# 直接访问（Docker 内部）
docker exec eligo-api curl -s http://localhost:8080/actuator/health

# 验证业务接口
docker exec eligo-api curl -s "http://localhost:8080/api/v1/activities?limit=3"
# 返回 {"code":0,"message":"成功",...} 表示正常
```

## 常见问题

### Q: Docker 拉镜像超时？
已配置国内镜像源（`docker.1ms.run` / `hub.rat.dev` / 腾讯云），重启 Docker Desktop 后生效。

### Q: MySQL 启动报密码错误？
之前创建过 volume 里有旧密码。清掉重建：
```powershell
cd eligo-new
docker compose down -v
docker compose up --build -d
```

### Q: 前端 H5 跑起来但接口 404 / 连接不上？
检查 `eligo-frontend/.env` 里 `VITE_API_BASE_URL`：
- Android 模拟器：`http://10.0.2.2:8080` ✅
- 真机 / 小程序（跑在本机）：`http://127.0.0.1:8080`
- 真机（局域网）：`http://电脑IP:8080`

### Q: 微信开发者工具请求被拦截？
详情 → 本地设置 → 勾选 **不校验合法域名、web-view、TLS 版本以及 HTTPS 证书**。

### Q: Android Studio Gradle Sync 慢？
已配置阿里云镜像，首次 Sync 约 1-3 分钟。

### Q: 后端 SQLSyntaxErrorException（ORDER BY 前报错）？
这个 bug 已修复（`ActivityReadMapper` WHERE 条件缺括号）。如果遇到同样错误，拉最新代码重建：
```powershell
cd eligo-new
git pull && docker compose up --build -d api
```

## 仓库地址

🔗 https://github.com/hotice21/Android_the_old_application
