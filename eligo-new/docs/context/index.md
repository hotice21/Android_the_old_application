# eligo-new 项目上下文索引

> 文档版本：1.0
> 事实核对日期：2026-09-13
> 维护责任：技术负责人维护跨模块规则；各模块负责人维护模块事实；联调双方共同维护联调记录。

eligo-new 是 eligo-main（原 Java 实现）的 Kotlin 重写版本，仅包含后端。本目录是 eligo-new 的长期工程上下文。eligo-new 后续开发引用本目录、内部 `docs/api/` 以及原 Java 项目 `Eligo-main/Eligo-main/docs/context/` 中的跨模块文档。

eligo-main 的核心约定、产品边界、角色规范与模块领域文档仍以原 Java 项目为准；eligo-new 在此前提下记录 Kotlin 重写的事实与偏差。

## 1. 状态词必须按证据使用

| 状态 | 可以写入的条件 | 不代表 |
| --- | --- | --- |
| `规划` | 只有目标、规则或建议接口，代码中不存在 | 后端已经可调用 |
| `后端已实现` | Controller、Service、持久化和后端测试均可定位，OpenAPI 已登记 | 前端已经使用 |
| `前端已封装` | 原项目 `src/api/` 中有正式请求函数 | 页面已经调用 |
| `页面已接入` | 原项目页面或 store 已调用正式 API，不再用该能力的 mock | 真实环境已经成功 |
| `本地已验证` | 有日期、环境、请求顺序和结果的联调记录 | 微信真机或共享环境已通过 |
| `真机已验证` | 非游客模式或体验版真机验收完成并记录证据 | 生产已发布 |

eligo-new 不含前端，`前端已封装`、`页面已接入`、`本地已验证`、`真机已验证` 等前端相关状态以原 Java 项目的客户端与联调记录为准，查看 [原项目 API 实现与联调台账](../../../Eligo-main/Eligo-main/docs/api/README.md)。

任何文档不得用"已完成"同时代替上述多个阶段。接口的当前状态统一查看 [API 实现与联调台账](../../../Eligo-main/Eligo-main/docs/api/README.md)。

## 2. 产品边界

eligo-new 是 Eligo 后端的 Kotlin 重写，与原 Java 版本共享同一产品边界与同一份 OpenAPI 契约。Eligo 整体是以线下活动为核心的微信小程序。当前 MVP 允许游客先浏览，再按操作升级身份，并区分个人和企业发布：

```text
游客浏览公开活动/动态
  → 点击发布、报名或关注
  → 微信登录并完善资料（当前无正式协议）
  → 成为认证个人
  → 个人发布活动/动态，或免费报名
  → 可选：后端预置唯一企业测试主体
  → 企业发布活动/动态
  → 免费报名或取消
  → 参与活动
  → 关注用户或组织
  → 在关注流查看动态
  → 接收业务系统通知
```

四类产品身份、权限矩阵、个人/企业发布的权威规则见
[用户身份、权限与发布流程规范](../../../Eligo-main/Eligo-main/docs/context/core/roles-and-permissions.md)。当前只做本地开发：没有正式协议、企业申请、人工审核和管理员页面。

当前明确不做：

- 私信、群聊、活动临时群、WebSocket IM、已读回执；
- 付费报名、退款、商户结算、平台抽佣；
- 点赞；
- 把"系统通知"包装成用户之间的消息发送能力。

活动收藏与活动评论已由 M3 扩展实现（见 [D-021](../../../Eligo-main/Eligo-main/docs/context/core/decisions.md)），不属于上述"明确不做"。

支付是未来可能的独立能力，当前只做边界预留，详见 [M6 支付边界预留](../../../Eligo-main/Eligo-main/docs/context/modules/m6-payment-reservation.md)。

## 3. 当前事实摘要

| 领域 | 当前事实 | 证据入口 |
| --- | --- | --- |
| 仓库 | eligo-new 仅含后端，是 eligo-main（原 Java）的 Kotlin 重写；自带 Docker 部署、OpenAPI 契约副本与上下文文档，可独立于 eligo-main 运行 | [eligo-new README](../../README.md)、[仓库结构](core/repository.md) |
| 后端 | Kotlin 2.1.21 + Spring Boot 4.1.0 + Gradle（Kotlin DSL）；M0/M1、M2 活动（含关键词/地点/坐标系/距离/半径/话题）、M3 报名/收藏/评论、M4 关注/动态、M4.1 推荐共 80 个 HTTP 操作已实现，与 Java 版本一致；65 条 OpenAPI 路径 | [OpenAPI 契约](../../api/openapi.yaml)、[M2](../../../Eligo-main/Eligo-main/docs/context/modules/m2-organization-activity-review.md)、[M3](../../../Eligo-main/Eligo-main/docs/context/modules/m3-participation.md) |
| 测试 | JUnit 5 + Mockito-Kotlin 5.4.0；713 个测试全部通过，129 个被跳过；0 失败、0 错误 | [eligo-new README](../../README.md)、[测试指南](core/testing-guide.md) |
| 数据 | Flyway 迁移 V1–V18，与 Java 版本一致；兴趣标签 10 条；协议 0 条 | [部署指南](core/deployment.md)、`src/main/resources/db/migration/` |
| 存储 | MySQL 8.4 + Redis 7.2；ORM 为 MyBatis-Plus 3.5.16；认证为 Spring Security + JWT（HS256）OAuth2 Resource Server | [eligo-new README](../../README.md)、[技术架构](core/tech-stack.md) |
| 联调 | 前端、客户端与端到端联调在原 Java 项目跟踪；eligo-new 仅提供后端契约与实现 | [IT-001](../../../Eligo-main/Eligo-main/docs/integration/IT-001-miniapp-auth-onboarding.md)、[IT-002](../../../Eligo-main/Eligo-main/docs/integration/IT-002-role-publishing-participation.md) |
| 角色闭环 | 四类身份、本地单账号企业、两套发布页和直接报名规则与原项目一致 | [角色规范](../../../Eligo-main/Eligo-main/docs/context/core/roles-and-permissions.md) |
| 网关 | 当前本地开发不使用 Nginx；真机/共享环境前再配置 HTTPS 网关 | [D-004](../../../Eligo-main/Eligo-main/docs/context/core/decisions.md) |

## 4. 架构与模块

eligo-new 采用模块化单体，源码根目录为 `src/main/kotlin/com/eligo/server/`。交付模块与代码包不要求一一对应；一个交付模块可以包含若干边界清晰的业务包。下表将 Kotlin 包映射到原 Java 项目的业务模块。

| 模块 | 当前阶段 | Kotlin 包与责任 |
| --- | --- | --- |
| [M0 平台基础](../../../Eligo-main/Eligo-main/docs/context/modules/m0-foundation.md) | 后端已实现 | `common`（统一 `Result`、游标分页、全局异常、错误码、请求追踪）、`config`（Spring Security、JWT、调度）、Flyway 迁移、健康检查 |
| [M1 账号与用户基础](../../../Eligo-main/Eligo-main/docs/context/modules/m1-identity-profile.md) | 后端已实现 | `account`（账号、登录、会话、刷新令牌轮换与重放防护、手机号绑定、账号数据导出/注销）、`agreement`（协议与同意）、`file`（文件上传、本地存储、图片审查、清理）、`integration/wechat`（微信登录、手机号、AccessToken 提供者） |
| [M2 企业主体、活动与发布](../../../Eligo-main/Eligo-main/docs/context/modules/m2-organization-activity-review.md) | 后端已实现 | `organization`（本地单账号企业、访问能力、开发种子）、`activity`（活动生命周期、关键词/地点/坐标系/距离/半径/话题、幂等墓碑、维护任务） |
| [M3 报名、收藏与评论](../../../Eligo-main/Eligo-main/docs/context/modules/m3-participation.md) | 后端已实现 | `participation`（报名、取消、名额、参与者列表、本人参与记录、活动取消联动）、`favorite`（活动收藏、账号注销联动）、`comment`（活动评论） |
| [M4 关注与动态](../../../Eligo-main/Eligo-main/docs/context/modules/m4-follow-feed.md) | 后端已实现 | `follow`（关注用户/组织、作者主页、关注流）、`post`（动态草稿、状态事件） |
| [M4.1 推荐](../../../Eligo-main/Eligo-main/docs/context/modules/m4-post-recommendation.md) | 后端已实现 | `recommendation`（向量索引、推荐流、回填任务、嵌入与向量存储客户端、游标编解码、遥测） |
| [M5 系统通知](../../../Eligo-main/Eligo-main/docs/context/modules/m5-notification.md) | 规划 | 报名和活动变更产生的站内通知（未实现） |
| [M6 支付边界预留](../../../Eligo-main/Eligo-main/docs/context/modules/m6-payment-reservation.md) | 未立项 | 为未来支付/退款/结算保留独立边界，当前无 API |

推荐推进顺序与原项目一致：`M0/M1 联调收口 → M2/M3 前端与 IT-002 → M4 前端接入 → M5`。M6 不进入当前排期。

## 5. 文档阅读路径

开始任何任务前按以下顺序读取。eligo-new 自身维护本索引、README、核心文档与 OpenAPI 契约副本；模块领域文档、客户端文档与联调文档在原 Java 项目中。

1. 本索引；
2. [eligo-new README](../../README.md)（Kotlin 技术版本、构建与运行、测试命令）；
3. [技术架构](core/tech-stack.md)（架构边界与运行形态）；
4. [开发约定](../../../Eligo-main/Eligo-main/docs/context/core/conventions.md)；
5. 涉及身份、企业、发布或参与时读取 [角色规范](../../../Eligo-main/Eligo-main/docs/context/core/roles-and-permissions.md)；
6. 与任务相关的 [模块文档](../../../Eligo-main/Eligo-main/docs/context/modules/) 和 [客户端文档](../../../Eligo-main/Eligo-main/docs/context/clients/)；
7. 涉及接口时读取 [API 治理](../../../Eligo-main/Eligo-main/docs/context/core/api-governance.md)、[OpenAPI 契约](../../api/openapi.yaml) 和 [接口台账](../../api/README.md)；
8. 涉及联调时读取对应 `Eligo-main/Eligo-main/docs/integration/IT-*.md`；
9. 仅在接手未完成任务时读取 [当前任务](../../../Eligo-main/Eligo-main/docs/context/task-tracker.md)。

## 6. 文档职责矩阵

| 文档 | 只记录什么 | 何时必须更新 |
| --- | --- | --- |
| `eligo-new/README.md` | Kotlin 版本、依赖、构建、测试命令、目录结构 | Kotlin 版本、依赖或构建方式变化 |
| `eligo-new/docs/context/index.md` | eligo-new 的 Kotlin 重写事实与模块映射 | 重写范围、测试规模或模块边界变化 |
| `docs/api/openapi.yaml`（eligo-new 内部副本） | 当前已实现 HTTP 契约（65 条路径、80 个操作） | 后端接口任何增删改 |
| `Eligo-main/.../core/tech-stack.md` | 技术版本、运行形态、架构边界 | 更换框架、运行时、存储或架构模式 |
| `Eligo-main/.../core/conventions.md` | 可执行的编码和契约规则 | 团队约定或公共协议变化 |
| `Eligo-main/.../core/api-governance.md` | API 生命周期、状态和同提交规则 | 接口治理流程变化 |
| `Eligo-main/.../core/infrastructure.md` | 服务、网络、卷、配置、外部依赖 | Compose、端口、配置或存储变化 |
| `Eligo-main/.../core/deployment.md` | 本地启动、验证、排障和未来环境门槛 | 启动/构建/发布方式变化 |
| `Eligo-main/.../core/testing-guide.md` | 测试层次、命令和质量门槛 | 测试工具、流水线或验收规则变化 |
| `Eligo-main/.../core/decisions.md` | 已决定且跨模块的长期决策 | 决策新增、取代或废弃 |
| `Eligo-main/.../core/roles-and-permissions.md` | 四类产品身份、能力、个人/企业发布和审核反馈 | 身份、认证、权限或发布流程变化 |
| `Eligo-main/.../modules/*.md` | 领域模型、规则、接口状态、测试 | 模块行为、数据或 API 变化 |
| `Eligo-main/.../clients/*.md` | 客户端结构、页面、状态管理和接入状态 | 页面或客户端请求链变化 |
| `Eligo-main/.../docs/api/README.md` | 后端到前端再到验收的状态台账 | 封装、页面接入或联调状态变化 |
| `Eligo-main/.../docs/integration/IT-*.md` | 一条可复验的端到端任务 | 开始联调、发现阻断、完成某级验收 |
| `Eligo-main/.../task-tracker.md` | 尚未完成且需要交接的工作 | 优先级、负责人或阻断变化 |

## 7. 合并前文档门禁

只要改动涉及 API，合并前必须同时满足：

- OpenAPI 与实际 Controller/DTO 一致；
- 对应模块文档的接口和业务规则已更新；
- `Eligo-main/.../docs/api/README.md` 的状态没有虚报；
- 后端契约测试通过（eligo-new 测试套件 0 失败、0 错误）；
- 若前端已调用，API 封装与客户端文档已更新；
- 若宣称联调完成，存在对应 IT 文档、环境和验收结果。
- 身份/发布改动与角色规范和角色任务 CSV 一致，没有残留"只做个人"或"企业账号单独登录"的冲突。

详细清单见 [接口变更检查单](../../../Eligo-main/Eligo-main/docs/context/templates/api-change-checklist.md)。
