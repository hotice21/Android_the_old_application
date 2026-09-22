# Eligo Server (Kotlin)

eligo-new 是 eligo-main（原 Java 实现）的 Kotlin 重写版本，仅包含后端。本仓库不包含前端代码，且可独立于 eligo-main 构建、测试与部署。

项目以 eligo-main 的 OpenAPI 契约为唯一接口事实来源，保持与 Java 版本相同的 65 条路径、80 个 HTTP 操作。

## 项目概述

| 项 | 说明 |
| --- | --- |
| 语言 | Kotlin 2.1.21（JVM 目标 17） |
| 框架 | Spring Boot 4.1.0 |
| 构建 | Gradle（Kotlin DSL），通过 Gradle Wrapper 统一版本 |
| 数据库 | MySQL 8.4 |
| 缓存 | Redis 7.2 |
| 数据库迁移 | Flyway |
| ORM | MyBatis-Plus 3.5.16 |
| 认证 | Spring Security + JWT（HS256），OAuth2 Resource Server |
| 测试 | JUnit 5 + Mockito-Kotlin 5.4.0，共 713 个测试全部通过 |
| OpenAPI 契约 | 65 条路径、80 个 HTTP 操作，与 Java 版本一致 |
| 源码根目录 | `src/main/kotlin/com/eligo/server/` |
| 前端 | 本仓库不包含前端 |

## 目录结构

```text
eligo-new/
├── src/main/kotlin/com/eligo/server/
│   ├── account/            # 账号、登录、会话、手机号绑定、账号数据导出与注销
│   ├── activity/           # 活动（个人/企业）创建、编辑、发布、生命周期
│   ├── agreement/          # 用户协议与同意
│   ├── comment/            # 活动评论
│   ├── common/             # 统一 Result、游标分页、全局异常与错误码
│   ├── config/             # Spring Security、JWT、调度配置
│   ├── favorite/           # 活动收藏
│   ├── file/               # 文件上传、本地存储、图片审查与清理
│   ├── follow/             # 用户关注与企业关注
│   ├── integration/wechat/ # 微信登录、手机号、AccessToken 提供者
│   ├── organization/       # 企业主体与访问能力
│   ├── participation/      # 活动报名、取消与读取
│   ├── post/               # 动态草稿
│   ├── recommendation/     # 推荐
│   └── EligoServerApplication.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
├── gradlew                 # Linux、macOS、WSL 构建脚本
└── gradlew.bat             # Windows 构建脚本
```

## 构建与运行

### 方式一：本地快速启动（无需 MySQL / Redis，推荐先看效果）

项目内置了 `local-dev` profile，使用 H2 内存数据库，Redis 指向本地（不影响启动，仅调用相关功能时报错）。

Windows（PowerShell）：

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local-dev"
```

Linux、macOS 或 WSL：

```bash
./gradlew bootRun --args="--spring.profiles.active=local-dev"
```

启动成功后：
- 服务地址：`http://localhost:8081`
- 健康检查：`http://localhost:8081/actuator/health/readiness`（返回 `{"status":"UP"}`）
- H2 控制台：`http://localhost:8081/h2-console`（JDBC URL：`jdbc:h2:mem:eligo`，用户名 `sa`，密码空）

> 注意：`local-dev` profile 下 Flyway 禁用，H2 为内存空库。涉及数据库读写的接口会因表不存在而报错，仅用于验证应用能正常启动。需要完整数据库功能请用方式二。

### 方式二：连接真实 MySQL / Redis（完整功能）

Windows（PowerShell）：

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

Linux、macOS 或 WSL：

```bash
./gradlew clean build
./gradlew bootRun
```

`bootRun` 默认使用 `local` profile，会从被 Git 忽略的 `.env` 读取 MySQL、Redis 与端口等本地配置。可直接连接 Docker Compose 暴露到 `127.0.0.1` 的 MySQL 和 Redis。

`.env` 示例（放在项目根目录）：

```properties
MYSQL_USER=eligo
MYSQL_PASSWORD=your_password
MYSQL_DATABASE=eligo
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
JWT_SIGNING_KEY_BASE64=<base64 编码的 32 字节密钥>
DATA_ENCRYPTION_KEY_BASE64=<base64 编码的 32 字节密钥>
DATA_LOOKUP_KEY_BASE64=<base64 编码的 32 字节密钥>
```

## 使用 Trae 启动

1. 用 Trae 打开 `eligo-new` 目录
2. 打开终端，执行本地快速启动命令：
   ```powershell
   .\gradlew.bat bootRun --args="--spring.profiles.active=local-dev"
   ```
3. 看到 `Started EligoServerApplicationKt` 且 `Tomcat started on port 8081` 即启动成功
4. 浏览器访问 `http://localhost:8081/actuator/health/readiness` 验证

## 使用 Android Studio 打包 APK

Android 离线打包工程位于同级目录 `android-app`，采用 WebView 套壳方式加载内置 H5 产物。

### 前置条件
- JDK 17+
- Android Studio（Hedgehog 2023.1.1 或更新）
- Node.js（用于构建前端 H5 产物）

### 步骤

1. **构建前端 H5 产物**
   ```powershell
   cd ..\eligo-frontend
   npm install
   npm run build:h5
   ```

2. **同步 H5 产物到 Android assets**
   ```powershell
   cd ..\android-app
   .\sync-assets.ps1
   ```

3. **用 Android Studio 打开 `android-app` 目录**
   - 启动 Android Studio → Open → 选择 `android-app` 目录
   - 等待 Gradle Sync 完成（首次会下载 Gradle 和 Android SDK）

4. **打包 APK**
   - 方式 A（Android Studio 界面）：菜单 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - 方式 B（命令行）：
     ```powershell
     .\gradlew.bat assembleDebug
     ```

5. **APK 输出位置**：`android-app\app\build\outputs\apk\debug\app-debug.apk`

6. **安装到设备**：
   ```powershell
   adb install -r "app\build\outputs\apk\debug\app-debug.apk"
   ```

也可以一键执行（构建 H5 + 同步 + 打 APK）：

```powershell
cd android-app
.\build-apk.ps1
```

## 测试

Windows（PowerShell）：

```powershell
.\gradlew.bat test
```

Linux、macOS 或 WSL：

```bash
./gradlew test
```

测试套件基于 JUnit 5 与 Mockito-Kotlin 5.4.0，当前共 713 个测试全部通过。

## 文档链接

- [OpenAPI 契约](docs/api/openapi.yaml)
- [原 Java 项目（eligo-main）](../Eligo-main/Eligo-main/)
- [原 Java 后端 README](../Eligo-main/Eligo-main/eligo-server/README.md)
- [原项目根 README](../Eligo-main/Eligo-main/README.md)
- [项目上下文](../Eligo-main/Eligo-main/docs/context/index.md)
- [技术架构](../Eligo-main/Eligo-main/docs/context/core/tech-stack.md)
- [用户角色与权限](../Eligo-main/Eligo-main/docs/context/core/roles-and-permissions.md)
- [API 治理规则](../Eligo-main/Eligo-main/docs/context/core/api-governance.md)
- [API 实现与联调台账](../Eligo-main/Eligo-main/docs/api/README.md)
- [部署与联调](../Eligo-main/Eligo-main/docs/context/core/deployment.md)
- [测试指南](../Eligo-main/Eligo-main/docs/context/core/testing-guide.md)
