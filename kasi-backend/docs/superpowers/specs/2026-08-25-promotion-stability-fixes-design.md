# Promotion Stability Fixes Design

日期：2026-08-25

## 目标

修复四个已确认的问题，保持现有 API、数据库结构和 GoodShort 业务契约不变：

1. 敏感账号变更在数据库回滚后不得长期停留在 Redis `MUTATING`。
2. 推广链接生成不得把 GoodShort HTTP 调用放在长数据库事务中，远程失败状态必须落库。
3. 订单同步只增加唯一键并发冲突兜底，不重写现有同步系统。
4. 管理后台不展示静态 Demo 数据，Dashboard 保留页面壳并显示欢迎语。

## 方案

### 1. 会话状态恢复

继续使用 `SessionService` 的统一事务完成回调。敏感操作进入 `MUTATING:{nonce}` 后，在事务提交和回滚的 `afterCompletion` 阶段按 nonce 写入新的 `ACTIVE:*` 版本。旧 Token 在两种结果下都失效，Redis 故障仍按现有 fail-closed 规则处理。

### 2. 推广链接事务边界

将 `createOrRetry` 拆成短事务与事务外编排：

- 短事务完成资格校验、requestKey 幂等读取，以及创建或重置 `PENDING` 记录。
- 事务提交后，在无数据库事务的上下文中调用 GoodShort HTTP。
- 远程成功或失败分别调用独立短事务，将记录写为 `SUCCESS` 或 `FAILED`。
- 保留 requestKey 唯一约束和并发锁；并发创建冲突时回读已存在记录，不重复生成第二条链接。

### 3. 订单唯一键兜底

在 `PromotionOrderServiceImpl.upsert` 的新记录插入处捕获 `DuplicateKeyException`。捕获后按 `(connectionId, externalOrderId)` 回读记录并返回 `created=false`，不改变已有订单的归因、佣金和同步分页流程。

### 4. Dashboard 展示

保留 `/dashboard` 页面路由和布局，但移除所有静态统计、图表和榜单组件，只显示居中的大号欢迎语“欢迎 XXX 使用卡司短剧推广平台”，其中 XXX 使用当前管理员真实姓名。侧边栏不再显示 Dashboard 菜单；品牌链接、搜索回车和通配路由统一跳转 `/user-management`。

## 测试与验收

- 会话：覆盖数据库回滚后 Redis 版本恢复为 `ACTIVE` 且新会话可建立。
- 推广链接：覆盖远程成功、远程失败状态持久化，以及事务外 HTTP 调用边界。
- 订单：覆盖唯一键并发插入冲突后返回已存在记录且不抛出异常。
- Dashboard：覆盖静态 Demo 卡片不渲染、欢迎语渲染、导航和兜底跳转目标。
- Java 25 编译、受影响 Maven/Vitest 测试和 `git diff --check` 必须通过；完整套件若受执行时限影响需如实报告。

## 不在本次范围

- 不新增真实 Dashboard 数据接口或完整数据大屏。
- 不重写 GoodShort 订单同步调度、分页、归因或佣金计算。
- 不修改数据库表结构、唯一键或公开 API 路径。
