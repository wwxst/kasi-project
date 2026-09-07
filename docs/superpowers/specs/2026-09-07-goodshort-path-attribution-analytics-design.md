# GoodShort 路径、链接归因与转化报表设计

## 目标

修复 GoodShort 第三方接口路径不统一、短剧目录分页超过甲方上限、订单仅按用户级归因的问题，并补齐管理端转化报表展示链路。

## 范围

本次只修改 GoodShort provider 适配、目录同步分页、订单归因和管理端转化报表。暂不修改用户端月度佣金展示、结算/提现能力、甲方接口字段之外的业务模型或数据库迁移结构。

## 设计

### 1. GoodShort endpoint 规范化

将 provider connection 的 `baseUrl` 约定为来源站点根地址，例如 `https://api.novelopen.com`，所有 GoodShort endpoint 常量统一包含 `/creek`：

- `/creek/open/book/initBooks`
- `/creek/open/book/incrementBooks`
- `/creek/open/book/freeContent`
- `/creek/open/partner/orders`
- `/creek/open/inviteCode/generate/partner/code`
- `/creek/open/promotion/analyticalReport`
- `/creek/open/filing/report`
- `/creek/open/filing/query`

保存配置时去除末尾 `/`，不自动加入 `/creek`；适配器只负责使用统一常量，避免部分路径依赖 base URL 子路径。现有适配器测试将固定根域名并断言完整 `/creek/open/...` 请求路径。

### 2. 目录分页边界

将 `app.promotion.drama.sync.page-size` 默认值从 100 调整为 50，并在 GoodShort 目录请求边界将 page size 限制为 1 到 50。订单和分析报表仍保持 500，因为甲方分别允许最多 500 条。

### 3. 链接级归因

生成推广链接时把本地 `promotion_link.tracking_no` 作为 GoodShort `customParams`。订单同步时按以下顺序归因：

1. 用订单 `customParams` 查询 `promotion_link.tracking_no`；
2. 找到链接后写入 `tracking_no`、`promotion_link_id`、`user_id`、`drama_id`；
3. 找不到链接时保持 `UNATTRIBUTED`，不得按用户编号自动归属。

这使同一用户的多条链接可以分别统计，同时保留未匹配订单供管理端核对。订单原始 JSON、外部字段、佣金快照和退款语义保持不变。

### 4. 管理端转化报表

复用现有后端接口 `/api/admin/promotion/analytical-reports`：

- 新增 admin-web API 类型和 `list/sync` 请求封装；
- 增加转化报表页面和 `/promotion/analytical-reports` 路由；
- 增加推广管理菜单入口；
- 支持日期范围、`customParams`、`bookId`、`code` 查询；
- 展示报告日期、PID、推广追踪参数、短剧、口令、点击、归因用户、新注册、新付费、新会员、付费用户、订单数和订单金额；
- 同步操作只调用现有后端任务接口，不在浏览器直连 GoodShort。

## 错误和兼容性

- 第三方签名、错误映射和数据库表结构不改变。
- 未配置真实 GoodShort 或 MySQL 时，相关真实验收保持 `SKIP`。
- 不自动兼容含 `/creek` 的历史 baseUrl；部署配置需统一改为来源站点根地址，并由测试覆盖该约定。

## 验证

- GoodShort adapter 单元测试：8 个 endpoint 的完整路径、签名和链接级 customParams。
- 订单归因测试：匹配 trackingNo、未匹配保持未归因、同用户不同链接不混淆。
- 目录同步配置测试：默认 pageSize 为 50，发送值不超过 50。
- admin-web 报表 API、路由和页面测试。
- `kasi-backend` canonical `mvn verify`。
- `kasi-admin-web` 与 `kasi-user-web` canonical `pnpm check`。
- 根目录 `git diff --check`。
