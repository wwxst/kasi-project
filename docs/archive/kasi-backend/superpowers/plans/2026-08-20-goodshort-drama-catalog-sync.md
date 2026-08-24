# GoodShort 短剧目录同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (推荐) 或 superpowers:executing-plans。Steps use checkbox (- [ ] ) syntax for tracking.

**Goal:** 在现有 GoodShort 接入基础上，交付可定时运行、可由管理员立即触发、可断点恢复且幂等的短剧目录和免费剧集同步能力。

**Architecture:** 在现有 ProviderAdapter 体系上增加目录同步适配器和统一记录模型；GoodShort 负责签名、HTTP 请求和字段映射。drama 领域负责目录、剧集、同步检查点、数据库租约和管理员 API；手动触发只创建任务，定时器负责实际拉取。

**Tech Stack:** Java 25、Spring Boot 4、Spring MVC、Spring Scheduling、MyBatis 4、Flyway、MySQL 8、H2、RestClient、JUnit 5、AssertJ、MockMvc、MockRestServiceServer。

---

## 固定契约

- 只接入 GOODSHORT，不新增 DramaBox。
- 默认语言 ENGLISH，配置键 app.promotion.drama.sync.languages，逗号分隔；本计划不增加语言配置前端。
- 全量调用 POST /open/book/initBooks，增量调用 POST /open/book/incrementBooks。
- 请求包含 pageNo、pageSize、language、pid、timestamp；增量另传 updateTime。
- 成功响应必须同时满足 status == 0 和 success == true；GoodShort DTO 不得进入 drama 服务。
- 书籍统一字段 externalDramaId、title、originalTitle、description、coverUrl、language、dramaType、remoteShowStatus、remoteUpdatedAt、contents。
- 剧集统一字段 externalContentId、sequenceNo、title、free、durationSeconds、remoteUpdatedAt。
- 目录唯一键为 (connection_id, external_drama_id)，剧集唯一键为 (drama_id, sequence_no)；远端未返回记录不删除，远端和本地状态分离。
- 目录同步要求完整 API 凭据；人工报白的空凭据连接跳过。

## Task 1: GoodShort 目录适配器

**Files:**
- Create: src/main/java/com/kasi/backend/provider/spi/DramaCatalogProviderAdapter.java
- Create: src/main/java/com/kasi/backend/provider/spi/DramaCatalogFetchRequest.java
- Create: src/main/java/com/kasi/backend/provider/spi/DramaCatalogPage.java
- Create: src/main/java/com/kasi/backend/provider/spi/ProviderDramaRecord.java
- Create: src/main/java/com/kasi/backend/provider/spi/ProviderDramaContentRecord.java
- Create: src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortCatalogResponse.java
- Create: src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortBookData.java
- Create: src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortEpisodeData.java
- Modify: src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java
- Test: src/test/java/com/kasi/backend/provider/goodshort/GoodShortCatalogAdapterTest.java

- [ ] Step 1: 写失败测试。使用现有 MockRestServiceServer 和固定 Clock，断言 initBooks 的 POST 路径、语言/分页/PID/时间戳、签名头和书籍/剧集映射；增量断言 incrementBooks 与 updateTime；另测业务失败、空 data、5xx、429、网络异常。
- [ ] Step 2: 运行失败测试。设置 Java 25 后执行 .\mvnw.cmd '-Dtest=GoodShortCatalogAdapterTest' test，预期因目录 SPI 不存在而失败。
- [ ] Step 3: 实现 SPI。定义 fetchFullDramas 和 fetchIncrementalDramas；请求包含 language、pageNo、pageSize、updateTime；页包含 items、pageNo、pageSize、total、hasNext、nextUpdateTime；统一记录使用固定契约字段。
- [ ] Step 4: 实现 GoodShort 映射。让 GoodShortAdapter 同时实现 AccountFilingProviderAdapter、DramaCatalogProviderAdapter；复用 GoodShortSigner 和异常转换，映射 bookId、bookName、originalBookName、introduction、cover、language、type、showStatus、updateTime 以及 episodeId、episodeNo、title、isFree、duration、updateTime；目录和报备使用不同响应 DTO。
- [ ] Step 5: 运行 GoodShortCatalogAdapterTest、GoodShortAdapterTest、GoodShortFilingAdapterTest，执行 git diff --check，提交 feat: add GoodShort drama catalog adapter。

## Task 2: 目录表和 MyBatis 持久层

**Files:**
- Create: src/main/resources/db/migration/V7__drama_catalog_sync.sql
- Create: src/main/java/com/kasi/backend/drama/entity/ProviderDrama.java
- Create: src/main/java/com/kasi/backend/drama/entity/ProviderDramaContent.java
- Create: src/main/java/com/kasi/backend/drama/entity/ProviderSyncCheckpoint.java
- Create: src/main/java/com/kasi/backend/drama/enums/DramaLocalStatus.java
- Create: src/main/java/com/kasi/backend/drama/enums/DramaSyncType.java
- Create: src/main/java/com/kasi/backend/drama/enums/DramaSyncStatus.java
- Create: src/main/java/com/kasi/backend/drama/mapper/ProviderDramaMapper.java
- Create: src/main/java/com/kasi/backend/drama/mapper/ProviderSyncCheckpointMapper.java
- Create: src/main/resources/mapper/ProviderDramaMapper.xml
- Create: src/main/resources/mapper/ProviderSyncCheckpointMapper.xml
- Modify: src/test/resources/test-schema.sql
- Modify: src/test/java/com/kasi/backend/BaseAuthTest.java
- Modify: src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java
- Test: src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java

- [ ] Step 1: 写失败测试。迁移测试断言三表、默认值、唯一约束和外键；持久层测试覆盖短剧/剧集 upsert、分页、详情、检查点、租约互斥和过期接管。
- [ ] Step 2: 设置 Java 25，运行 MediaAccountFilingMigrationTest、DramaCatalogPersistenceTest，预期新表或 Mapper 不存在。
- [ ] Step 3: 添加 V7 和 H2 结构。provider_drama 保存连接、外部 ID、标题、简介、封面、语言、类型、远端状态、本地 DRAFT/PUBLISHED/OFFLINE、远端更新时间和 last_seen_at；provider_drama_content 保存外部剧集 ID、序号、标题、免费标记、时长和远端更新时间；provider_sync_checkpoint 保存连接、FULL/INCREMENTAL、语言、IDLE/REQUESTED/RUNNING/SUCCESS/FAILED、页码、update_time、成功/运行时间、统计、错误和租约。所有表 utf8mb4；唯一键和外键按固定契约；迁移不写 CREATE DATABASE 或 USE。
- [ ] Step 4: 实现 Mapper。目录 Mapper 提供按 ID/外部 ID查询、远端字段 upsert、分页 count/list、剧集查询和 upsert；检查点 Mapper 提供 find、insert、requestRun、findDue、claimLease、updateProgress、markSuccess、markFailure。upsert 不覆盖 local_status；剧集不删除；租约领取必须检查状态、过期时间和影响行数。BaseAuthTest 清理顺序为 provider_drama_content、provider_drama、provider_sync_checkpoint、provider_media_filing、promotion_media_account。
- [ ] Step 5: 运行持久层测试和 git diff --check，提交 feat: persist drama catalog sync state。

## Task 3: 同步服务、配置和断点恢复

**Files:**
- Create: src/main/java/com/kasi/backend/drama/config/DramaSyncProperties.java
- Create: src/main/java/com/kasi/backend/drama/service/DramaCatalogSyncService.java
- Create: src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java
- Create: src/main/java/com/kasi/backend/drama/vo/DramaSyncTaskVO.java
- Modify: src/main/resources/application.properties
- Test: src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java

- [ ] Step 1: 写 Mockito 失败测试。覆盖无成功全量时增量升级全量、页面成功后才推进页码、第二页异常保留第一页检查点、租约单实例领取、人工报白空凭据不调用目录适配器。
- [ ] Step 2: 运行 DramaCatalogSyncServiceTest，预期服务类不存在。
- [ ] Step 3: 添加 DramaSyncProperties。前缀 app.promotion.drama.sync；默认 schedulerEnabled=true、fixedDelay=5m、batchSize=10、pageSize=100、leaseDuration=2m、languages=[ENGLISH]；同步类型 FULL/INCREMENTAL，状态 IDLE/REQUESTED/RUNNING/SUCCESS/FAILED。
- [ ] Step 4: 实现 requestSync(providerId,type,languages)、getStatuses(providerId)、processDueBatch()。手动请求只标记 REQUESTED；处理时先原子领取租约，再 resolve FULL_DRAMA_SYNC 或 INCREMENTAL_DRAMA_SYNC；无全量成功点时从第一页全量；每页 upsert 成功后才推进检查点；异常只写失败和释放租约；不删除历史记录；人工报白空凭据连接跳过。
- [ ] Step 5: 运行 DramaCatalogSyncServiceTest、DramaCatalogPersistenceTest、git diff --check，提交 feat: orchestrate drama catalog synchronization。

## Task 4: 管理员查询、详情、上下架和同步 API

**Files:**
- Create: src/main/java/com/kasi/backend/drama/dto/DramaPageQueryDTO.java
- Create: src/main/java/com/kasi/backend/drama/dto/RequestDramaSyncDTO.java
- Create: src/main/java/com/kasi/backend/drama/dto/UpdateDramaLocalStatusDTO.java
- Create: src/main/java/com/kasi/backend/drama/vo/DramaListItemVO.java
- Create: src/main/java/com/kasi/backend/drama/vo/DramaPageVO.java
- Create: src/main/java/com/kasi/backend/drama/vo/DramaDetailVO.java
- Create: src/main/java/com/kasi/backend/drama/vo/DramaContentVO.java
- Create: src/main/java/com/kasi/backend/drama/vo/DramaSyncStatusVO.java
- Create: src/main/java/com/kasi/backend/drama/service/DramaCatalogAdminService.java
- Create: src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogAdminServiceImpl.java
- Create: src/main/java/com/kasi/backend/drama/controller/AdminDramaCatalogController.java
- Modify: src/main/java/com/kasi/backend/common/exception/ErrorCode.java
- Test: src/test/java/com/kasi/backend/drama/controller/AdminDramaCatalogControllerTest.java
- Test: src/test/java/com/kasi/backend/drama/service/DramaCatalogAdminServiceTest.java

- [ ] Step 1: 写失败测试。继承 BaseAuthTest，覆盖 GET /api/admin/drama/catalog、GET /{id}、POST /sync、GET /sync/status、PATCH /{id}/status；普通管理员可读/同步/上下架，推广用户 403，匿名 401，DTO 非法返回 1006；上下架只改 local_status。
- [ ] Step 2: 运行 API 测试，预期 DTO、VO、服务和控制器不存在。
- [ ] Step 3: 实现 DTO/VO。查询支持 page、size、title、language、remoteShowStatus、localStatus；同步使用 @NotNull DramaSyncType 和可选语言列表；上下架使用 @NotNull DramaLocalStatus；详情不返回连接 ID、PID、密钥、租约持有者或内部检查点字段。
- [ ] Step 4: 实现 AdminDramaCatalogController，路径 /api/admin/drama/catalog；请求体和查询 DTO 使用 @Valid。复用现有 /api/admin/drama/** ROLE_ADMIN 规则，不增加用户端路由。
- [ ] Step 5: 运行 AdminDramaCatalogControllerTest、DramaCatalogAdminServiceTest、git diff --check，提交 feat: expose admin drama catalog APIs。

## Task 5: 定时调度

**Files:**
- Create: src/main/java/com/kasi/backend/drama/task/DramaCatalogScheduler.java
- Modify: src/main/java/com/kasi/backend/promotion/config/MediaFilingTaskConfig.java
- Modify: src/test/resources/application-test.properties
- Test: src/test/java/com/kasi/backend/drama/task/DramaCatalogSchedulerTest.java

- [ ] Step 1: 写失败测试，断言 processDueDramas 委托 DramaCatalogSyncService.processDueBatch，关闭 scheduler-enabled 时不创建调度器。
- [ ] Step 2: 实现 ConditionalOnProperty 和 Scheduled fixedDelay 5m；在现有 MediaFilingTaskConfig 注册 DramaSyncProperties，不重复声明 EnableScheduling。
- [ ] Step 3: 运行 DramaCatalogSchedulerTest、DramaCatalogSyncServiceTest、AdminDramaCatalogControllerTest、DramaCatalogPersistenceTest、GoodShortCatalogAdapterTest，提交 feat: schedule drama catalog sync。

## Task 6: 文档和全量验证

**Files:**
- Modify: README.md
- Modify: AGENTS.md
- Modify: docs/superpowers/specs/2026-08-20-goodshort-drama-catalog-sync-design.md
- Test: all existing backend tests

- [ ] Step 1: 记录 V7、三张表、GoodShort 全量/增量、默认 ENGLISH、定时/手动触发、管理员接口、本地上下架和未实现的推广链接/分佣/订单/转化。
- [ ] Step 2: Java 25 下运行目录聚焦测试：GoodShortCatalogAdapterTest、DramaCatalogPersistenceTest、DramaCatalogSyncServiceTest、DramaCatalogAdminServiceTest、AdminDramaCatalogControllerTest、DramaCatalogSchedulerTest；预期 Failures: 0、Errors: 0。
- [ ] Step 3: Java 25 下运行 mvnw test、mvnw -DskipTests compile、git diff --check，三条命令都退出 0 后才能报告完成。
- [ ] Step 4: 检查 git status、git diff --stat、git diff --name-only；只暂存 README.md、AGENTS.md 和设计文档，确认 dump.rdb 未暂存，提交 docs: document GoodShort drama catalog sync。

## 自检结果

- 规格覆盖：Task 1 适配器，Task 2 三表和持久层，Task 3 配置/全量/增量/断点/租约，Task 4 五个管理员 API，Task 5 调度，Task 6 文档和全量验证。
- 占位扫描：每个任务都有文件、测试命令和预期结果，没有未定义的实施步骤。
- 类型一致性：DramaCatalogProviderAdapter、DramaCatalogFetchRequest、DramaCatalogPage、DramaSyncType、DramaSyncStatus 在各任务中保持一致。
- 范围检查：不实现推广链接、素材下载、分佣、订单、导出或转化分析。
