# CapCut 项目账号报白与回填设计

日期：2026-09-13

更新：2026-09-14

状态：设计草案，待确认，尚未实施

## 1. 本期范围

本期只实现 CapCut 项目的人工账号资料闭环：推广用户分别提交 TikTok 账号报白资料和 CapCut 账号回填资料，管理员在本系统中分别维护每条记录的当前状态，推广用户查看结果。

TikTok 账号和 CapCut 账号共用一张新的 CapCut 项目账号表，但每次填写分别创建独立记录。两类账号不在同一表单中提交、不互相绑定，状态也互不影响。

本期账号流程与现有 GoodShort 媒体账号报白分开存储、分开提供接口和页面。两套业务只统一状态名称和页面展示文案，不复用 GoodShort 的 Provider、连接、报白任务或 Worker。

## 2. 已确认规则

- TikTok 账号走人工报白；CapCut 账号由推广用户回填，两种账号分别填写和提交。
- TikTok 报白记录和 CapCut 回填记录各自维护状态，不建立关联关系，也不要求先提交另一种账号。
- 两种账号的新记录都直接进入 `PENDING`，不存在 `NOT_SUBMITTED`。
- 状态只有 `PENDING`、`APPROVED`、`REJECTED`，不存在 `SUBMIT_FAILED`。
- 两种账号的地区都固定使用 `US`、`EU`、`ROW` 三个业务枚举值。
- 不保存审核人。
- 不保存拒绝原因。
- 只保留当前状态和更新时间，不提供审核历史。
- 不修改现有 GoodShort 媒体账号、报白记录、状态机和页面行为。

状态及用户端文案：

| 状态 | 用户端文案 | 含义 |
| --- | --- | --- |
| `PENDING` | 审核中 | TikTok 报白或 CapCut 回填已提交，等待管理员处理 |
| `APPROVED` | 已加白 | 当前记录状态为通过 |
| `REJECTED` | 未通过 | 当前记录状态为未通过 |

账号地区及页面文案：

| 地区值 | 页面文案 | 业务范围 |
| --- | --- | --- |
| `US` | 美国 | 美国 |
| `EU` | 英法德 | 英国、法国、德国 |
| `ROW` | 其他地区 | 除美国、英国、法国、德国以外的其他地区 |

`EU` 是本业务的固定分组代码，不根据现实中的欧盟成员关系动态调整。

## 3. 非目标

- 不接入 TikTok 或 CapCut 官方报白 API。
- 不实现自动提交、自动查询、失败重试或定时任务。
- 不实现 CapCut 订单同步、UID 订单归因或未匹配订单处理。
- 不实现 CPA、CPM、佣金、账单、钱包或提现。
- 不建立 TikTok 账号与 CapCut 账号的关联表。
- 不把现有 GoodShort 报白表改造成通用报白表。
- 不根据推广项目名称判断项目是不是 CapCut。
- 不在本期把 CapCut 项目账号记录关联到 `promotion_project`。现有 `promotion_project_type` 的 `CPA/CPM/CPS` 只表示计费分类，不能唯一标识 CapCut 项目。

## 4. 数据结构

新增独立表 `promotion_capcut_project_account`。一行只表示一次 TikTok 报白或一次 CapCut 回填，不同时保存两类账号资料：

```text
promotion_capcut_project_account
- id                       自增主键
- user_id                  推广用户内部 ID，逻辑关联
- account_type             TIKTOK / CAPCUT
- account_name             TIKTOK 账号名称
- account_region           账号地区
- capcut_id                CapCut ID
- capcut_uid               CapCut UID
- status                   当前记录状态：PENDING / APPROVED / REJECTED
- created_at               提交时间
- updated_at               最近更新时间
```

不增加 `reviewed_by`、`reviewed_at`、`reject_reason`、`last_error_code`、`last_error_message`、任务或租约字段。

字段规则：

| 账号类型 | 必填字段 | 必须为空的字段 |
| --- | --- | --- |
| `TIKTOK` | `account_name`、`account_region` | `capcut_id`、`capcut_uid` |
| `CAPCUT` | `capcut_id`、`capcut_uid`、`account_region` | `account_name` |

- 所有文本保存前去除首尾空白，去除后不得为空。
- `account_name`、`capcut_id`、`capcut_uid` 最大 128 个字符；`account_region` 最大 32 个字符。
- `account_region` 只允许 `US`、`EU`、`ROW`；前端使用固定选项，后端必须再次校验。
- `capcut_uid` 建立唯一约束，一个 CapCut UID 在系统中只能存在一条记录。
- TikTok 账号名称不作为全局唯一标识，允许同一用户提交多条 TikTok 账号。
- 表不建立物理外键，沿用当前生产 schema 的逻辑关联规则。
- 删除推广用户前必须检查该用户是否存在 CapCut 项目账号记录；存在时禁止物理删除，避免产生孤儿数据。
- 生产环境只通过新增 Flyway 迁移建表；开发重建脚本同步维护相同最终结构。

`created_at` 直接作为提交时间，`updated_at` 作为状态更新时间，不再增加含义重复的时间字段。

## 5. 用户端接口

```http
GET  /api/user/promotion/capcut/accounts
POST /api/user/promotion/capcut/accounts
```

### 5.1 查询本人记录

`GET` 只返回当前登录用户自己的记录，按 `created_at DESC, id DESC` 排序。

响应字段：

```json
{
  "id": 1,
  "accountType": "CAPCUT",
  "accountName": null,
  "accountRegion": "US",
  "capcutId": "123456",
  "capcutUid": "789012",
  "status": "PENDING",
  "createdAt": "2026-09-13T10:00:00",
  "updatedAt": "2026-09-13T10:00:00"
}
```

### 5.2 分别提交账号

用户端提供两个独立表单。每次只能提交一种账号资料，每次请求只创建一条独立记录；共享接口通过 `accountType` 区分 TikTok 报白和 CapCut 回填。

TikTok 账号报白请求：

```json
{
  "accountType": "TIKTOK",
  "accountName": "账号名称",
  "accountRegion": "US"
}
```

CapCut 账号回填请求：

```json
{
  "accountType": "CAPCUT",
  "capcutId": "123456",
  "capcutUid": "789012",
  "accountRegion": "US"
}
```

后端必须按 `accountType` 校验条件字段，不能只依赖前端隐藏无关输入。创建成功后该记录的状态固定为 `PENDING`，客户端不能指定状态。一种账号的提交和状态变化不得创建、修改或要求另一种账号记录。

用户端不提供编辑和删除接口。资料填错时由管理员删除本地记录，用户再重新提交；仅状态判断错误时由管理员直接纠正状态。

## 6. 管理端接口

```http
GET    /api/admin/promotion/capcut/accounts?page=1&size=20&userNo=&accountType=&status=&keyword=
PATCH  /api/admin/promotion/capcut/accounts/{id}/status
DELETE /api/admin/promotion/capcut/accounts/{id}
```

普通管理员和超级管理员均可访问，与现有 GoodShort 人工报白管理权限保持一致。

### 6.1 分页查询

列表返回推广用户编号、昵称、账号类型、对应账号资料、地区、状态、提交时间和更新时间。

- `userNo`：精确筛选推广用户编号。
- `accountType`：筛选 `TIKTOK` 或 `CAPCUT`。
- `status`：筛选三种状态。
- `keyword`：匹配账号名称、CapCut ID 或 CapCut UID。
- 默认按 `created_at DESC, id DESC` 排序。

### 6.2 更新状态

请求体只允许：

```json
{ "status": "APPROVED" }
```

或：

```json
{ "status": "REJECTED" }
```

状态规则与当前 GoodShort 人工报白一致：

- `PENDING -> APPROVED`
- `PENDING -> REJECTED`
- `APPROVED -> REJECTED`
- `REJECTED -> APPROVED`
- 管理员不能把记录改回 `PENDING`。
- 请求状态与当前状态相同时不执行更新，并返回状态操作不允许的业务错误。

状态更新只修改 `status` 和 `updated_at`，不记录审核人和原因。

### 6.3 删除

管理员可以删除任意状态的本地记录。删除只影响本系统，不代表 CapCut 或 TikTok 甲方侧撤销加白。前端删除前必须明确提示这一点并要求确认。

## 7. 页面设计

### 7.1 用户端

新增独立菜单和路由：

```text
菜单：CapCut 报白
路由：/workspace/capcut-filing
```

页面包含两个独立切换项和表单：

```text
TikTok 账号报白
- 账号名称
- 账号地区

CapCut 账号回填
- CapCut ID
- CapCut UID
- 账号地区
```

两个表单分别提交，不会在一次请求中同时保存 TikTok 和 CapCut 资料。页面下方显示本人提交的两类独立记录及各自的三种状态。不显示“关联 TikTok 账号”“绑定 CapCut 账号”等控件，也不要求先提交另一种账号。

### 7.2 管理端

新增独立菜单和路由：

```text
菜单：推广管理 / CapCut 报白
路由：/promotion/capcut-filing
```

页面提供分页列表、用户编号/账号类型/状态/关键词筛选、状态更新和删除操作。账号类型明确显示“TikTok 账号报白”或“CapCut 账号回填”；每条记录的状态独立更新。页面不显示审核人、拒绝原因、报白方式、外部报备编号、失败原因、重试按钮或任务信息。

## 8. 错误行为

- CapCut UID 已存在：返回明确的 UID 重复业务错误。
- 条件字段不符合账号类型：返回参数校验错误。
- 账号地区不是 `US`、`EU`、`ROW`：返回参数校验错误。
- 记录不存在：返回 CapCut 项目账号记录不存在业务错误。
- 用户越权查询：用户接口通过 `user_id` 限定，不返回其他用户记录。
- 管理员提交 `PENDING` 或非法状态转换：返回状态操作不允许业务错误。
- 删除不存在记录：返回 CapCut 项目账号记录不存在业务错误。

具体错误码编号在实施时从 `ErrorCode.java` 当前可用分段中选择，不在设计阶段预留无调用错误码。

## 9. 验证

后端验证：

- TikTok 报白和 CapCut 回填通过两个独立表单分别提交，每次只创建一条对应类型记录，初始状态都是 `PENDING`。
- 更新一种账号记录的状态不会修改另一种账号记录。
- 条件字段缺失、混用或只包含空白时提交失败。
- TikTok 报白和 CapCut 回填都只接受 `US`、`EU`、`ROW`，其他地区值提交失败。
- 重复 CapCut UID 被数据库唯一约束和业务错误稳定拦截。
- 用户只能读取本人记录。
- 管理员可以执行四种允许的状态转换，不能将状态改回 `PENDING`。
- 管理员可以删除任意状态记录，删除不存在记录返回业务错误。
- 存在 CapCut 项目账号记录的推广用户不能被物理删除。
- 新迁移和开发重建脚本的表结构一致。
- 现有 GoodShort 报白测试不回归。

前端验证：

- TikTok 报白和 CapCut 回填表单只显示并提交各自字段，不会一起提交；两种表单的账号地区都使用“美国 / 英法德 / 其他地区”固定选项。
- 列表正确显示“审核中 / 已加白 / 未通过”。
- 用户端不出现“待提交”和“提交失败”。
- 管理端不出现审核人、拒绝原因、失败重试和任务信息。
- 状态操作和删除确认调用正确接口。
- 用户端桌面及 320px 以上移动视口不存在遮挡或裁切。

完成前运行后端、管理端、用户端 canonical Gate 以及根目录 `git diff --check`。真实 CapCut 环境不属于本期验证项，因为本期不调用 CapCut API。

## 10. 实施顺序

1. 新增 CapCut 项目账号表的数据库迁移、开发重建结构、后端 Entity/Mapper/DTO/VO 和针对性测试。
2. 实现用户查询、提交接口和推广用户删除保护。
3. 实现管理员分页、状态更新和删除接口。
4. 实现用户端独立的 TikTok 报白与 CapCut 回填页面。
5. 实现管理端独立的 TikTok 报白与 CapCut 回填管理页面。
6. 完成三端 Gate 后更新当前架构和缺口文档。

## 11. 与旧草案的关系

本设计只处理 TikTok 人工报白和 CapCut 账号回填。确认后，它将替代 `2026-09-09-capcut-filing-and-order-attribution-design.md` 中的账号与报白部分；旧草案中的订单归因内容继续保持未确认、未实施，不能随本设计一起进入开发。

## 12. 待确认项

以下事项尚未从当前需求中得到最终口径：

1. 账号资料填错时是否允许管理员删除任意状态的本地记录；本稿按“允许删除，用户重新提交”编写。
2. 本期是否使用独立菜单且暂不关联具体 `promotion_project`；本稿按独立菜单和独立业务表编写，不通过项目名称或 `CPA/CPM/CPS` 类型识别 CapCut。

TikTok 账号报白与 CapCut 账号回填分开填写、共用一张新表但各自生成独立记录、分别维护三状态，账号地区固定为 `US`（美国）、`EU`（英国/法国/德国）、`ROW`（其他地区），以及无审核人、无拒绝原因、无官方 API、无订单和结算已经确认；其他字段、接口和页面行为仍以本稿为评审建议，整份设计经确认后再实施。
