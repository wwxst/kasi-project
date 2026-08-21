# 平台分佣规则配置页设计

日期：2026-08-21

状态：设计已确认，待实现

## 目标

在现有“系统配置 → 短剧 API 配置”页面内，为当前选中的短剧平台增加平台级分佣规则配置，不新增独立左侧菜单或独立路由。

## 页面结构

当前页面按平台使用 Tabs。每个平台 Tab 内保持两个相互独立的区块：

1. API 接入配置：接口 URL、PID、KEY、启用状态、账号报备方式，以及现有保存和连接测试操作。
2. 分佣规则：该平台的当前、未来和历史规则列表，以及规则生命周期操作。

API 接入配置和分佣规则分别提交，避免把两种不同的保存逻辑混在一个表单中。切换平台时，两个区块都切换到该平台的数据。

## 分佣规则交互

- 表格列展示五项费率、`effectiveFrom`、`effectiveTo` 和派生状态。
- 费率在页面上使用 `0..100` 百分比输入和展示，最多保留四位小数；后端负责保存为 `0..1` 高精度比例。
- 当前生效规则显示在列表前部，未来规则和历史规则按生效时间倒序排列。
- 超级管理员可以新增规则、编辑 `PENDING` 规则、提前结束 `ACTIVE` 规则、删除 `PENDING` 规则。
- 普通管理员可以查看规则，但不显示写操作入口；后端权限仍是最终约束。
- `ENDED` 规则只读。
- 新增和编辑使用独立弹窗表单；提前结束和删除使用二次确认。
- 接口业务错误沿用现有 HTTP 客户端的统一错误提示，不在前端复制时间重叠等业务规则。

## 后端接口

```text
GET    /api/admin/drama/providers/{providerId}/commission-rules
POST   /api/admin/drama/providers/{providerId}/commission-rules
PUT    /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
PATCH  /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}/end-time
DELETE /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
```

## 非目标

本次不实现推广链接、订单、佣金结算、导出、转化分析，也不在推广用户端增加分佣页面。

## 验证

- API 层测试覆盖五个请求方法、百分比字段映射和统一错误处理。
- 页面测试覆盖平台切换、规则加载、超级管理员写操作、普通管理员只读、状态对应操作入口和业务错误提示。
- 提交前运行 `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm format:check` 和 `pnpm build`。
