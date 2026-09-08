# CapCut 系统报白与订单归属设计

日期：2026-09-09

状态：设计草案，待确认，尚未实施

## 1. 背景

CapCut 项目由本系统提供账号报白能力。用户可以分别提交 TK 账号和 CAPCUT 账号，两种账号互相独立，不要求一一对应。

CapCut 后续订单通过 CAPCUT UID 匹配推广用户，因此报白时必须保存 CAPCUT UID，并保证一个 UID 只能明确归属一个用户。

项目卡片和报白入口配置见[推广项目卡片与后台管理设计](2026-09-09-promotion-project-card-management-design.md)。

## 2. 目标

- 用户可以单独提交 TK 账号。
- 用户可以单独提交 CAPCUT 账号。
- TK 账号保存账号名称和账号地区。
- CAPCUT 账号保存 CAPCUT ID、CAPCUT UID 和账号地区。
- 管理员可以审核 CapCut 报白记录。
- 只有审核通过的 CAPCUT UID 才能参与订单归属。
- CapCut 订单保存原始 UID，并能区分 UID 归因和现有推广链接归因。

## 3. 非目标

- 不把 TK 账号和 CAPCUT 账号绑定成一对一关系。
- 不把 CAPCUT ID 或账号地区作为订单匹配键。
- 不修改现有 GoodShort 媒体账号报白字段和流程。
- 不在本阶段实现 CapCut 官方报白 API或订单 API 的具体适配。
- 不在本阶段实现新的结算规则或佣金规则。

## 4. 账号数据模型

新增 `promotion_capcut_filing_account` 表。一行只代表一个独立账号，不包含另一个账号的关联 ID。

```text
promotion_capcut_filing_account
- id
- user_id
- account_type             TIKTOK / CAPCUT
- account_name             TIKTOK 时必填
- account_region           两种类型都必填
- capcut_id                CAPCUT 时必填
- capcut_uid               CAPCUT 时必填
- status                   PENDING / APPROVED / REJECTED
- reject_reason
- submitted_at
- reviewed_at
- reviewed_by
- created_at
- updated_at
```

字段规则：

| 账号类型 | 必填字段 | 不使用的字段 |
| --- | --- | --- |
| `TIKTOK` | `account_name`、`account_region` | `capcut_id`、`capcut_uid` |
| `CAPCUT` | `capcut_id`、`capcut_uid`、`account_region` | `account_name` |

账号提交示例：

```json
{
  "accountType": "TIKTOK",
  "accountName": "账号名称",
  "accountRegion": "US"
}
```

```json
{
  "accountType": "CAPCUT",
  "capcutId": "123456",
  "capcutUid": "789012",
  "accountRegion": "US"
}
```

后端必须执行条件校验，不能只依赖前端隐藏字段。两种账号都可以有多条记录，提交一种账号时不要求另一种账号存在。

`capcut_uid` 是订单匹配键。保存前去除首尾空格并拒绝空值；一个 UID 只能归属一个用户。重复 UID 提交返回业务错误，不能让两个用户同时拥有同一个 UID。

账号地区只用于报白资料和审核，CAPCUT ID 用于报白资料和排查；两者都不参与订单匹配。

表不建立物理外键，沿用当前生产 schema 的逻辑关联规则。生产环境新增表只能通过新的 Flyway 迁移，开发重建脚本必须同步更新。

## 5. 用户端接口

```http
GET  /api/user/promotion/capcut/accounts
POST /api/user/promotion/capcut/accounts
```

接口返回用户自己的 TK 和 CAPCUT 账号及各自审核状态。初始状态为 `PENDING`，管理员审核后变为 `APPROVED` 或 `REJECTED`。

只有账号本人可以查询自己的记录。用户端不提供修改已审核账号的入口；需要更正时重新提交一条记录，避免已参与订单匹配的 UID 被静默改变。

## 6. 管理端接口与页面

```http
GET /api/admin/promotion/capcut/accounts?page=1&size=20&status=&keyword=
PUT /api/admin/promotion/capcut/accounts/{id}/review
```

管理员列表显示：账号类型、账号名称或 CAPCUT ID/UID、地区、提交时间和审核状态。

审核接口只允许：

```text
PENDING -> APPROVED
PENDING -> REJECTED
```

拒绝时必须填写拒绝原因。已完成状态不能被普通审核操作重复覆盖。

如果需要纠正 UID 归属，必须由管理员执行明确的冲突处理和订单重匹配操作，不能通过普通编辑接口覆盖原 UID。

## 7. 用户端页面

CapCut 卡片的“账号报白”入口进入系统报白页面。页面包含两个独立切换项：

```text
TikTok 账号
- 账号名称
- 账号地区

CAPCUT 账号
- CAPCUT ID
- CAPCUT UID
- 账号地区
```

页面提供独立的账号列表和审核状态：

- `PENDING`：审核中。
- `APPROVED`：已通过，可参与订单匹配。
- `REJECTED`：已拒绝，显示拒绝原因。

页面不显示“关联 TK 账号”“绑定 CAPCUT 账号”等控件，也不要求用户先填写另一种账号。

地区第一版作为必填字段；地区是固定枚举还是后端字典，需要在实施前确认。没有确认列表时，后端至少按非空字符串校验，不能把地区写死成某一个值。

## 8. CapCut 订单匹配

CapCut Provider 适配层必须把订单中的 UID 映射为明确的 `capcutUid` 字段。不能依赖业务代码猜测 `externalUserId` 或解析展示文本。

订单同步流程：

1. 读取并规范化订单中的 CAPCUT UID。
2. 将原始 UID 保存到订单的 `capcut_uid`。
3. 查询 `account_type = CAPCUT` 且 `status = APPROVED` 的报白账号。
4. 找到唯一账号后，把该账号的 `user_id` 写入订单。
5. 将订单归因来源写为 `CAPCUT_UID`。
6. 没有匹配到时保留 UID，订单保持未归属并记录原因。
7. 发现 UID 冲突时不自动归属，交给管理员排查。

订单表新增：

```text
capcut_uid              订单原始 CAPCUT UID，可为空
attribution_source      TRACKING_LINK / CAPCUT_UID / NONE
```

现有 CPS 订单继续使用推广跟踪号归因，来源为 `TRACKING_LINK`。CapCut UID 归因不能覆盖已经存在的推广链接归因，也不能让同一订单同时走两条归因路径。

只有 `APPROVED` 账号参与匹配。订单同步时尚未审核通过的 UID 先保持未归属；账号之后审核通过时，可以对仍未结算的未归属订单执行受控重匹配。已结算订单不能被静默修改归属。

CAPCUT UID 冲突、未匹配和重匹配都需要保留审计信息，便于管理员解释订单归属结果。

## 9. 后端实现边界

后端按现有 `promotion/controller`、`dto`、`entity`、`mapper`、`service`、`vo` 分层：

- Controller 负责权限、参数校验和响应包装。
- Service 负责账号类型校验、UID 唯一性、审核状态和订单匹配规则。
- Mapper 负责账号查询、唯一性检查和订单关联 SQL。
- Provider 适配层负责从 CapCut 原始订单中提取 `capcutUid`。

在 CapCut 外部 API 字段确认前，不伪造自动报白或订单同步实现。系统可以先完成账号提交、管理员审核和明确的 UID 数据边界。

## 10. 验证与实施顺序

后端验证覆盖：

- TK 和 CAPCUT 条件字段校验。
- 两种账号独立提交，不要求互相存在。
- CAPCUT UID 重复提交被拒绝。
- 审核状态转换和权限。
- 只有 `APPROVED` UID 可以匹配订单。
- 无匹配 UID、重复 UID 和审核后受控重匹配。
- 现有 CPS/GoodShort 订单归因不回归。

前端验证覆盖：

- 两种账号表单只显示对应字段。
- 独立账号列表和状态展示。
- 重复 UID 显示业务错误。
- CapCut 卡片可以进入系统报白页面。
- 桌面和 320px 以上移动视口没有表单遮挡或按钮裁切。

实施顺序：

1. 新增 CapCut 账号表和用户查询/提交接口。
2. 新增管理员审核接口和页面。
3. 接入 CapCut 系统报白页面。
4. 扩展订单模型和 Provider 记录，保存并匹配 `capcutUid`。
5. 完成未匹配订单和审核后受控重匹配验证。

本设计确认前不新增 CapCut API、schema、权限或订单归因代码。
