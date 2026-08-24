# 短剧平台分佣规则设计

> 历史设计，已被 2026-08-22 的平台默认分佣规则契约取代。当前每个平台一条规则，仅支持 GET、POST 首次设置和 PUT 覆盖；无时间段、状态、提前结束或删除。当前行为以仓库 README 和 AGENTS.md 为准。

日期：2026-08-21

状态：历史实现记录；当前契约已取代本文方案

## 1. 背景

当前仓库已经完成短剧平台接入、媒体账号报备、GoodShort 短剧目录同步和平台级分佣规则。本阶段交付的规则版本管理、内部有效规则查询和纯 `BigDecimal` 计算器，为后续推广链接生成和订单预计佣金计算提供稳定的费率来源。

分佣规则按短剧平台配置，不按单部短剧配置。首期一个平台只有一个接入账号；未来即使同一平台增加多个接入账号，也暂时共用平台规则。

## 2. 目标

- 为每个短剧平台维护带生效时间的五项费率规则。
- 保留完整历史版本，并能按平台和指定时间准确匹配规则。
- 阻止同一平台的规则有效时间重叠。
- 提供统一的 `BigDecimal` 预计佣金计算器。
- 普通管理员可查看，只有超级管理员可写入。
- 为后续推广链接和订单模块提供内部查询能力。

## 3. 非目标

本期不实现：

- 按单部短剧、推广用户、媒体账号或接入账号配置特殊费率。
- 推广链接、推广口令和素材下载。
- 订单同步、费率快照、退款处理、导出或结算。
- 钱包、提现、付款和已结算状态。
- 新增 RBAC 或财务角色。

## 4. 规则作用域

一条规则绑定“短剧平台 + 生效时间段”。例如 GoodShort 在同一时间使用一套五项费率，平台下所有短剧和当前唯一接入账号共用该规则。

有效时间采用左闭右开区间 `[effective_from, effective_to)`：

- `effective_from` 必填。
- `effective_to` 可空，空值表示长期有效。
- 前一条规则的结束时间可以等于后一条规则的开始时间。
- 同一平台任意时刻最多存在一条有效规则。
- 允许规则之间存在空档；空档期表示平台没有有效分佣规则，后续推广模块不得生成新推广链接。

时间状态不持久化，根据当前时间计算：

- `PENDING`：尚未到开始时间。
- `ACTIVE`：当前处于有效区间。
- `ENDED`：已经到达或超过结束时间。

## 5. 数据模型

Flyway 迁移 `V8__provider_commission_rule.sql` 已建立 `provider_commission_rule` 表。

| 字段 | 说明 |
|---|---|
| `id` | 内部自增主键 |
| `provider_id` | 关联 `short_drama_provider.id` |
| `channel_fee_rate` | 渠道费率 |
| `principal_fee_rate` | 甲方手续费率 |
| `principal_commission_rate` | 甲方给我方的分佣比例 |
| `downstream_fee_rate` | 我方手续费率 |
| `downstream_commission_rate` | 我方给下游的分佣比例 |
| `effective_from` | 开始生效时间 |
| `effective_to` | 结束时间，可空 |
| `created_by` | 创建管理员内部 ID |
| `updated_by` | 最后修改管理员内部 ID |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

五项费率使用高精度 `DECIMAL` 保存 `0..1` 的比例，不使用 `float` 或 `double`。表通过外键限制 `provider_id`，并建立平台和生效时间查询索引。`created_by`、`updated_by` 沿用当前审计字段约定，不增加管理员外键。

数据库不保存派生状态，也不把当前费率直接写入 `short_drama_provider`。

## 6. 费率输入与计算

管理员 API 使用百分比输入和输出，`30` 表示 `30%`。五项费率都必须明确提交，范围为 `0..100`，最多保留四位百分比小数。渠道费率、甲方手续费率和我方手续费率可由前端默认填 `0`，但请求中仍必须出现；数据库统一保存为 `0..1` 的高精度比例。

数据库写入前将百分比除以 `100`，读取后再转换为百分比 VO。转换和计算全程使用 `BigDecimal`。

```text
推广用户预计佣金
= 订单金额
× (1 - 渠道费率)
× (1 - 甲方手续费率)
× 甲方给我方的分佣比例
× (1 - 我方手续费率)
× 我方给下游的分佣比例
```

中间结果不提前舍入。最终金额保留两位小数并使用 `HALF_UP` 四舍五入。计算器只负责确定性数学计算，不查询数据库；订单模块后续负责匹配规则并保存费率快照。

## 7. 历史与修改规则

- 尚未生效的未来规则可以修改全部字段或物理删除。
- 已生效规则的五项费率和开始时间不可修改，也不可删除。
- 已生效规则只能设置更早的结束时间，用于提前终止；新结束时间必须晚于当前时间并通过重叠校验。
- 已结束规则永久只读。
- 调整当前费率时，结束旧规则并新建下一条规则，不覆盖旧费率。

这样可以保证延迟同步的订单仍能按实际支付时间匹配历史规则。

## 8. 并发与事务

新增、修改、提前结束和删除规则均在 Service 事务中执行。写入前锁定对应 `short_drama_provider` 记录，再查询重叠区间并执行写入，确保两个管理员并发操作时不会创建重叠规则。

重叠判断排除当前正在修改的规则，并按左闭右开区间处理。应用层校验与事务锁共同维护该约束，不能只依赖写入前的普通查询。

## 9. 后端分层

代码继续位于 `com.kasi.backend.drama`：

```text
drama/controller   管理员分佣规则 HTTP API
drama/service      分佣规则 Service 接口
drama/service/impl 事务、权限外业务规则和持久化编排
drama/mapper       单表持久化
drama/entity       provider_commission_rule 映射
drama/dto          创建、修改和提前结束请求
drama/vo           规则列表和详情响应
drama/calculator   无数据库依赖的 BigDecimal 计算器
```

Controller 只做参数校验、调用 Service 和返回 `ApiResponse<VO>`，不直接调用 Mapper。

## 10. 管理员 API

```text
GET    /api/admin/drama/providers/{providerId}/commission-rules
POST   /api/admin/drama/providers/{providerId}/commission-rules
PUT    /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
PATCH  /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}/end-time
DELETE /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
```

- `GET` 允许 `ROLE_ADMIN` 和 `ROLE_SUPER_ADMIN`，按开始时间倒序返回规则及派生状态。
- 所有写接口只允许 `ROLE_SUPER_ADMIN`。
- `POST` 创建当前或未来规则。
- `PUT` 只修改尚未生效的规则。
- `PATCH /end-time` 只提前结束当前规则。
- `DELETE` 只删除尚未生效的规则。

后续模块通过内部 Service 按“平台 ID + 指定时间”查询有效规则，不调用管理员 HTTP API。查询不到时返回空结果，由调用方按自身业务路径决定错误码；本期不提前增加推广链接专用错误码。

## 11. 校验与错误

Jakarta Validation 处理必填、百分比范围和小数精度，失败继续返回 `VALIDATION_ERROR(1006)`。Service 处理以下业务错误：

- 短剧平台不存在。
- 规则不存在或不属于路径中的平台。
- 开始时间不早于结束时间。
- 同一平台规则时间重叠。
- 当前规则不允许修改费率或删除。
- 历史规则不允许任何写操作。

新增错误码使用短剧领域 `6xxx` 段，只增加当前 API 能够实际返回的枚举值。普通管理员访问写接口由 Spring Security 返回 403。

## 12. 测试

- V8 迁移在隔离 H2 MySQL 模式数据库中真实执行。
- Mapper 覆盖五项费率精度、平台查询、指定时间匹配和重叠查询。
- 计算器覆盖零费率、100% 费率、小数费率和最终两位 `HALF_UP`。
- Service 覆盖待生效、生效中、已结束状态，以及相邻区间和重叠区间。
- Service 覆盖未来规则编辑/删除、当前规则提前结束和历史规则只读。
- 并发测试证明同一平台不能创建两条重叠规则。
- Controller 覆盖正常路径、DTO 校验、资源不存在、状态冲突、普通管理员只读和超级管理员写入。
- 运行分佣规则聚焦测试、完整 `mvnw.cmd test`、Java 25 编译检查和 `git diff --check`。

2026-08-21 使用 Temurin JDK 25.0.3 和 Maven Wrapper 3.9.16 完成最新自动化验证：

- 分佣规则六类聚焦测试：`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`。
- 完整测试套件：`Tests run: 264, Failures: 0, Errors: 0, Skipped: 0`。
- `mvnw.cmd -DskipTests compile`：exit code 0，`BUILD SUCCESS`。
- `git diff --check`：exit code 0，无空白错误。

## 13. 文档同步

已同步更新 `README.md`、`AGENTS.md`、多平台路线图和总设计，将平台级分佣规则标记为已实现；推广链接、订单同步、费率快照、导出、钱包/结算和转化分析继续明确标记为未实现。
