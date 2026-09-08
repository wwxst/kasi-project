# 模块四：推广任务与推广链接最终设计

日期：2026-09-08

## 目标与边界

模块四继续以 `promotion_link` 作为推广任务实体。用户重新发起推广时创建新任务；用户端不展示任务状态、失败原因、操作列或充值金额，也不提供重试、修改或重新提交能力。

创建接口返回批次 `complete=true` 时，用户端才关闭弹窗、跳转推广任务页并提示“推广链接和口令已生成”。`complete=false` 时保留当前弹窗并提示本次生成未全部成功。

## 转化归因

GoodShort `POST /creek/open/promotion/analyticalReport` 返回 `code`、`bookId`、`customParams` 和 `pId/pid`。`code` 是甲方口令标识，但甲方文档未承诺跨 PID 全局唯一，本地 `promotion_link.external_code` 也没有全局唯一约束。因此查询以 `code -> promotion_link.external_code` 为主，并同时核对：

- `pid -> short_drama_connection.partner_id`
- `bookId -> provider_drama.external_drama_id`
- `customParams -> promotion_user.user_no`

推广任务分页查询按该关系聚合同一链接全部自然日数据，返回点击数、归因用户数、新注册人数、新充值人数、新会员人数、充值用户数和订单数。`orderAmount` 继续同步和保存，但不进入用户推广任务 VO。

## 创建约束

- GoodShort 生成接口按 `pid + bookId + customParams + codeMedia` 维度保证相邻调用至少间隔 2 秒。
- `mediaTypes` 在 HTTP 输入边界拒绝重复元素。
- 同一用户的同一 `requestKey` 再次提交时，provider、drama、媒体平台集合和链接类型必须与原批次一致；不一致直接返回业务冲突，不重置旧记录，不调用 GoodShort。
- 不修改 `customParams=userNo`、订单归因、表结构或物理外键，不增加补偿任务或通用重试框架。

## 用户端字段

创建时间、推广名称、短剧、媒体平台、口令、推广链接、点击数、归因用户数、新注册人数、新充值人数、新会员人数、充值用户数、订单数。

## 验证

后端覆盖转化聚合与用户隔离、requestKey 内容冲突、重复媒体校验、2 秒维度限流和现有 GoodShort HTTP 请求；用户端覆盖完整字段、无状态/失败/操作/充值金额，以及 `complete=false` 不跳转不提示成功。

## 成功记录展示与日报滚动补拉

- 用户推广任务分页和总数只包含 `promotion_link.status = 'SUCCESS'` 的记录；`PENDING` 和 `FAILED` 继续保留在数据库，不提供用户端状态、失败原因或重试入口。
- `GOODSHORT_ANALYTICAL_REPORT_SYNC` 每日 08:00 使用一次日期范围请求，同步最近 3 个已经结束的自然日，即昨天以及之前两天。
- 日报继续按现有唯一维度 upsert，同一天的数据覆盖更新；用户任务查询继续对每日数据求和，不增加补偿任务、重试框架或数据库变更。
