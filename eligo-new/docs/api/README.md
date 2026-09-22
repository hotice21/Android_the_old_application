# Eligo API 实现与联调台账（eligo-new / Kotlin 重写）

> 契约：[`openapi.yaml`](openapi.yaml)（eligo-new 内部副本，与 eligo-main 保持一致）
> 重写验证基线：713 个测试，失败 0，跳过 129
> 维护规则：后端 Kotlin 实现、测试或契约发生变化时，在同一提交更新本表。
> `✅` 有证据；`—` 未开始/不适用；`⛔` 前置条件阻断。

## 1. 当前总览

| 指标 | 数量 |
| --- | ---: |
| OpenAPI paths | 65 |
| HTTP operations | 80 |
| 后端已实现（Kotlin） | 80 |
| M4.1 已实现 | 1 |
| 仍待编码规划操作 | 0 |
| 前端已封装 | — |
| 页面显式接入 | — |
| 完整本地端到端通过 | — |
| 真机通过 | — |

eligo-new 是 eligo-main 后端的 Kotlin 重写，仅包含后端，不包含前端。全部 80 个 HTTP
操作均已在 Kotlin 中实现，覆盖 M0 到 M4.1 全部模块。前端封装、页面接入、本地联调和
真机验证均不适用于本子项目（标记为 `—`），相关证据以 eligo-main 的台账为准。

OpenAPI 契约由父级 `../docs/api/openapi.yaml`（相对 eligo-new 根目录）与 eligo-main
共享，二者保持 65 条路径、80 个 HTTP 操作一致。eligo-new 不单独维护 OpenAPI 契约，
仅验证 Kotlin 实现与契约一致。

## 2. 状态台账

eligo-new 只承担后端 Kotlin 实现。下表按模块汇总每个模块的全部操作状态：每个模块内
的全部 HTTP operation 均已实现后端 ✅，前端列统一为 `—`。各模块 HTTP operations 之和
为 80，与 OpenAPI 契约一致。

| 模块 | HTTP operations | 后端（Kotlin） | 前端封装 | 页面/调用者 | 本地联调 | 真机 | 说明 |
| --- | ---: | --- | --- | --- | --- | --- | --- |
| M0 | 1 | ✅ | — | — | — | — | `GET /api/v1/system/ping` |
| M1/Auth | 3 | ✅ | — | — | — | — | 微信登录、刷新、登出 |
| M1/Agreement | 3 | ✅ | — | — | — | — | 协议查询与同意 |
| M1/Profile | 6 | ✅ | — | — | — | — | 资料、头像、昵称、兴趣标签 |
| M1/Phone | 3 | ✅ | — | — | — | — | 手机号绑定/换绑/解绑 |
| M1/Session | 2 | ✅ | — | — | — | — | 会话查询与删除 |
| M1/Security | 1 | ✅ | — | — | — | — | 安全事件游标分页 |
| M1/Data | 6 | ✅ | — | — | — | — | 账号注销与数据导出 |
| M1/File | 4 | ✅ | — | — | — | — | 图片上传、元数据、删除、内容读取 |
| M2/Capability | 1 | ✅ | — | — | — | — | 用户能力查询 |
| M2/Organization | 2 | ✅ | — | — | — | — | 企业公开详情与本人企业 |
| M2/Activity | 13 | ✅ | — | — | — | — | 活动 CRUD、发布、取消、地图、二维码 |
| M3/Participation | 5 | ✅ | — | — | — | — | 报名、取消、参与者管理 |
| M3/Favorite | 4 | ✅ | — | — | — | — | 活动收藏 |
| M3/Comment | 3 | ✅ | — | — | — | — | 活动评论 |
| M4/Profile | 1 | ✅ | — | — | — | — | 公开资料与关注统计 |
| M4/Follow | 8 | ✅ | — | — | — | — | 用户/企业关注与状态 |
| M4/Post | 12 | ✅ | — | — | — | — | 动态草稿、发布、读取、删除 |
| M4/Feed | 1 | ✅ | — | — | — | — | 关注流 |
| M4.1/Recommendation | 1 | ✅ | — | — | — | — | 推荐流，功能开关默认关闭 |
| **合计** | **80** | **80 ✅** | **—** | **—** | **—** | **—** | M0–M4.1 全部后端实现 |

契约共 65 条路径、80 个 HTTP 操作，与 eligo-main 保持一致。契约边界与业务规则同样
与 eligo-main 一致，详见 eligo-main 的[角色规范](../../../Eligo-main/Eligo-main/docs/context/core/roles-and-permissions.md)
与各模块文档。

## 3. Kotlin 重写验证基线

eligo-new 的 Kotlin 重写以 JUnit 5 + Mockito-Kotlin 5.4.0 为测试框架，对全部模块进行
回归验证，确认 Kotlin 实现与 Java 版本行为等价。

| 指标 | 数量 |
| --- | ---: |
| 测试总数 | 713 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 129 |

验证范围覆盖：

- M0 系统健康检查与 OpenAPI 契约对齐；
- M1 认证、协议、资料、手机号、会话、安全事件、账号注销与数据导出、文件上传与读取；
- M2 能力查询、企业主体、活动创建/编辑/发布/取消/地图/二维码；
- M3 报名、取消、参与者管理、活动收藏、活动评论；
- M4 公开资料、用户/企业关注、动态草稿/发布/读取/删除、关注流；
- M4.1 推荐流（Ollama、Qdrant、MySQL、Redis 真实依赖联合验收，功能开关默认关闭）。

跳过的 129 项主要集中在外部依赖（微信、Ollama、Qdrant、Redis）未就绪或需要独立隔离
数据库的场景，不影响 Kotlin 重写的行为等价性结论。

运行测试：

```powershell
.\gradlew.bat test
```

Linux、macOS 或 WSL：

```bash
./gradlew test
```

## 4. 当前差距

- **无前端**：eligo-new 仅含后端 Kotlin 实现，前端封装、页面接入、本地联调和真机
  验证均不适用（`—`），以 eligo-main 台账为前端权威来源。
- **无 IT 联调记录**：本子项目不维护独立 IT 联调任务与证据，联调结果登记在
  eligo-main 的 `docs/integration/`。
- **共享 OpenAPI 契约**：接口契约由父级 `../docs/api/openapi.yaml`（相对 eligo-new
  根目录）与 eligo-main 共享，eligo-new 不单独维护契约文件，仅验证 Kotlin 实现与
  契约一致。
- **生成物不提交**：Gradle 构建产物位于 `build/`，已被 `.gitignore` 忽略；不出现
  Maven 的 `target/`、`pom.xml` 或 `mvnw` 残留。

## 5. 更新示例

若 Kotlin 重写新增或修正某个操作的实现：

1. Kotlin 代码、测试同提交；
2. 本表对应模块行更新说明（如“修正报名并发行为”）；
3. 重写验证基线数字（测试总数/失败/跳过）随 `.\gradlew.bat test` 结果同步更新；
4. 涉及契约变化时，同步修改 `docs/api/openapi.yaml` 并与 eligo-main 协调保持一致。

任何一步都不得提前批量打勾。
