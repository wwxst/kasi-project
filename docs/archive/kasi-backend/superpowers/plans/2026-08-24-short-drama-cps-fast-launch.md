# Short Drama CPS Fast Launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入通用项目结算框架的前提下，补齐 GoodShort 短剧 CPS 的最小可上线闭环：用户生成真实推广链接，管理员按日期窗口手动同步订单，系统按 `trackingNo` 归因、保存费率快照、计算佣金，并提供管理端与用户端查询/CSV 导出。

**Architecture:** 继续使用现有 `provider` 适配器、`promotion_link` 推广链接、`provider_commission_rule` 平台分佣规则和 `ProviderCommissionCalculator`。新增独立的 `promotion_order` 订单域；外部订单同步只由管理员手动触发，落库和归因在短事务中完成。订单保存原始响应和当时的五项费率快照，月度查询直接聚合已支付订单，不新增账单、钱包或提现域。

**Tech Stack:** Java 25, Spring Boot 4.0.7, MyBatis 4.0.1, MySQL 8/Flyway, H2 MySQL compatibility tests, Spring Security/JWT, React 19, Ant Design (admin), TDesign React (user), TanStack Query, Vitest/MSW, CSV export.

---

## 首发边界与上线节奏

本计划只覆盖 GoodShort 短剧 CPS。CapCut、CPA、CPM、通用项目管理、CapCut 人工文件导入、自动订单轮询、订单定时任务、每日转化统计、独立账单表、开票/付款、钱包/提现均进入后续版本。CapCut 后续即使采用人工从甲方系统导出，也不应提前把其字段或结算判断混入本次短剧订单表。

最快可行节奏：单人全职约 7-10 个工作日；两人并行后端/前端约 4-6 个工作日。外部 GoodShort 订单接口字段若未拿到可复现样例，会成为唯一的前置阻塞；在接口样例确认前不写猜测性的 JSON 映射。

上线验收只认以下链路：

```text
用户选择短剧 + 已报白媒体账号
  -> POST /api/user/promotion/links
  -> GoodShort 真实口令/链接 + trackingNo
  -> 管理员输入 from/to 手动同步 GoodShort 订单
  -> promotion_order 幂等 upsert + customParams/trackingNo 归因
  -> 保存五项费率快照 + ProviderCommissionCalculator 计算佣金
  -> 管理员/用户按支付月份查询和 CSV 导出
```

## 现有实现基线（实现人员必须先确认）

- `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java` 已生成 `trackingNo`、`customParams` 和真实 GoodShort 链接，首发直接复用。
- `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java` 已实现目录、报白和推广链接；当前 `capabilities()` 错误地声明了尚未实现的 `ORDER_SYNC`、`ANALYTICS_SYNC`。
- `src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java` 当前直接覆盖 `provider_commission_rule`，必须在订单快照前增加不可变历史记录。
- `src/main/java/com/kasi/backend/drama/calculator/ProviderCommissionCalculator.java` 已实现五项费率 `BigDecimal` 公式和 `HALF_UP`，不能复制第二套公式。
- `src/main/java/com/kasi/backend/promotion/service/impl/PromotionTaskServiceImpl.java` 和 `V13__promotion_task.sql` 是工作区未提交的占位能力；它生成另一套 trackingNo、没有媒体账号关联，也没有真实 GoodShort 链接，不能作为首发主链路。
- `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.tsx` 当前实际调用 `createPromotionTasks`，必须改回已有 `createPromotionLink`。

## 计划任务

### Task 1: 固化 GoodShort 订单接口契约

**Files:**
- Create: `docs/superpowers/specs/2026-08-24-goodshort-order-contract.md`
- Read: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortSigner.java`
- Read: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Test reference: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkAdapterTest.java`

- [ ] **Step 1: 收集真实接口样例**

从 GoodShort 供应方文档或脱敏抓包中确认订单查询的 HTTP method、path、签名字段、分页字段、时间窗口字段及以下响应字段：第三方订单号、订单状态、支付时间、退款时间、金额、币种、`customParams`、短剧外部 ID、供应方更新时间和原始状态码。样例必须能解释重复订单更新和退款。

- [ ] **Step 2: 编写契约文档**

在 `goodshort-order-contract.md` 固定 Java 映射名和状态映射，至少包含：

```text
GoodShortOrderRecord.externalOrderId -> promotion_order.external_order_id
GoodShortOrderRecord.customParams    -> promotion_order.custom_params
GoodShortOrderRecord.paidAt          -> promotion_order.paid_at
GoodShortOrderRecord.refundedAt      -> promotion_order.refunded_at
GoodShortOrderRecord.providerUpdatedAt -> promotion_order.provider_updated_at
```

文档同时写明金额精度、时区、`from/to` 是否闭开区间，以及分页终止条件。不得在实现代码中留下未确认的字段名。

- [ ] **Step 3: 做契约检查**

运行 `rg -n "GoodShortOrder|ORDER_SYNC|customParams|externalOrderId" docs/superpowers/specs/2026-08-24-goodshort-order-contract.md`。预期输出包含完整字段表、至少一份脱敏请求和一份脱敏响应；缺少任一字段时先补契约，不进入后续编码。

### Task 2: 让用户端只生成真实 PromotionLink

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/promotionLinkApi.ts`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/promotionLinkTypes.ts`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/mediaAccountApi.ts`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/app/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/layouts/AccountLayout.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.test.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/app/AppRouter.test.tsx`

- [ ] **Step 1: 写失败测试**

在 `PromotionLinkPage.test.tsx` 增加 MSW 场景：页面加载短剧和本人媒体账号；选择媒体账号后提交请求到 `/api/user/promotion/links`，请求体包含 `providerId`、`dramaId`、`mediaAccountId`、`landingType`、`requestKey`；成功后展示 `shareUrl`、`externalCode` 和 `trackingNo`。同时断言页面不请求 `/api/user/promotion/tasks`。

- [ ] **Step 2: 运行失败测试**

在 `E:/JavaProjects/kasi-project/kasi-user-web` 运行 `pnpm exec vitest run src/pages/promotion/PromotionLinkPage.test.tsx`。预期当前实现因调用 `/api/user/promotion/tasks` 而失败。

- [ ] **Step 3: 实现最小链路**

移除任务名称、多平台任务和 `createPromotionTasks` 状态，改为使用已有链接 API：

```ts
const createLinkMutation = useMutation({ mutationFn: createPromotionLink })

await createLinkMutation.mutateAsync({
  providerId: drama.providerId,
  dramaId: drama.id,
  mediaAccountId: selectedMediaAccountId,
  requestKey: crypto.randomUUID(),
  landingType: 'DEFAULT',
})
```

保留短剧筛选、详情抽屉和链接历史；`PromotionTaskPage` 路由和导航改为重定向到 `/promotion/links`，不再展示 0 值订单/广告统计。

- [ ] **Step 4: 运行前端回归**

运行 `pnpm exec vitest run src/pages/promotion/PromotionLinkPage.test.tsx src/app/AppRouter.test.tsx`，预期全部通过；再运行 `pnpm run build`，预期 TypeScript 和生产构建均为零错误。

### Task 3: 增加最小订单同步 SPI 和 GoodShort 映射

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/spi/OrderSyncProviderAdapter.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/OrderSyncRequest.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/ProviderOrderPage.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/ProviderOrderRecord.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortOrderResponse.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortOrderData.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `src/main/java/com/kasi/backend/provider/enums/ProviderCapability.java`
- Test: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortOrderAdapterTest.java`

- [ ] **Step 1: 写失败测试**

在 `GoodShortOrderAdapterTest` 固定三种响应：一条已支付订单、一条退款订单、一页无数据；断言签名请求包含 Task 1 契约定义的时间窗口和分页字段，映射保留原始状态码、金额、币种、`customParams` 和更新时间。

- [ ] **Step 2: 运行失败测试**

在后端根目录运行 `./mvnw.cmd -Dtest=GoodShortOrderAdapterTest test`。预期因 SPI 和 DTO 尚不存在而失败。

- [ ] **Step 3: 实现 SPI**

新增接口保持适配器边界，不把 GoodShort JSON 泄漏到 promotion 域：

```java
public interface OrderSyncProviderAdapter extends ProviderAdapter {
    ProviderOrderPage fetchOrders(ProviderConnectionSecret connection,
                                  OrderSyncRequest request);
}

public record OrderSyncRequest(LocalDateTime from, LocalDateTime to,
                               int pageNo, int pageSize) {}
```

`GoodShortAdapter` 实现分页调用和状态映射，复用现有 `GoodShortSigner`、`post` 异常分类及时间解析；从 `CAPABILITIES` 移除未实现的 `ANALYTICS_SYNC`，只有实现完成后才保留 `ORDER_SYNC`。

- [ ] **Step 4: 运行适配器测试**

再次运行 `./mvnw.cmd -Dtest=GoodShortOrderAdapterTest test`，预期 PASS，并确认 `GoodShortAdapter` 原有连接探测、目录、报白和链接测试仍通过：`./mvnw.cmd --% -Dtest=GoodShortAdapterTest,GoodShortPromotionLinkAdapterTest,GoodShortCatalogAdapterTest test`。

### Task 4: 建立订单与费率历史表

**Files:**
- Create: `src/main/resources/db/migration/V15__promotion_order_and_rule_history.sql`
- Modify: `src/test/resources/test-schema.sql`
- Create: `src/main/java/com/kasi/backend/promotion/entity/PromotionOrder.java`
- Create: `src/main/java/com/kasi/backend/promotion/enums/PromotionOrderStatus.java`
- Create: `src/main/java/com/kasi/backend/promotion/enums/PromotionAttributionStatus.java`
- Create: `src/main/java/com/kasi/backend/drama/entity/ProviderCommissionRuleHistory.java`
- Create: `src/test/java/com/kasi/backend/PromotionOrderMigrationTest.java`
- Create: `src/test/java/com/kasi/backend/ProviderCommissionRuleHistoryMigrationTest.java`

- [ ] **Step 1: 写迁移失败测试**

迁移测试实际执行 Flyway/H2 MySQL 模式并断言表、唯一键和关键列存在；订单唯一键必须是 `(connection_id, external_order_id)`，而不是视频或短剧 ID。

- [ ] **Step 2: 增加最小 DDL**

`promotion_order` 至少包含以下列，所有上游字段和计算结果同时保留：

```sql
CREATE TABLE promotion_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  connection_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL,
  external_order_id VARCHAR(128) NOT NULL,
  raw_status VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  paid_at DATETIME NULL,
  refunded_at DATETIME NULL,
  provider_updated_at DATETIME NULL,
  order_amount DECIMAL(18,6) NULL,
  currency VARCHAR(16) NULL,
  custom_params VARCHAR(512) NULL,
  tracking_no VARCHAR(128) NULL,
  promotion_link_id BIGINT NULL,
  user_id BIGINT NULL,
  media_account_id BIGINT NULL,
  drama_id BIGINT NULL,
  attribution_status VARCHAR(32) NOT NULL,
  rule_history_id BIGINT NULL,
  channel_fee_rate DECIMAL(20,10) NULL,
  principal_fee_rate DECIMAL(20,10) NULL,
  principal_commission_rate DECIMAL(20,10) NULL,
  downstream_fee_rate DECIMAL(20,10) NULL,
  downstream_commission_rate DECIMAL(20,10) NULL,
  commission_amount DECIMAL(18,2) NULL,
  commission_status VARCHAR(32) NULL,
  raw_payload_json JSON NOT NULL,
  sync_from DATETIME NOT NULL,
  sync_to DATETIME NOT NULL,
  first_synced_at DATETIME NOT NULL,
  last_synced_at DATETIME NOT NULL,
  last_error_message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_promotion_order_source (connection_id, external_order_id),
  KEY idx_promotion_order_user_paid (user_id, paid_at),
  KEY idx_promotion_order_attribution (attribution_status, paid_at)
);
```

`provider_commission_rule_history` 保存每次当前规则变更前的五项费率、变更时间和操作人；迁移时把现有 `provider_commission_rule` 当前值写入初始历史记录，使首发订单始终能引用一个历史 ID。

- [ ] **Step 3: 验证迁移**

运行 `./mvnw.cmd -Dtest=PromotionOrderMigrationTest,ProviderCommissionRuleHistoryMigrationTest test`，预期 PASS；运行 `git diff --check`，预期无空白错误。

### Task 5: 实现订单幂等落库、归因和 CPS 快照

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/mapper/PromotionOrderMapper.java`
- Create: `src/main/resources/mapper/PromotionOrderMapper.xml`
- Create: `src/main/java/com/kasi/backend/promotion/mapper/ProviderCommissionRuleHistoryMapper.java`
- Create: `src/main/resources/mapper/ProviderCommissionRuleHistoryMapper.xml`
- Modify: `src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`
- Modify: `src/main/resources/mapper/PromotionLinkMapper.xml`
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionOrderService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/ProviderCommissionRuleServiceImpl.java`
- Test: `src/test/java/com/kasi/backend/promotion/mapper/PromotionOrderPersistenceTest.java`
- Test: `src/test/java/com/kasi/backend/promotion/service/PromotionOrderServiceTest.java`
- Test: `src/test/java/com/kasi/backend/drama/service/ProviderCommissionRuleHistoryServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖四个可复现行为：同一 `(connectionId, externalOrderId)` 二次同步只更新原记录；已支付订单通过 `customParams == promotion_link.tracking_no` 找到用户；找不到 trackingNo 的订单保留原始字段并标记 `UNATTRIBUTED`；当前规则修改后，旧订单的五项费率和 `commissionAmount` 不变化。

- [ ] **Step 2: 运行失败测试**

运行 `./mvnw.cmd --% -Dtest=PromotionOrderPersistenceTest,PromotionOrderServiceTest,ProviderCommissionRuleHistoryServiceTest test`，预期因 Mapper、Service 和历史快照不存在而失败。

- [ ] **Step 3: 实现 Mapper 与归因**

增加 `PromotionLinkMapper.findByTrackingNo(String trackingNo)`，订单 Mapper 提供 `findBySourceForUpdate`、`insert`、`updateFromSync`、管理员/用户分页查询和月度聚合。归因只允许以下链路：

```java
PromotionLink link = promotionLinkMapper.findByTrackingNo(record.customParams());
if (link == null) {
    order.setAttributionStatus(UNATTRIBUTED);
} else {
    order.setPromotionLinkId(link.getId());
    order.setUserId(link.getUserId());
    order.setMediaAccountId(link.getMediaAccountId());
    order.setDramaId(link.getDramaId());
    order.setTrackingNo(link.getTrackingNo());
    order.setAttributionStatus(ATTRIBUTED);
}
```

禁止按短剧名、媒体账号名、最近用户或视频字段猜测归属。

- [ ] **Step 4: 实现费率历史和计算快照**

将 `ProviderCommissionRuleServiceImpl` 的 POST/PUT 改为同一事务：锁定平台规则行，先插入旧值历史，再更新当前规则；同步服务在订单首次归因且状态为 `PAID` 时读取当前规则，保存 `ruleHistoryId`、五项数据库比例和 `ProviderCommissionCalculator.calculate(...)` 返回值。订单更新只刷新原始状态/时间/金额，不重新读取规则重算已存在快照；退款只更新退款状态并把佣金标记为 `REVERSED`。

- [ ] **Step 5: 运行服务测试**

运行 `./mvnw.cmd --% -Dtest=PromotionOrderPersistenceTest,PromotionOrderServiceTest,ProviderCommissionRuleHistoryServiceTest,ProviderCommissionCalculatorTest test`，预期全部 PASS。

### Task 6: 提供管理员手动同步、订单查询和未归因 API

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/dto/PromotionOrderSyncDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/dto/PromotionOrderPageQueryDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/PromotionOrderVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/PromotionOrderPageVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionOrderAdminService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/promotion/controller/AdminPromotionOrderController.java`
- Create: `src/test/java/com/kasi/backend/promotion/controller/AdminPromotionOrderControllerTest.java`

- [ ] **Step 1: 写控制器失败测试**

在 `AdminPromotionOrderControllerTest` 断言 `ROLE_ADMIN` 可以调用同步和查询，未认证返回 401；`POST /api/admin/promotion/orders/sync` 接收 `providerId`、`connectionId`、`from`、`to`，日期窗口超过 31 天返回校验错误；`GET /api/admin/promotion/orders/unattributed` 只返回 `UNATTRIBUTED` 订单。

- [ ] **Step 2: 定义端点**

```text
POST /api/admin/promotion/orders/sync
GET  /api/admin/promotion/orders
GET  /api/admin/promotion/orders/unattributed
GET  /api/admin/promotion/orders/export.csv?from=...&to=...
```

同步端点只做一次外部分页调用和本地 upsert，不创建定时任务；响应返回 `fetchedCount`、`insertedCount`、`updatedCount`、`unattributedCount`、`failedCount` 和失败摘要，不返回供应方密钥。

- [ ] **Step 3: 实现权限和事务边界**

Controller 只接收 `@Valid DTO` 并调用 Service；`PromotionOrderAdminService.sync` 使用 `@Transactional` 包住每页订单的本地 upsert，HTTP 拉取在事务外完成。查询使用 `@Transactional(readOnly = true)`。沿用 `ApiResponse`、`ErrorCode` 和当前 `ROLE_ADMIN` 规则，禁止新增项目名称判断。

- [ ] **Step 4: 运行后端接口测试**

运行 `./mvnw.cmd -Dtest=AdminPromotionOrderControllerTest test`，预期 PASS；再运行 `./mvnw.cmd -DskipTests compile`，预期 Java 25 编译零错误。

### Task 7: 增加管理员/月度查询与 CSV 导出

**Files:**
- Modify: `src/main/java/com/kasi/backend/promotion/service/PromotionOrderService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/PromotionMonthlyCommissionVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionOrderExportService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderExportServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/controller/AdminPromotionOrderController.java`
- Create: `src/main/java/com/kasi/backend/promotion/controller/UserPromotionOrderController.java`
- Create: `src/test/java/com/kasi/backend/promotion/controller/UserPromotionOrderControllerTest.java`
- Create: `src/test/java/com/kasi/backend/promotion/service/PromotionOrderExportServiceTest.java`

- [ ] **Step 1: 写查询失败测试**

构造两个已支付订单、一个退款订单和一个未归因订单，断言管理员月度汇总按 `paid_at` 所在月份聚合，用户查询只能看到自己的 `ATTRIBUTED` 订单；退款订单从可结佣总额扣除，未归因订单不进入用户汇总。

- [ ] **Step 2: 定义只读端点**

```text
GET /api/admin/promotion/orders/monthly?month=2026-08&userId=...
GET /api/user/promotion/orders?month=2026-08
GET /api/user/promotion/orders/monthly?month=2026-08
GET /api/user/promotion/orders/export.csv?month=2026-08
```

用户 VO 仅包含短剧、订单状态、支付时间、订单金额（如产品允许展示）、佣金金额和佣金状态；不返回供应方原始 JSON、上游密钥或仅供后台排查的字段。管理员 CSV 可包含原始订单号、trackingNo、归因状态和五项快照，供对账排查。

- [ ] **Step 3: 实现 CSV 输出**

使用 `HttpHeaders.CONTENT_DISPOSITION` 返回 UTF-8 BOM CSV；固定列顺序，所有值通过 CSV 转义函数处理引号、逗号和换行。不要引入 Excel 依赖，不新增账单表。

- [ ] **Step 4: 验证查询和导出**

运行 `./mvnw.cmd --% -Dtest=UserPromotionOrderControllerTest,PromotionOrderExportServiceTest test`，预期 PASS；使用 MockMvc 断言用户越权查询返回空结果而不是其他用户订单。

### Task 8: 管理后台上线最小订单工作台

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/orderApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/orderTypes.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/PromotionOrderPage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/PromotionOrderPage.test.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/router/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/layouts/AdminLayout.tsx`

- [ ] **Step 1: 写页面失败测试**

用 MSW 验证页面显示日期窗口、同步按钮、订单状态筛选、归因状态筛选、未归因列表和 CSV 导出按钮；点击同步发送 `POST /api/admin/promotion/orders/sync`，成功后刷新列表并展示新增/更新/未归因数量。

- [ ] **Step 2: 实现 API 类型和页面**

同步请求和查询类型与后端 DTO 一一对应：

```ts
export type PromotionOrderQuery = {
  from?: string
  to?: string
  status?: 'UNPAID' | 'PAID' | 'REFUNDED' | 'UNKNOWN'
  attributionStatus?: 'ATTRIBUTED' | 'UNATTRIBUTED'
  page?: number
  size?: number
}

export async function syncPromotionOrders(request: PromotionOrderSyncRequest) {
  return apiRequest<PromotionOrderSyncResult>({
    method: 'POST', url: '/api/admin/promotion/orders/sync', data: request,
  })
}
```

页面合并订单和未归因两个 Tab，不创建复杂对账流程；使用现有 Ant Design Table、DatePicker、Tag、message 和下载能力。

- [ ] **Step 3: 接入路由和导航**

新增 `/promotion/orders` 路由和“推广管理/订单结算”导航项；仅 `ROLE_ADMIN` 可见。保留现有目录、媒体账号报白和分佣规则页面，不把订单同步塞进 `ScheduledTaskPage`。

- [ ] **Step 4: 运行管理端验证**

在 `E:/JavaProjects/kasi-project/kasi-admin-web` 运行 `pnpm exec vitest run src/pages/promotion/PromotionOrderPage.test.tsx` 和 `pnpm run build`，预期均通过。

### Task 9: 用户端增加月度佣金视图

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/promotionOrderApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/promotionOrderTypes.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionIncomePage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionIncomePage.test.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/app/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/layouts/AccountLayout.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/README.md`

- [ ] **Step 1: 写页面失败测试**

用 MSW 验证用户只能看到自己的月度佣金、订单数和订单明细；未归因订单不出现；加载、空数据、接口错误和 CSV 下载均有可见状态。

- [ ] **Step 2: 实现最小页面**

页面只提供月份选择、税前/可结佣金额、订单数、退款扣减和明细表；不展示 GoodShort 原始金额字段，不伪造 `PromotionTask` 的点击/引流/广告统计。链接入口继续指向 `/promotion/links`。

- [ ] **Step 3: 接入路由**

新增 `/promotion/income`，导航名称为“佣金明细”；`/promotion/tasks` 直接 `Navigate` 到 `/promotion/links`，避免未实现统计继续对外可见。

- [ ] **Step 4: 运行用户端验证**

在 `E:/JavaProjects/kasi-project/kasi-user-web` 运行 `pnpm exec vitest run src/pages/promotion/PromotionIncomePage.test.tsx src/pages/promotion/PromotionLinkPage.test.tsx src/app/AppRouter.test.tsx` 和 `pnpm run build`，预期零错误。

### Task 10: 发布前迁移、回归和文档收口

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/development-gaps.md`
- Modify: `docs/architecture-decisions.md`
- Modify: `docs/superpowers/specs/2026-08-24-short-drama-cps-completion-scope.md`
- Test: all backend and frontend test suites

- [ ] **Step 1: 更新当前事实**

只在实现、迁移和测试完成后把短剧订单闭环标为“已实现”；明确 `PromotionTask`、CapCut、CPA/CPM、自动同步、账单/钱包仍为后续缺口。同步记录 ADR：订单按 `(connection_id, external_order_id)` 幂等，归因只走 `customParams -> trackingNo -> promotion_link -> user`，佣金使用导入时费率快照。

- [ ] **Step 2: 重建开发数据库并验证 Flyway**

按 `AGENTS.md` 的开发约束删除并重建本地开发库，使用 `./mvnw.cmd spring-boot:run` 验证 V1、V13、V14、V15 顺序执行；测试库使用 H2/MySQL 模式，不依赖本机 MySQL 数据。

- [ ] **Step 3: 运行后端完整验证**

在 Java 25 环境运行：

```powershell
./mvnw.cmd test
./mvnw.cmd -DskipTests compile
git diff --check
```

预期完整测试零失败、编译零错误、`git diff --check` 无输出。若只因工作区已有未提交 `PromotionTask` 测试失败，先按其真实失败原因修复或在发布分支明确排除，不能把占位统计当作已上线能力。

- [ ] **Step 4: 运行两个前端完整验证**

分别在 admin-web 和 user-web 执行 `pnpm test`、`pnpm run build`；验收浏览器流程：用户登录生成链接，管理员同步同一日期窗口两次，订单数量不重复，用户月度佣金可见，CSV 可下载。

- [ ] **Step 5: 形成发布清单**

发布前只提交本计划涉及的业务文件和文档；工作区已有的 `V13__promotion_task.sql`、`PromotionTask*` 及其他用户改动不得被误删或误标记为首发闭环。记录迁移版本、回滚动作（停用新端点并恢复上一应用版本）和首个同步窗口，完成上线签字。

## 不进入首发的后续切分

1. CapCut 人工文件导入：单独设计 `capcut_source_record`，保留 TalentID、发稿日期和原始行，不复用 GoodShort 订单表。
2. 自动订单同步：在手动同步稳定后再增加 `system_scheduled_task` task code、租约、检查点和失败重试。
3. 规则时间线：把当前规则覆盖模型升级为按生效时间匹配；首发只保存变更历史和订单快照。
4. 正式账单与付款：在订单数据和财务对账口径稳定后，再设计 bill/detail、锁定、开票、付款和钱包域。
5. CPA/CPM/通用项目：以独立需求重新评估领域模型，禁止在本次 `promotion_order` 中加入 `if (projectName)` 分支。

## 自检结果

- **规格覆盖：** 首发闭环、现有 PromotionLink 复用、GoodShort 手动同步、原始订单保留、trackingNo 归因、历史费率快照、管理员/用户查询导出、权限、迁移、前端路由和回归验证均有对应任务。
- **占位符扫描：** 计划不使用英文待定标记或“补测试”等不可执行描述；外部接口不确定性被收敛为 Task 1 的契约验收门槛。
- **类型一致性：** `ProviderOrderRecord` -> `PromotionOrder` -> `PromotionOrderVO` 的字段链路固定；`PromotionOrderSyncDTO` 与前端 `PromotionOrderSyncRequest` 使用相同的 `providerId/connectionId/from/to` 字段；费率字段沿用 `ProviderCommissionRule` 和 `ProviderCommissionCalculator` 的五个名称。
- **范围检查：** 未新增 CapCut、CPA、CPM、通用项目表、TikTok-CapCut 关系、自动订单任务、账单/钱包或第二套 trackingNo。

Plan complete and saved to `docs/superpowers/plans/2026-08-24-short-drama-cps-fast-launch.md`. Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh worker per task with review checkpoints.
2. **Inline Execution** - execute this plan in the current session using executing-plans checkpoints.
