# Eligo 项目 — 新机器一键启动

> 适用于 Windows 10/11 + Docker Desktop 已安装并运行的新机器
> 打开 PowerShell（或 PowerShell 7），cd 到本仓库根目录，逐条复制执行

---

## ✅ 前置检查（先跑这个，确认 Docker 真的在跑）

```powershell
docker version 2>&1 | Select-String -Pattern "Server Version|ERROR"
```

**预期**: 看到 `Server Version: 2x.x.x`。如果看到 `ERROR` — 去开 Docker Desktop 等它启动完再继续。

---

## 一键启动（3 条命令）

### 命令 ①：三件套 up（后端 API + MySQL + Redis）

```powershell
cd .\eligo-new; docker compose up --build -d mysql redis 2>&1
```

**预期输出**: `Creating eligo-mysql ... done`、`Creating eligo-redis ... done`

**如果失败**: 看下面"常见问题"第 1/2/3 条。

等 **30 秒**（MySQL 首次初始化数据目录需要时间），再跑：

```powershell
docker exec eligo-mysql mysqladmin ping -uroot -proot 2>&1
```

**预期**: `mysqld is alive`。如果没有就再等 10 秒重跑。

### 命令 ②：灌种子数据（活动/帖子/聊天消息）

```powershell
Get-Content ".\eligo-new\seed_data.sql" -Raw | docker exec -i eligo-mysql mysql -uroot -proot --default-character-set=utf8mb4 eligo 2>&1
```

**预期**: 无报错。灌完验证一下：

```powershell
docker exec eligo-mysql mysql -uroot -proot eligo -e "SELECT COUNT(*) AS cnt FROM activities;" 2>&1
```

**预期**: `cnt` 下面一行是 `10`。

### 命令 ③：启动后端 API

```powershell
cd .\eligo-new; docker compose up --build -d api 2>&1
```

等 **40-60 秒**（Gradle 下载依赖 + 编译 Kotlin + Spring Boot 启动，首次比较慢），验证：

```powershell
docker exec eligo-api curl.exe -s http://localhost:8080/actuator/health 2>&1
```

**预期**: `{"status":"UP",...}` 里有 `UP`。

### 命令 ④（可选）：启动前端 H5 dev

如果你想改前端代码用 dev server，或者前端还没 build 好：

```powershell
cd .\eligo-frontend
if (-not (Test-Path "node_modules")) { npm.cmd install }
npm.cmd run dev:h5
```

**访问地址**: http://localhost:3003 （或看 Vite 输出的端口）

### 命令 ④ 备选：用 nginx 直接跑已 build 的前端

```powershell
cd .\eligo-new; docker compose up --build -d web 2>&1
```

**访问地址**: http://localhost:8088

---

## 🎯 启动后快速验证（全绿才算成功）

```powershell
Write-Host "=== 1. API 健康 ==="; docker exec eligo-api curl.exe -s http://localhost:8080/actuator/health; Write-Host ""
Write-Host "=== 2. MySQL ==="; docker exec eligo-mysql mysqladmin ping -uroot -proot 2>&1; Write-Host ""
Write-Host "=== 3. Redis ==="; docker exec eligo-redis redis-cli ping 2>&1; Write-Host ""
Write-Host "=== 4. 容器列表 ==="; docker compose -f .\eligo-new\docker-compose.yml ps 2>&1
Write-Host "=== 5. 活动列表 ==="; docker exec eligo-api curl.exe -s http://localhost:8080/api/v1/activities?limit=1 | Select-String '"code":0'
Write-Host "=== 6. 帖子列表 ==="; docker exec eligo-api curl.exe -s http://localhost:8080/api/v1/posts | Select-String '"code":0'
Write-Host "=== 全部验证完成 ==="
```

**预期**:
- API 健康 → 有 `UP`
- MySQL → `mysqld is alive`
- Redis → `PONG`
- 容器列表 → 全 `Up` (healthy 最好，mysql 首次启动可能还没 healthy，等 30 秒再看)
- 活动/帖子 → 有 `"code":0`

---

## 🔧 常见启动失败排查

### ❶ `docker: Error response from daemon: ... port is already allocated`

端口冲突。MySQL/Redis/API/Web 分别占 3306/6379/8080/8088。

```powershell
# 查哪个进程占了端口
netstat -ano | findstr ":3306"
# Windows 服务里的 MySQL 占了？停掉它：
Get-Service -Name "*mysql*" | Stop-Service
# 或改 docker-compose.yml 端口：
# MYSQL_PORT=3307 REDIS_PORT=6380 SERVER_PORT=8081 WEB_PORT=8089
```

### ❷ `Error: invalid reference format: repository name must be lowercase`

PowerShell 环境变量没设好或有空格。直接在命令前显式设：

```powershell
$env:MYSQL_ROOT_PASSWORD="root"; docker compose up --build -d mysql
```

### ❸ `mysql (eligo-mysql) is unhealthy` 持续超过 1 分钟

旧的 MySQL volume 里有不兼容数据。**强制清库重来**：

```powershell
# 停掉并删除所有容器和命名卷
docker compose -f .\eligo-new\docker-compose.yml down -v
# 重新 up
docker compose -f .\eligo-new\docker-compose.yml up --build -d mysql redis
# 等 40 秒再灌 seed
```

### ❹ `api` 容器启动 90 秒以上还没 UP

Gradle 下载依赖中。看日志：

```powershell
docker logs eligo-api --tail 30 -f
```

- 看到 `Downloading https://...` → 正常，等（可能需要 5-10 分钟，取决于网速）
- 看到 `BUILD SUCCESSFUL` → 正常，Spring Boot 还在启动
- 看到 `Caused by:` + stack trace → 贴给我

### ❺ `Gradle build failed: Could not resolve ...`

网络问题或代理。设置 gradle 走代理（如果公司网络需要）：

```powershell
# 在 eligo-new/gradle.properties 加一行：
Add-Content ".\gradle.properties" "systemProp.http.proxyHost=你的代理IP"
Add-Content ".\gradle.properties" "systemProp.http.proxyPort=端口"
```

### ❻ `web` 容器 build 失败：`COPY failed: ... no such file or directory`

docker-compose.yml 里 web 的 build context 是 `../eligo-frontend`。确认你**是从 eligo-new 目录跑 docker compose** 的：

```powershell
# ✅ 正确
cd .\eligo-new; docker compose up -d
# ❌ 错误（会找不到 ../eligo-frontend）
cd .\; docker compose -f .\eligo-new\docker-compose.yml up -d web
```

**简单的解决办法**: 如果不想用 web 服务，只跑前三件套：

```powershell
cd .\eligo-new; docker compose up --build -d mysql redis api
# 前端用 dev server（npm run dev:h5）更方便
```

### ❼ Windows Docker Desktop 打不开 WSL2

```powershell
# 以管理员身份跑 PowerShell
wsl --install
# 然后重启电脑，开 Docker Desktop 设置里勾 "Use the WSL 2 based engine"
```

### ❽ `docker compose` vs `docker-compose`

新版 Docker 用 `docker compose`（空格，v2），旧版用 `docker-compose`（连字符，v1）。
如果你那台电脑只有 v1：

```powershell
docker-compose -f .\eligo-new\docker-compose.yml up --build -d
```

### ❾ MySQL 中文乱码

确认 seed 命令带了 `--default-character-set=utf8mb4`（我已在命令里加了）。
如果还是乱码：

```powershell
docker exec eligo-mysql mysql -uroot -proot eligo -e "SET NAMES utf8mb4; SELECT title FROM activities LIMIT 3;" 2>&1
```

---

## 🛑 停止 / 重置

```powershell
# 停止所有（保留数据）
docker compose -f .\eligo-new\docker-compose.yml down

# 停止 + **清掉所有数据**（完全重来）
docker compose -f .\eligo-new\docker-compose.yml down -v
```

---

## 📋 技术栈速查（让另一个 AI 看）

| 组件 | 版本 | 端口 | 备注 |
|---|---|---|---|
| Spring Boot | 4.1.0 | - | JDK 17 + Kotlin 2.1.21 |
| MySQL | 8.4 | 3306 | root/root, DB=eligo, utf8mb4 |
| Redis | 7.2 | 6379 | 无密码 |
| API | - | 8080 | Spring Boot Actuator 健康检查 |
| uni-app H5 | 3.x | 3003+ | 前端 dev server |

完整技术细节见 **AGENT_BRIEF.md**
