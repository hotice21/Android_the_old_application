# 测试与验收指南

> 目标：每个“已完成”都能关联到可执行测试或可复验联调记录。
> 事实核对日期：2026-09-13。
> 适配版本：eligo-new Kotlin 重写版（Kotlin 2.1.21 / Gradle Kotlin DSL）。

## 1. 当前测试资产

eligo-new 是后端单仓项目（无前端），测试源位于 `src/test/kotlin/com/eligo/server/`，使用 **JUnit 5 + Mockito-Kotlin 5.4.0 + AssertJ + Spring Boot Test** 技术栈，当前有 100+ 个 `*Test(s).kt` 测试源文件，覆盖与原 Java 版相同的业务域（M0–M4.1）：

- 公共 Result、异常和请求追踪；
- JWT 签发/校验、会话校验、账号限制；
- 微信登录、手机号、微信 access token 客户端；
- refresh 轮换、重放保护和并发；
- 手机号换绑事务和并发；
- 协议查询、同意和并发；
- 资料、昵称、兴趣、头像；
- 文件上传、内容、持久化、清理和限制；
- 注销、数据导出和安全事件；
- M2 企业、活动草稿、发布、取消、到期结束、图片访问和创建幂等；
- M3 报名、本人取消/重新报名、发起者移除、参与查询、名额并发和活动取消联动；
- M3 活动收藏与活动评论（含幂等、软删除、注销清理）；
- 注销 blocker、用户状态锁、M2/M3 跨模块事务和模块依赖边界；
- V1 至 V18 迁移和 MySQL 约束；
- M2 关键词、地点名称、坐标系、距离/半径与话题查询；
- M4 关注、动态、关注流与作者主页；
- M4.1 推荐文本、游标、精排、快照分页、依赖降级和推荐契约；
- OpenAPI 与 Controller 契约。

专项契约与边界测试（Kotlin 源文件）：

- `contract/OpenApiContractTest.kt`：OpenAPI 文档与 Controller 路由/方法/公开性一致性。
- `contract/ModuleBoundaryTest.kt`：模块依赖边界静态校验。

项目无前端，因此不包含前端自动测试与前端构建门槛；验收阶段的端到端联调仍按第 6 节标准记录。

## 2. 测试金字塔

| 层次 | 目的 | 运行频率 |
| --- | --- | --- |
| 单元测试 | 纯规则、转换、校验、错误码 | 每次提交 |
| Spring 切片/集成测试 | HTTP、认证、序列化、Result | 每次接口变更 |
| MySQL 边界测试 | 真实 SQL、迁移、约束、并发 | 数据/事务变更 |
| OpenAPI 契约 | 路由/方法/公开性与文档一致 | 每次接口变更 |
| 模块边界测试 | 跨模块依赖与事务边界 | 每次提交 |
| IT 联调 | 前后端真实调用链 | 每个联调任务 |
| 真机验收 | 微信权限、域名、设备差异 | 体验版/发布前 |

## 3. 后端命令

eligo-new 使用 Gradle（Kotlin DSL）构建，对应原 Java 版的 Maven 命令全部替换为 `gradlew` / `gradlew.bat`。

Windows：

```powershell
$repoRoot = git rev-parse --show-toplevel
Set-Location (Join-Path $repoRoot 'eligo-new')
.\gradlew.bat test
```

Linux / Mac：

```bash
./gradlew test
```

只运行 OpenAPI 契约：

```powershell
.\gradlew.bat test --tests "com.eligo.server.contract.OpenApiContractTest"
```

只运行单个测试类（Gradle `--tests` 接受全限定类名）：

```powershell
.\gradlew.bat test --tests "com.eligo.server.account.service.AuthServiceTests"
```

同时运行多个测试类（重复 `--tests`）：

```powershell
.\gradlew.bat test --tests "com.eligo.server.account.service.AuthServiceTests" --tests "com.eligo.server.account.controller.AuthControllerIntegrationTests"
```

当 Kotlin 守护进程出现卡死、内存或编译异常时，使用 in-process 编译策略绕过 daemon：

```powershell
.\gradlew.bat test -Pkotlin.compiler.execution.strategy=in-process
```

`test` task 默认通过 `useJUnitPlatform()` 启用 JUnit 5，运行态排除 DataSource 和 Redis，可安全运行而不接个人业务库。

## 4. MySQL 集成测试

带 `@EnabledIfSystemProperty(named="eligo.database.tests", matches="true")` 的测试只有显式开启才运行，与原 Java 版开关一致。

要求：

- 独立数据库，例如 `eligo_stage2_test`；
- 不与日常 `eligo` 共用 schema；
- 测试账号只有该测试库权限；
- 运行前确认 `application-it.properties`；
- 测试清理器只能清测试库。

示意命令（Gradle 通过 `-D` 传递系统属性）：

```powershell
.\gradlew.bat test -Deligo.database.tests=true
```

当前 `application-it.properties` 默认目标为 `127.0.0.1:13306/eligo_stage2_test` 且 root 密码为空，只有开发者显式准备对应隔离实例才可运行。不能为了“让测试通过”改指向本地 Compose 的 `eligo` 业务库。

专项条件测试必须同时启用数据库测试安全开关和自身开关：

| 测试组 | 自身开关 | 额外要求 |
| --- | --- | --- |
| 鉴权并发 | `eligo.auth.concurrency.tests=true` | 注入固定非生产 JWT、加密和 lookup 测试密钥 |
| 手机号并发 | `eligo.phone-binding.concurrency.tests=true` | 无 |
| 协议同意并发 | `eligo.agreement.concurrency.tests=true` | 无 |
| 协议迁移原子性 | `eligo.agreement.migration.atomicity.tests=true` | 使用独立 `eligo_stage2_atomic_test`，并设置 `eligo.agreement.migration.atomicity.url` |
| 活动/参与数据库与并发 | 无额外开关 | 随 `eligo.database.tests=true` 运行，必须使用重建后的隔离 schema |

上述专项组不要在同一个已产生测试数据的 schema 中一次性全部开启。每组运行前重建明确命名的隔离测试 schema，防止测试顺序和残留数据互相影响。测试清理器还会校验 `eligo.database.tests=true`，缺少该开关时拒绝清库属于预期安全行为。

## 5. API 变更最低用例

| 维度 | 必测 |
| --- | --- |
| 认证 | 未登录 401、有效 token 成功、无角色 403（如适用） |
| 参数 | 缺失、长度、类型、枚举、边界值 |
| 所有权 | A 用户不能读写 B 用户资源 |
| 状态 | 合法转换、重复请求、非法转换 409 |
| 并发 | 唯一关系、名额、刷新、协议等竞争 |
| HTTP | 方法、路径、Content-Type、200/201 |
| 契约 | OpenAPI schema、security、错误响应 |
| 日志 | requestId 存在、敏感值未输出 |

## 6. 联调证据标准

每个 `docs/integration/IT-*.md` 至少记录：

- 日期与环境（开发者工具/真机/共享环境）；
- API/前端构建版本或 Git commit；
- 必要的基础数据状态；
- 实际请求顺序；
- 每个验收项结果；
- 失败时的业务码和脱敏 requestId；
- 阻断归属与下一动作。

截图可以作为辅助证据，但不能替代上述文本。Token、微信 code、手机号和 Secret 必须遮挡。

## 7. 当前验证基线

截至 2026-09-13：

- 代码基线：eligo-new Kotlin 重写版；
- 构建工具：Gradle（Kotlin DSL），`build.gradle.kts`；
- 语言版本：Kotlin 2.1.21，`apiVersion`/`languageVersion` 固定为 `KOTLIN_2_1`，JVM target 17；
- 测试框架：JUnit 5（`useJUnitPlatform()`）+ Mockito-Kotlin 5.4.0 + AssertJ + Spring Boot Test；
- 测试资产：100+ 个 `*Test(s).kt` 测试源文件，位于 `src/test/kotlin/com/eligo/server/`；
- 契约范围：OpenAPI 文档与 Controller 一致性由 `OpenApiContractTest.kt` 校验；
- 模块边界：`ModuleBoundaryTest.kt` 包含在全量测试中；
- 运行态：`test` task 默认排除 MySQL/Redis，未开启 MySQL 条件组。

| 验证项 | 结果 | 摘要 |
| --- | --- | --- |
| 后端全量测试 | 通过 | 713 项，0 失败，129 项跳过 |
| OpenAPI 契约 | 通过 | `OpenApiContractTest.kt` 与 Controller/公开性基线一致 |
| 模块边界 | 通过 | `ModuleBoundaryTest.kt` 包含在全量测试中 |
| MySQL 迁移、约束与并发条件组 | 默认跳过 | 需独立 MySQL 8.4 schema 和显式安全开关 |
| Kotlin/Gradle 构建环境门禁 | 通过 | Kotlin 2.1.21，Gradle Kotlin DSL，JVM 17 |

结论：eligo-new 在普通测试、HTTP/OpenAPI 契约和静态模块边界范围内通过；该结果不替代隔离 MySQL 条件测试或端到端联调。

## 8. 技术负责人质量门槛

合并接口功能前：

- 相关测试通过；
- OpenAPI 同步；
- API 台账状态真实；
- 不降低已有安全测试；
- 迁移通过独立库验证；
- 页面接入则有 IT 记录；
- 未验证项明确写出。

发布体验版前再加：

- 真实微信登录；
- 合法域名/HTTPS；
- 多设备会话；
- 网络失败和恢复；
- 文件上传；
- 隐私协议版本；
- 真机主链路回归。
