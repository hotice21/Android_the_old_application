# 单仓库、目录职责与生成物（eligo-new / Kotlin 重写）

> eligo-new 是 eligo-main（原 Java 实现）的 Kotlin 重写版本，仅包含后端。
> 本项目可独立于 eligo-main 构建、测试与 Docker 部署。
> OpenAPI 契约副本位于 `docs/api/openapi.yaml`，与 eligo-main 保持一致。

## 1. 仓库结构

eligo-new 自身包含后端 Kotlin 实现、Docker 部署文件与 OpenAPI 契约副本，可独立运行。

```text
eligo-new/                        Kotlin 重写（本项目，可独立部署）
├── .gitignore
├── .dockerignore
├── .env.example                  环境变量模板
├── README.md                     新人启动入口
├── build.gradle.kts              Gradle 构建脚本（Kotlin DSL）
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat         Gradle Wrapper
├── Dockerfile                    多阶段构建镜像
├── docker-compose.yml            api + mysql + redis 编排
├── docs/
│   ├── api/
│   │   ├── openapi.yaml          OpenAPI 契约副本（与 eligo-main 一致）
│   │   └── README.md             接口实现台账
│   └── context/                  项目上下文文档
└── src/
    ├── main/kotlin/com/eligo/server/  后端源码
    └── test/kotlin/com/eligo/server/  测试源码
```

eligo-new 的源码根目录是 `src/main/kotlin/com/eligo/server/`，不再使用 Java 的
`src/main/java/`。构建脚本由 `build.gradle.kts`（Kotlin DSL）与 Gradle Wrapper 提供，
没有 Maven 的 `pom.xml` 或 `mvnw`。根目录不保留旧 React Native 工程、压缩包或
其他项目残留。OpenAPI 契约副本位于 `docs/api/openapi.yaml`，Kotlin 重写的实现与验证
状态以 `docs/api/README.md` 为准。

## 2. 历史来源

eligo-new 是 eligo-main 后端（原 Spring Boot + Java 实现）的 Kotlin 重写版本，目的
是在保持接口契约不变的前提下，用 Kotlin 2.1.21 重新实现后端。原 Java 实现仅供追溯，
eligo-new 不再依赖 eligo-main 的任何运行时资源。

## 3. 目录所有权

| 目录/文件 | 责任方 | 说明 |
| --- | --- | --- |
| `src/main/kotlin/com/eligo/server/` | 后端 | Kotlin 源码，按领域包划分 |
| `src/main/resources/` | 后端 | `application*.properties`、Flyway 迁移、行政区划 JSON |
| `src/test/kotlin/com/eligo/server/` | 后端 | Kotlin 测试源码 |
| `build.gradle.kts`、`settings.gradle.kts` | 后端 | Gradle 构建配置 |
| `Dockerfile`、`docker-compose.yml` | 后端 | Docker 部署配置 |
| `.env.example` | 后端 | 环境变量模板 |
| `docs/api/openapi.yaml` | 后端 | OpenAPI 契约副本 |
| `docs/context/` | 后端 | 项目上下文文档 |

## 4. 生成物规则

Gradle 构建产物位于 `build/`，已被 `.gitignore` 忽略。不得提交以下内容：

- `build/` 目录
- `.gradle/` 目录
- `.kotlin/` 目录
- `target/`、`pom.xml`、`mvnw` 等 Maven 残留
- `.env` 文件（仅提交 `.env.example` 模板）
- IDE 配置文件（`.idea/`、`.vscode/`、`*.iml`）

## 5. 允许进入仓库的配置

允许：

- `application.properties`、`application-local.properties`、`application-prod.properties`、`application-docker.properties`（不含真实密钥）
- `db/migration/V*__*.sql`（Flyway 迁移脚本）
- `docker-compose.yml`、`Dockerfile`
- `.env.example`（模板，不含真实值）

禁止：

- 真实 JWT 签名密钥、数据加密密钥
- 真实微信 AppSecret
- 真实数据库密码
- 任何 `.env` 文件

## 6. 分支和提交

- 主分支为 `main`。
- 后端功能分支命名：`feat/<模块>-<简短描述>`，例如 `feat/activity-contact`。
- 后端修复分支命名：`fix/<模块>-<简短描述>`，例如 `fix/wechat-token-cache`。
- 提交信息遵循 Conventional Commits：`feat:`、`fix:`、`refactor:`、`test:`、`docs:`、`chore:`。

## 7. 合并流

```text
feat/activity-contact --> 本地 gradlew test 通过 --> PR --> 代码审查 --> 合并到 main
```

合并前必须满足：

1. `.\gradlew.bat test` 全部通过（0 失败）
2. `OpenApiContractTest` 验证契约一致性通过
3. `ModuleBoundaryTest` 验证模块边界通过
4. 文档更新（如有接口或架构变更）

## 8. 仓库卫生检查

提交前确认以下命令无 Maven 残留：

```powershell
# 不应找到 Maven 文件
Get-ChildItem -Path . -Recurse -Include pom.xml,mvnw,mvnw.cmd | Where-Object { $_.FullName -notmatch '\\.gradle\\' }

# 构建产物目录应为 build/ 而非 target/
Test-Path .\build
```
