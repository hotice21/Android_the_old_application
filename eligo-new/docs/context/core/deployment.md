# 本地开发、构建与环境演进

> 当前只支持并验证本地开发。
> 本文中的“未来共享/生产环境”是准入清单，不表示已经部署。
> 当前不需要 Nginx 才能进行开发者工具内的前后端联调。
> eligo-new 仅包含后端，前端在 eligo-main 仓库维护；Docker 基础设施与 eligo-main 共享同一份 MySQL/Redis 配置。
> 原始 Java 版部署指南见 `../../../../../Eligo-main/Eligo-main/docs/context/core/deployment.md`。

## 1. 首次准备

前置：

- Windows + Docker Desktop；
- JDK 17（仅在宿主机运行后端时需要）；
- Gradle（通过 Gradle Wrapper 统一版本，无需单独安装）；
- 微信开发者工具与 Node/npm 仅在需要前端联调时安装，前端在 eligo-main 仓库。

复制环境模板。Docker 基础设施与 eligo-main 共享，位于 eligo-main 项目目录：

```powershell
$repoRoot = git rev-parse --show-toplevel
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
Copy-Item .env.example .env
```

填写本地密码、三个独立 Base64 密钥以及真实微信 AppID/Secret。`.env` 不得提交。

eligo-new 在宿主机运行后端（`bootRun`）时，需要在本项目根目录创建独立的 `.env`，供 `application-local.properties` 通过 `spring.config.import=optional:file:./.env` 读取：

```powershell
Set-Location (Join-Path $repoRoot 'eligo-new')
# 创建 eligo-new/.env，至少包含以下变量（值须与 infra/docker/.env 一致）：
# MYSQL_PORT=3306
# MYSQL_DATABASE=eligo
# MYSQL_USER=eligo
# MYSQL_PASSWORD=<同 infra/docker/.env>
# REDIS_PASSWORD=<同 infra/docker/.env>
# JWT_SIGNING_KEY_BASE64=<32 字节 Base64>
# DATA_ENCRYPTION_KEY_BASE64=<32 字节 Base64>
# DATA_LOOKUP_KEY_BASE64=<32 字节 Base64>
# 可选：WECHAT_APP_ID / WECHAT_APP_SECRET（真实微信登录时需要）
New-Item -ItemType File -Path .env -Force
```

`.env` 不得提交。三个 Base64 密钥不得复用。

检查前端（前端在 eligo-main 仓库，非本项目）：

```text
Eligo-main/Eligo-main/eligo-frontend/src/manifest.json
  mp-weixin.appid == Eligo-main/Eligo-main/infra/docker/.env 中的 WECHAT_APP_ID
```

只比较是否一致，不把 Secret 复制到前端。eligo-new 不包含前端，无需在此检查。

OpenAPI 契约文件位置（eligo-new 内部副本）：

```text
docs/api/openapi.yaml
```

## 2. 一键启动本地后端

eligo-new 自带完整的 Docker Compose 编排，提供 `api`（Kotlin 后端）、`mysql`、`redis` 三个服务，可独立于 eligo-main 运行。

```powershell
Set-Location <eligo-new 根目录>
Copy-Item .env.example .env   # 填写密钥与密码
docker compose up -d --build
docker compose ps
```

期望 `eligo-api`、`eligo-mysql`、`eligo-redis` 三个容器均为 `healthy`。API 容器等待 MySQL、Redis 健康后启动，并自动执行 Flyway 迁移（V1–V18）。

启动依赖并运行 Kotlin 后端：

```powershell
$repoRoot = git rev-parse --show-toplevel
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
docker compose up -d mysql redis
docker compose ps

Set-Location (Join-Path $repoRoot 'eligo-new')
.\gradlew.bat bootRun
```

若只需要任一可用的后端（例如前端联调），也可直接启动共享 Compose 的全部服务（含 Java 版 API）：

```powershell
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
docker compose up -d --build
docker compose ps
```

期望 `mysql`、`redis` 为 `healthy`（启动 Java 版 API 时 `api` 也应为 `healthy`）。共享 Compose 的 API 等待 MySQL、Redis 健康后启动，并对这套 Compose 管理的数据库执行 Flyway；启用 `recommendation` profile 时另有 `qdrant`、`ollama`。

健康验证（Kotlin 后端通过 `bootRun` 运行时）：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
Invoke-RestMethod http://127.0.0.1:8080/api/v1/system/ping
```

注意：`bootRun` 控制台显示 `Started` 不代表 Spring Boot 已就绪。`Invoke-RestMethod` 出现“基础连接已经关闭”时先查看 `docker compose ps` 和后端日志，等待 health 变为 healthy 后重试。

## 3. 仅启动依赖、在 IDE 调试 API

```powershell
$repoRoot = git rev-parse --show-toplevel
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
docker compose up -d mysql redis

Set-Location (Join-Path $repoRoot 'eligo-new')
.\gradlew.bat bootRun
```

在 IDE 中调试时，使用 Gradle `bootRun` 任务或直接运行 `EligoServerApplication.kt` 主类。本机 `local` profile 默认连接 `localhost:3306/6379` 并读取 eligo-new 工作目录的 `.env`。若主要配置仍在 `infra/docker/.env`，需要在 IDE Run Configuration 显式映射变量，不能假定 Spring 会跨目录读取。

不要同时让 Docker API 和 IDE/bootRun API 占用 8080。调试前：

```powershell
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
docker compose stop api
```

## 4. 小程序开发与构建

eligo-new 不包含前端。前端目录、构建命令和小程序导入路径仍以 eligo-main 仓库为准。需要前端联调时，参照原始部署指南的“小程序开发与构建”一节：

- 前端目录：`Eligo-main/Eligo-main/eligo-frontend/`；
- 开发监听：`npm run dev:mp-weixin`；
- 一次性构建：`npm run build:mp-weixin`；
- 微信开发者工具导入：`Eligo-main/Eligo-main/eligo-frontend/dist/dev/mp-weixin`（开发）或 `dist/build/mp-weixin`（构建）；
- `dist/` 是生成物，已被 Git 忽略。

完整前端流程见 `../../../../../Eligo-main/Eligo-main/docs/context/core/deployment.md` 第 4 节。

## 5. 当前联调路径

### 开发者工具

1. Docker 依赖服务 healthy（`mysql`、`redis`）；
2. 前端和后端微信凭证一致；
3. 使用小程序开发者账号登录，不使用游客模式；
4. 开发阶段勾选“不校验合法域名、web-view、TLS 版本以及 HTTPS 证书”；
5. 小程序请求 `http://127.0.0.1:8080`；
6. 按 [IT-001](../../../../../Eligo-main/Eligo-main/docs/integration/IT-001-miniapp-auth-onboarding.md) 验收。

当前请求层地址：

- 微信小程序 fallback：`http://127.0.0.1:8080`；
- H5：空基址 + Vite `/api` 代理；
- 可通过 `VITE_API_BASE_URL` 覆盖。

### 基础数据基线

2026-07-29 只读核验：

```text
Flyway schema version = 3
agreements = 0
interest_tags = 3
```

上述是历史本地环境记录。当前仓库空库迁移基线已到 V18，启用兴趣标签为 10 条，协议仍为 0；本地开发按已确认范围跳过协议，不伪造同意事实。eligo-new 与 eligo-main 共享同一份 Flyway 迁移脚本与 schema 事实。若未来进入共享测试或上线，必须先：

1. 产品负责人提供经确认的用户协议和隐私政策正文；
2. 后端用 V18 之后的空闲迁移号（或受控管理流程）创建版本、生效时间和状态；
3. 重新构建 API；
4. 执行 IT-001 并记录 requestId。

## 6. 常用运维命令

Docker 运维命令在共享的 `infra/docker` 目录执行：

```powershell
$repoRoot = git rev-parse --show-toplevel
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
docker compose ps
docker compose logs --tail=100 api
docker compose logs -f api
docker compose restart api
docker compose up -d --build --force-recreate api
docker compose stop
docker compose down
```

`docker compose down` 保留命名卷。`down -v` 会删除数据库、Redis 和文件卷，不属于普通重启命令。

查看某服务最终配置时使用：

```powershell
docker compose config
```

输出可能包含已展开环境值，截图或粘贴前必须脱敏。

eligo-new 构建与测试命令（在 `eligo-new` 目录执行）：

```powershell
# 清理并构建
.\gradlew.bat clean build

# 运行后端
.\gradlew.bat bootRun

# 运行全量测试
.\gradlew.bat test

# 只运行 OpenAPI 契约测试
.\gradlew.bat test --tests "com.eligo.server.contract.OpenApiContractTest"
```

Linux / Mac 等价命令：

```bash
./gradlew clean build
./gradlew bootRun
./gradlew test
```

## 7. 按症状排障

| 症状 | 优先检查 | 常见原因 |
| --- | --- | --- |
| ping 连接被关闭 | `docker compose ps`、后端日志 | Spring 尚未 ready、容器重启、端口占用 |
| `/agreements/current` 返回空数组 | 数据库协议数量/状态 | 当前确实没有有效协议 |
| 微信登录 `11001` | 微信凭证与 API 日志 | 游客模式、AppID/Secret 不一致、code 过期/重复 |
| 受保护接口 401 | access/refresh、session | token 过期、会话被撤销、刷新失败 |
| 所有受保护接口重复刷新 | Network 面板 | 请求层未 single-flight 或旧请求重复 |
| 图片上传 413/11402 | multipart 配置和大小 | 超过 10 MB 或网关限制（未来） |
| 数据库迁移失败 | API/Flyway 日志 | 修改历史迁移、脏 schema、权限不足 |
| 开发者工具白屏 “Weixin” | 导入路径 | 导入旧模板/错误 dist |
| `webapi_getwxaasyncsecinfo:fail` | 工具登录模式 | 游客模式微信数据能力受限 |
| Gradle 构建失败 | `build.gradle.kts`、Gradle Wrapper 版本 | 依赖解析失败、Kotlin 守护进程卡死 |
| Kotlin 守护进程卡死或内存异常 | 编译日志 | 使用 `-Pkotlin.compiler.execution.strategy=in-process` 绕过 daemon |

排障时记录 `requestId`，不要记录 access token、refresh token 或微信 code。

## 8. 停止、重建与数据保护

普通停止：

```powershell
Set-Location (Join-Path $repoRoot 'Eligo-main\Eligo-main\infra\docker')
docker compose down
```

重建 API 不清数据库：

```powershell
docker compose up -d --build --force-recreate api
```

eligo-new 后端通过 `bootRun` 运行时，停止直接终止 Gradle 进程（Ctrl+C 或 IDE 停止按钮）。

删除卷是破坏性操作，只在确认本地数据全部可丢弃后执行：

```powershell
docker compose down -v
```

执行前应列出 `docker volume ls` 并确认只影响 `eligo-local` 对应卷。共享环境禁止用“删卷重来”代替迁移修复。

## 9. 为什么现在不配 Nginx

本机开发者工具直接访问 API 可以：

- 少一层反向代理变量；
- 直接看到 Spring HTTP 状态和 Result；
- 更快定位 CORS、路径和后端异常；
- 不需要域名和证书。

以下任一条件出现时再配置 Nginx/等价网关：

- 体验版或真机需要微信合法 request 域名；
- 前后端共享同一测试环境；
- 需要 HTTPS 证书终止；
- 需要统一访问日志、上传限制、超时、限流和 requestId 透传。

## 10. 共享测试环境准入（尚未实现）

在配置共享环境前必须完成：

- 域名主体、备案/解析和有效 TLS 证书；
- 微信公众平台 request/upload/download 合法域名；
- Nginx `/api/` 反代，仅暴露 443；
- MySQL、Redis 不暴露公网；
- Secret 使用环境/密钥管理，不进入镜像和 Git；
- 独立数据库与对象存储；
- 备份恢复演练；
- Flyway 发布前审查与回滚策略；
- API readiness、5xx、磁盘/数据库告警；
- 日志脱敏与保留周期；
- 测试账号、协议和兴趣等基础数据。

## 11. 未来发布流程建议（尚未实现）

```text
PR 合并 main
  → 后端测试（gradlew test）+ OpenAPI 契约
  → 前端 build:mp-weixin（eligo-main 仓库）
  → 构建不可变 API 镜像（gradlew build → Docker）
  → 备份数据库
  → 审核并执行迁移
  → 部署 API
  → readiness + smoke
  → 上传小程序体验版
  → 真机回归
  → 提交微信审核
```

在真正建立流水线前，不得把本节描述为现有自动发布能力。
