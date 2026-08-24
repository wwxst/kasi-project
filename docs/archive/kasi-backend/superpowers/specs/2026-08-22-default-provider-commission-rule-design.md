# 平台默认分佣规则设计

当前平台分佣规则采用默认配置模型：每个平台最多一条规则，不设置生效时间、结束时间或状态，不允许删除。首次配置使用 `POST`，已有规则使用 `PUT` 直接覆盖五项费率。普通管理员可查询，超级管理员可新增和编辑。API 使用 `0..100` 百分比，数据库保存 `0..1` 高精度比例。

接口仅保留：

```text
GET  /api/admin/drama/providers/{providerId}/commission-rules
POST /api/admin/drama/providers/{providerId}/commission-rules
PUT  /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
```

V11 迁移会将旧时间版本收敛为每个平台一条记录并增加 `provider_id` 唯一约束。推广链接、订单同步、结算和分析仍不属于本次范围。
