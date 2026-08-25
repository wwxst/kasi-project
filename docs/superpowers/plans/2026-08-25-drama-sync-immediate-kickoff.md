# 短剧目录手动同步即时唤醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 手动提交短剧目录同步后，在事务提交后立即异步唤醒目录执行器，同时保留 5 分钟定时扫描兜底。

**Architecture:** `requestSync` 只负责事务内创建/请求检查点；事务提交后由受控的单线程执行器调用既有 `processDueBatch()`。数据库租约仍是唯一领取协调机制，定时器继续调用同一入口。

**Tech Stack:** Spring Boot scheduling/transactions, Java 25, JUnit 5, Mockito。

---

### Task 1: 定义可注入的即时触发边界

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncTrigger.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncTriggerImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`

- [x] **Step 1: Write the failing test**

在 `DramaCatalogSyncServiceTest` 中注入 mock trigger，验证 `requestSync` 注册提交后触发；回滚场景不触发。

- [x] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=DramaCatalogSyncServiceTest test`
Expected: 编译失败，因为触发器接口和构造依赖尚不存在。

- [x] **Step 3: Write minimal implementation**

新增 `DramaCatalogSyncTrigger.trigger()` 接口和单线程实现；`requestSync` 在成功创建任务后注册 `TransactionSynchronization.afterCommit`，回调调用触发器。触发器只负责异步调用 `processDueBatch()`，异常记录日志。

- [x] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd -Dtest=DramaCatalogSyncServiceTest test`
Expected: 目标测试全部通过。

### Task 2: 保持定时兜底并覆盖触发失败边界

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/task/DramaCatalogScheduler.java`
- Test: `src/test/java/com/kasi/backend/drama/task/DramaCatalogSchedulerTest.java` 或现有调度测试文件

- [x] **Step 1: Write the failing test**

验证定时器仍直接调用 `processDueBatch()`，并验证异步触发器提交失败不会改变检查点状态。

- [x] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=DramaCatalogSchedulerTest,DramaCatalogSyncServiceTest test`
Expected: 新增断言在即时触发未实现前失败。

- [x] **Step 3: Write minimal implementation**

保持 `DramaCatalogScheduler` 的固定延迟和入口不变；为触发器配置单线程执行器，异步提交异常仅记录日志。

- [x] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd -Dtest=DramaCatalogSchedulerTest,DramaCatalogSyncServiceTest test`
Expected: 目标测试全部通过。

### Task 3: 文档和完整验证

**Files:**
- Modify: `README.md`（同步行为说明）

- [x] **Step 1: Update current behavior documentation**

说明手动同步事务提交后会立即异步唤醒，定时器仍每 5 分钟兜底；明确前端状态窗口仍需刷新查看。

- [x] **Step 2: Run focused and full verification**

Run: `./mvnw.cmd -Dtest=DramaCatalogSyncServiceTest,DramaCatalogSchedulerTest test`

Run: `./mvnw.cmd test`

Run: `git diff --check`

Expected: 命令退出码为 0，测试无失败，差异检查无输出。
