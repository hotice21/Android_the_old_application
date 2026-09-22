# 开发与编码约定

> 本文是合并审查规则，不是风格建议。
> 优先级：安全与正确性 > 契约一致性 > 可维护性 > 局部代码偏好。
> 本约定适用于 eligo-new（eligo-main 的 Kotlin 重写版本）。原 Java 版约定见 eligo-main 仓库 `docs/context/core/conventions.md`。

## 1. 通用数据约定

| 项目 | 约定 |
| --- | --- |
| JSON 字段 | `camelCase` |
| 数据库表/列 | `snake_case`，表名用复数 |
| Kotlin 类型 | 类 `PascalCase`，函数/属性 `camelCase`，常量 `UPPER_SNAKE_CASE` |
| ID | API 中使用字符串；后端可解析为 `Long` |
| 时间 | 数据库存 UTC；API 返回 ISO 8601 带时区；前端仅展示时转换 |
| 日期 | 无时间语义时使用 `YYYY-MM-DD` |
| 空值 | 缺失、`null`、空字符串含义必须在契约中明确，不互相替代 |
| 金额 | 未来支付使用最小货币单位整数，不用浮点数 |
| 枚举 | API 使用稳定大写字符串；数据库可用数值但 Service 负责映射 |

## 2. 后端包与依赖方向

每个领域包建议包含：

```text
<domain>/
├── controller/    HTTP 入口
├── dto/           请求对象
├── vo/            响应对象
├── service/       用例与事务
├── entity/        持久化/领域状态
└── mapper/        本领域数据访问
```

规则：

- Controller 不直接注入 Mapper。
- Mapper 不调用 Service。
- 一个模块不能直接调用另一个模块的 Mapper。
- 跨模块读取优先通过只读 Service/Port；跨模块副作用优先通过应用服务或领域事件。
- 外部微信、对象存储、内容安全放在 `integration` 或接口实现中，核心业务依赖抽象。
- 网络 I/O 和大文件 I/O 不长时间占用数据库事务。

Kotlin 包约定：源码根目录为 `src/main/kotlin/com/eligo/server/`，按领域顶层包组织（`account`、`activity`、`profile` 等），领域包内部统一使用 `controller`、`service`、`mapper`、`entity`、`dto`、`vo`、`error` 子包。领域对象（DTO、VO、Entity、配置属性）优先使用 `data class` 表达；Service 接口与默认实现以 `Default*Service` 命名（如 `DefaultAuthService`）。

## 3. Controller 与 HTTP

- 基础路径固定为 `/api/v1`。
- 当前用户只从 `@AuthenticationPrincipal UserPrincipal` 获取；受保护接口不得接受可伪造的 `userId` 作为本人身份。
- 请求 DTO 使用 Bean Validation；Service 仍验证领域条件。
- 创建资源通常返回 201；查询/幂等替换返回 200；当前统一空成功仍返回 Result，不自行改为无包裹 204。
- 文件内容和 Actuator 是统一 Result 的明确例外。
- 公开接口必须显式登记在 `SecurityConfig` 和 OpenAPI，默认一律认证。
- 管理接口同时验证路由角色和资源级权限。

统一响应：

```json
{
  "code": 0,
  "message": "成功",
  "data": {},
  "requestId": "脱敏追踪编号",
  "timestamp": "2026-07-29T10:00:00Z"
}
```

业务失败同时使用正确 HTTP 状态和业务码。前端不可只看 HTTP 200，也不可只看 `code` 而忽略 401/403 的会话语义。

Kotlin 特有约定：

- 受保护接口的 `@AuthenticationPrincipal` 参数声明为非空 `UserPrincipal`；允许匿名访问的公开接口必须声明为可空 `UserPrincipal?`，Service 侧再按是否登录走不同分支（如公开活动详情、公开动态）。
- 请求 DTO 使用 `data class`；字段校验注解必须使用 `@field:NotBlank`、`@field:Size`、`@field:Pattern` 等 `@field:` 前缀，不能只写 `@NotBlank`——Kotlin 数据类的主构造器参数注解默认不作用于字段，会跳过校验。
- 带校验的字段必须声明为可空类型（如 `val wechatCode: String?`），让 Bean Validation 能识别“缺失”并报错；非空类型字段在 Kotlin 中默认非空，框架无法触发 `@NotBlank`。
- 可选字段同样使用可空类型与默认值（如 `val description: String? = null`）。
- 需要从 JSON 序列化中隐藏的内部字段使用 `@get:JsonIgnore`，因为 Kotlin 数据类会生成 getter，Jackson 默认会通过 getter 发现并序列化该字段。

## 4. REST 与幂等

| 场景 | 方法 | 规则 |
| --- | --- | --- |
| 创建服务器分配 ID 的资源 | `POST` | 重试可能重复时要有幂等键/业务唯一键 |
| 建立唯一集合关系 | `PUT` | 关注、报名等重复调用保持一条有效关系 |
| 整体替换 | `PUT` | 兴趣列表等以请求全集替换 |
| 局部更新 | `PATCH` | 明确字段更新 |
| 删除/取消关系 | `DELETE` | 重复调用的预期状态要在契约中定义 |

列表规则：

- 稳定小型后台列表可以页码分页；
- 动态、关注流、安全事件等时间线使用游标分页；
- 游标是不透明字符串，前端不得解析；
- 默认 `limit=20`，最大值由接口明确，当前安全事件最大 100；
- 排序必须包含唯一 ID 作为稳定次序。

## 5. 事务与并发

- 事务边界放在 Service 用例，不放在 Controller。
- 先依赖数据库唯一约束保护最终结果，再用应用层给出友好错误。
- 名额扣减、状态转换使用条件更新、行锁或等价原子策略，不能先 count 再无锁 insert。
- 捕获唯一键冲突后转换为稳定业务码，不返回数据库异常细节。
- 事务内产生通知时，MVP 可同库写入；跨进程/外部推送必须使用 outbox 或提交后处理，不能造成主事务假成功。
- 已执行 Flyway 迁移只前进不改写；NOT NULL 新列先考虑默认值、回填和分阶段收紧。

## 6. 账号、安全与隐私

- access token、refresh token、微信 code、App Secret、手机号明文和加密密钥禁止写日志。
- refresh token 采用轮换与重放保护；并发 401 在前端只允许一次刷新。
- 微信 `code` 一次性使用，不缓存、不重试旧 code。
- 手机号等敏感数据使用加密值和独立 lookup key，三个密钥不得复用。
- 账号注销、导出、安全事件保留审计记录；不得物理删除审计事实。
- 用户公开资料使用专用 DTO，不返回“我的资料”中的手机号、完整地区或安全字段。
- UGC 动态上线前必须设计内容安全、举报/隐藏、审计和文件引用状态。

## 7. 前端目录与请求规则

eligo-new 不包含前端。前端目录、请求规则和小程序特有规则仍以 eligo-main 仓库为准（`eligo-frontend/` 与 `docs/context/core/conventions.md`）。需要补充或修改前端约定时，到 eligo-main 仓库维护，不在本仓库重复定义。

后端联调时需要参照的前端关键约束：

- 页面不得直接调用 `uni.request`；API 调用统一走 `src/api/` 封装。
- `Authorization` 只由请求层注入，不在页面写死。
- 401 single-flight 刷新；刷新失败清会话并回到登录入口。
- 业务错误展示 `message`，问题定位保留 `code` 和 `requestId`。
- 页面不得依赖 64 位数字运算处理业务 ID。

## 8. 测试约定

- 修复缺陷先补能复现的测试。
- Service 测业务规则、状态机和事务分支。
- Controller 测路由、校验、认证、HTTP 状态和 Result。
- MySQL 测唯一约束、迁移、并发和真实 SQL，不用内存库冒充。
- 安全接口至少有未登录 401、无权限 403、有效身份成功三个边界。
- OpenAPI 契约测试必须随接口变更通过。

Kotlin/Gradle/Mockito-Kotlin 特有约定：

- 全量测试使用 Gradle Wrapper 执行：`.\gradlew.bat test`（原 Java 版使用 `.\mvnw.cmd test`）。
- 测试源码放在 `src/test/kotlin/`，包结构与主源码 `com.eligo.server.*` 对齐。
- Mock 创建使用 `org.mockito.kotlin` 或 `org.mockito.Mockito.mock`；mockito-kotlin 5.4.0 已在 `build.gradle.kts` 中声明。
- 参数匹配器使用 `org.mockito.kotlin` 命名空间：`any<T>()`、`eq()`、`anyOrNull()`，不要混用 `org.mockito.ArgumentMatchers`（Kotlin 下与可空类型和最终类不兼容）。
- 打桩使用 mockito-kotlin 的 `whenever(...)`，避免使用 Mockito 的 `` `when`(...) ``（后者在 Kotlin 中是关键字，需反引号转义，可读性差）。
- 断言使用 AssertJ（`assertThat`、`assertThatThrownBy`）。
- `@AuthenticationPrincipal` 为可空的公开接口测试要覆盖匿名（principal 为 `null`）和已登录两种路径。

## 9. Git 与提交

- 根仓库是唯一 Git 边界；前后端目录不允许嵌套 `.git`。
- `main` 保持可发布；日常集成分支和 feature/fix 分支策略由团队统一执行。
- 一个提交完成一个可说明的行为变化；接口代码、OpenAPI、测试和相关文档不拆成互相漂移的提交。
- 禁止提交 `.env`、密钥、Token、证书、运行日志、上传文件、IDE 缓存、`build/`、`.gradle/`、`node_modules/` 和任何 `dist/`。
- 合并前执行 `git diff --check`，避免尾随空格和冲突标记。

## 10. 代码审查重点

### 后端

- 是否绕过 Principal 接受 `userId`；
- 是否跨模块直接访问 Mapper；
- 是否缺少事务/唯一约束/并发处理；
- 是否在日志或错误响应泄漏敏感数据；
- 是否遗漏 OpenAPI、错误码或迁移；
- 是否把外部调用放入长事务。

### Kotlin 特有

- DTO/VO 是否使用 `data class`，字段是否使用 `val`；
- 带校验的 DTO 字段是否声明为可空 `String?` 并使用 `@field:` 前缀注解；
- 允许匿名的 Controller 参数是否声明为 `UserPrincipal?`；
- 是否滥用 `!!`：仅在类型系统无法证明非空但业务逻辑保证非空时使用，优先用 `?.` 和 `?:` 处理空值；
- 是否把内部字段暴露给 Jackson 序列化（应使用 `@get:JsonIgnore`）；
- 测试是否使用 `org.mockito.kotlin` 的 `whenever` 与 `any<T>()`/`eq()`/`anyOrNull()`，而非 Mockito 原生 `when` 与 `ArgumentMatchers`。
