# 媒体账号绑定与通用报备实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现推广用户媒体账号绑定、不可转让归属、GoodShort 账号报备、三态审核结果、后台轮询重试和管理员查询/重试完整闭环。

**Architecture:** 复用平台接入模块提供的平台、接入账号、加密凭据、能力声明和 GoodShort HTTP/签名基础。`promotion` 模块只管理媒体账号、平台报备和持久任务；第三方字段转换留在 `provider.goodshort`，远程调用不进入数据库事务。

**Tech Stack:** Java 25, Spring Boot 4.0.7, Spring Scheduling, Spring Security, Jakarta Validation, MyBatis, MySQL 8, H2, Flyway, JUnit 5, AssertJ, Mockito, MockMvc, MockRestServiceServer.

---

## 执行前置

执行本计划前，必须先完成并验收 [GoodShort 平台接入计划](2026-08-17-goodshort-provider-connection.md) 中除已被 V2 取代的建表步骤之外的任务，仓库至少应已经存在并通过测试：

- `ShortDramaProviderMapper` 和 `ShortDramaConnectionMapper`
- `ProviderCredentialCipher`
- `ProviderAdapter`、`ProviderCapability` 和 `ProviderConnectionSecret`
- `GoodShortSigner`、`GoodShortAdapter`、统一 GoodShort 响应模型和固定 `Clock`
- 平台接入账号的管理、加密保存和连接测试

不能为了执行本计划再次创建 `short_drama_provider`、`short_drama_connection`，也不能修改 V1。当前 V2 已建立四张基础表；本计划仅增加业务代码和一个用于并发隔离的向前迁移。

## 模块边界

本计划交付：

- 用户绑定、查询、修改、启停多个媒体账号。
- `(media_type, external_account_id)` 全局唯一和永久归属。
- 同一媒体账号向多个短剧平台分别报备。
- GoodShort `/open/filing/report` 和 `/open/filing/query` 对接。
- `PENDING / APPROVED / FAILED` 三态映射。
- 持久轮询、指数退避、多实例租约和旧资料版本隔离。
- 管理员分页筛选、详情和失败报备重试。
- 已绑定媒体账号的推广用户禁止物理删除。

本计划不交付：短剧目录、免费内容、分佣、推广链接、口令、订单、导出和转化分析。

## 文件结构

```text
promotion/
├─ controller/
│  ├─ UserMediaAccountController.java
│  └─ AdminMediaAccountController.java
├─ dto/
├─ entity/
│  ├─ PromotionMediaAccount.java
│  └─ ProviderMediaFiling.java
├─ enums/
│  ├─ MediaType.java
│  ├─ MediaAccountStatus.java
│  ├─ FilingStatus.java
│  └─ FilingAction.java
├─ mapper/
│  ├─ PromotionMediaAccountMapper.java
│  └─ ProviderMediaFilingMapper.java
├─ service/
│  ├─ MediaAccountService.java
│  ├─ MediaAccountAdminService.java
│  ├─ MediaAccountOwnershipService.java
│  ├─ MediaFilingTaskService.java
│  └─ impl/
├─ task/MediaFilingScheduler.java
└─ vo/

provider/
├─ service/ProviderRuntimeConnectionService.java
├─ service/impl/ProviderRuntimeConnectionServiceImpl.java
└─ spi/
   ├─ AccountFilingProviderAdapter.java
   ├─ AccountFilingSubmission.java
   ├─ AccountFilingQuery.java
   ├─ AccountFilingResult.java
   └─ ProviderRuntimeConnection.java
```

每个 Mapper 只写一张主表。跨表业务判断由 Service 编排；分页筛选允许主表查询通过 `EXISTS` 读取关联状态，但不能在一个 Mapper 中更新多张表。

### Task 1: 补齐报备任务资料版本契约

**Files:**
- Create: `src/main/resources/db/migration/V3__media_filing_task_version.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java`

- [ ] **Step 1: 写失败的迁移测试**

在现有 `migrateV2CreatesMediaAccountFilingSchema` 后新增：

```java
@Test
@DisplayName("V3为报备任务保存独立资料版本")
void migrateV3AddsFilingTaskDataVersion() {
    JdbcTemplate jdbc = migrateAllMigrations();
    Integer defaultValue = jdbc.queryForObject(
            "SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME = 'PROVIDER_MEDIA_FILING' AND COLUMN_NAME = 'TASK_DATA_VERSION'",
            Integer.class);
    assertThat(defaultValue).isEqualTo(1);
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest test
```

Expected: FAIL，因为 `TASK_DATA_VERSION` 不存在。

- [ ] **Step 3: 添加最小 V3**

```sql
ALTER TABLE `provider_media_filing`
    ADD COLUMN `task_data_version` INT NOT NULL DEFAULT 1
        COMMENT '当前异步任务对应的媒体账号资料版本'
        AFTER `submitted_data_version`;
```

在 `test-schema.sql` 的同一位置增加 `task_data_version INT NOT NULL DEFAULT 1`。

- [ ] **Step 4: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest test
```

Expected: 迁移测试零失败、零错误。

- [ ] **Step 5: 提交迁移契约**

```powershell
git add src/main/resources/db/migration/V3__media_filing_task_version.sql src/test/resources/test-schema.sql src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java
git commit -m "feat: version media filing tasks"
```

### Task 2: 实现媒体账号与报备持久层

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/enums/MediaType.java`
- Create: `src/main/java/com/kasi/backend/promotion/enums/MediaAccountStatus.java`
- Create: `src/main/java/com/kasi/backend/promotion/enums/FilingStatus.java`
- Create: `src/main/java/com/kasi/backend/promotion/enums/FilingAction.java`
- Create: `src/main/java/com/kasi/backend/promotion/entity/PromotionMediaAccount.java`
- Create: `src/main/java/com/kasi/backend/promotion/entity/ProviderMediaFiling.java`
- Create: `src/main/java/com/kasi/backend/promotion/mapper/PromotionMediaAccountMapper.java`
- Create: `src/main/java/com/kasi/backend/promotion/mapper/ProviderMediaFilingMapper.java`
- Create: `src/main/resources/mapper/PromotionMediaAccountMapper.xml`
- Create: `src/main/resources/mapper/ProviderMediaFilingMapper.xml`
- Create: `src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java`

- [ ] **Step 1: 写失败的 Mapper 测试**

测试继承 `BaseAuthTest`，在每个测试中插入一条测试连接。覆盖：

```java
@Test
@DisplayName("同一媒体平台账号只能绑定一次")
void mediaIdentityIsGloballyUnique() {
    PromotionMediaAccount first = mediaAccount(primaryUserId(), "TIKTOK", "creator-1");
    assertThat(mediaAccountMapper.insert(first)).isEqualTo(1);

    PromotionMediaAccount duplicate = mediaAccount(mobileUserId(), "TIKTOK", "creator-1");
    assertThatThrownBy(() -> mediaAccountMapper.insert(duplicate))
            .isInstanceOf(DuplicateKeyException.class);
}

@Test
@DisplayName("同一接入账号和媒体账号只保留一条报备")
void filingIsUniquePerConnectionAndMediaAccount() {
    Long mediaId = insertMediaAccount();
    Long connectionId = insertConnection();
    assertThat(filingMapper.insert(pendingFiling(connectionId, mediaId, 1))).isEqualTo(1);
    assertThatThrownBy(() -> filingMapper.insert(pendingFiling(connectionId, mediaId, 1)))
            .isInstanceOf(DuplicateKeyException.class);
}
```

另测：本人列表、`findOwnedById`、`findByIdForUpdate`、身份重复查询、资料版本递增、启停、按媒体账号批量查报备、失败报备重置、租约条件更新和按 `task_data_version + lease_owner` 条件写回。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingPersistenceTest test
```

Expected: 编译失败，因为枚举、Entity 和 Mapper 不存在。

- [ ] **Step 3: 实现枚举和纯 Entity**

```java
public enum MediaType { TIKTOK, FACEBOOK, YOUTUBE, INSTAGRAM }
public enum FilingStatus { PENDING, APPROVED, FAILED }
public enum FilingAction { SUBMIT, QUERY, NONE }

@RequiredArgsConstructor
@Getter
public enum MediaAccountStatus {
    DISABLED(0), ENABLED(1);
    private final int code;
}
```

Entity 使用 Lombok `@Data`，只映射表字段，不包含业务方法。时间使用 `LocalDateTime`。

- [ ] **Step 4: 实现一表一 Mapper**

`PromotionMediaAccountMapper` 至少提供：

```java
PromotionMediaAccount findById(Long id);
PromotionMediaAccount findOwnedById(Long id, Long userId);
PromotionMediaAccount findByIdForUpdate(Long id);
PromotionMediaAccount findByIdentity(MediaType mediaType, String externalAccountId);
List<PromotionMediaAccount> findByUserId(Long userId);
long countByUserId(Long userId);
int insert(PromotionMediaAccount entity);
int updateDetails(PromotionMediaAccount entity);
int updateStatus(Long id, Integer status);
```

`ProviderMediaFilingMapper` 至少提供：

```java
ProviderMediaFiling findById(Long id);
ProviderMediaFiling findByConnectionAndMedia(Long connectionId, Long mediaAccountId);
List<ProviderMediaFiling> findByMediaAccountId(Long mediaAccountId);
List<ProviderMediaFiling> findByMediaAccountIds(List<Long> mediaAccountIds);
int insert(ProviderMediaFiling entity);
int enqueue(Long id, FilingStatus status, FilingAction action, int taskDataVersion, LocalDateTime nextActionAt);
List<Long> findDueIds(LocalDateTime now, int limit);
int claimLease(Long id, String owner, LocalDateTime now, LocalDateTime leaseUntil);
int completeSubmit(Long id, String owner, int taskDataVersion, LocalDateTime submittedAt, LocalDateTime nextQueryAt);
int completeQuery(ProviderMediaFiling entity, String owner, int taskDataVersion);
int recordRetry(ProviderMediaFiling entity, String owner, int taskDataVersion);
```

所有任务写回 SQL 同时包含 `id`、`lease_owner` 和 `task_data_version` 条件。领取租约使用条件更新，确保多实例只有一个实例返回更新行数 1。

- [ ] **Step 5: 运行 Mapper 测试并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingPersistenceTest test
```

- [ ] **Step 6: 提交持久层**

```powershell
git add src/main/java/com/kasi/backend/promotion/enums src/main/java/com/kasi/backend/promotion/entity src/main/java/com/kasi/backend/promotion/mapper src/main/resources/mapper/PromotionMediaAccountMapper.xml src/main/resources/mapper/ProviderMediaFilingMapper.xml src/test/java/com/kasi/backend/promotion/mapper/MediaAccountFilingPersistenceTest.java
git commit -m "feat: persist media accounts and filings"
```

### Task 3: 建立运行时平台连接和报备 SPI

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/spi/AccountFilingProviderAdapter.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/AccountFilingSubmission.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/AccountFilingQuery.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/AccountFilingResult.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/ProviderRuntimeConnection.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/ProviderAdapterRegistry.java`
- Create: `src/main/java/com/kasi/backend/provider/service/ProviderRuntimeConnectionService.java`
- Create: `src/main/java/com/kasi/backend/provider/service/impl/ProviderRuntimeConnectionServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/provider/exception/ProviderTransientException.java`
- Create: `src/main/java/com/kasi/backend/provider/exception/ProviderRemoteRejectedException.java`
- Create: `src/test/java/com/kasi/backend/provider/service/ProviderRuntimeConnectionServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`

- [ ] **Step 1: 写失败的运行时连接测试**

使用 Mockito 覆盖：平台不存在、平台禁用、没有连接、连接禁用、密钥解密失败、适配器不支持 `ACCOUNT_FILING`、成功返回不含密文的短生命周期上下文。

```java
@Test
@DisplayName("运行时连接只向适配器提供解密后的短生命周期密钥")
void resolveReturnsRuntimeSecretWithoutCiphertext() {
    when(providerMapper.findById(1L)).thenReturn(enabledProvider());
    when(connectionMapper.findByProviderId(1L)).thenReturn(enabledConnection("v1:cipher"));
    when(cipher.decrypt("v1:cipher")).thenReturn("remote-key");
    when(registry.require("GOODSHORT")).thenReturn(accountFilingAdapter());

    ProviderRuntimeConnection result = service.resolve(1L, ProviderCapability.ACCOUNT_FILING);

    assertThat(result.connectionId()).isEqualTo(10L);
    assertThat(result.secret().apiKey()).isEqualTo("remote-key");
    assertThat(result.toString()).doesNotContain("remote-key", "v1:cipher");
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=ProviderRuntimeConnectionServiceTest test
```

- [ ] **Step 3: 定义细粒度报备契约**

```java
public interface AccountFilingProviderAdapter extends ProviderAdapter {
    Set<MediaType> supportedMediaTypes();
    void submitAccountFiling(ProviderConnectionSecret secret, AccountFilingSubmission submission);
    AccountFilingResult queryAccountFiling(ProviderConnectionSecret secret, AccountFilingQuery query);
}

public record AccountFilingResult(
        FilingStatus status,
        String remoteStatus,
        String externalFilingId,
        LocalDateTime filingTime,
        LocalDateTime operateTime) {}

public record ProviderRuntimeConnection(
        Long connectionId,
        Long providerId,
        String providerCode,
        String providerName,
        ProviderConnectionSecret secret,
        ProviderAdapter adapter) {
    @Override
    public String toString() {
        return "ProviderRuntimeConnection[connectionId=" + connectionId
                + ", providerId=" + providerId
                + ", providerCode=" + providerCode + "]";
    }
}
```

`AccountFilingSubmission` 包含媒体类型、账号 ID、名称和主页链接；`AccountFilingQuery` 只包含媒体类型和账号 ID。

`ProviderTransientException` 只表示超时、限流、连接失败和 5xx 等可重试异常；`ProviderRemoteRejectedException` 表示平台明确拒绝请求。两者只保存脱敏错误类型和稳定消息，不保存请求体、签名前原文、明文密钥或第三方完整响应。

- [ ] **Step 4: 实现注册表和运行时解析**

`ProviderAdapterRegistry` 构造时按 `providerCode()` 建不可变 Map，重复编码启动失败。`ProviderRuntimeConnectionService.resolve(providerId, capability)` 依次校验平台、连接、状态、能力和密钥，最后返回 `connectionId/providerId/providerCode/providerName/secret/adapter`。VO、日志和 `toString()` 不得暴露明文或密文。

模块 1 的 6001-6006 平台错误码继续复用；只补实际可达且尚不存在的能力不支持错误，不为订单等后续模块预留错误码。

- [ ] **Step 5: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=ProviderRuntimeConnectionServiceTest test
```

- [ ] **Step 6: 提交运行时契约**

```powershell
git add src/main/java/com/kasi/backend/provider/spi src/main/java/com/kasi/backend/provider/service/ProviderRuntimeConnectionService.java src/main/java/com/kasi/backend/provider/service/impl/ProviderRuntimeConnectionServiceImpl.java src/main/java/com/kasi/backend/provider/exception src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/test/java/com/kasi/backend/provider/service/ProviderRuntimeConnectionServiceTest.java
git commit -m "feat: resolve provider filing connections"
```

### Task 4: 实现 GoodShort 账号报备适配器

**Files:**
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortFilingReportRequest.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortFilingQueryRequest.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortFilingData.java`
- Create: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortFilingAdapterTest.java`

- [ ] **Step 1: 写失败的提交报备 HTTP 测试**

使用固定 `Clock` 和 `MockRestServiceServer`，断言请求：

```json
{
  "pid": "partner-1",
  "timestamp": 1681810530092,
  "type": "ACCOUNT",
  "media": "TIKTOK",
  "accountId": "creator-1",
  "accountName": "Creator One",
  "accountLink": "https://www.tiktok.com/@creator-1"
}
```

断言路径 `/open/filing/report`、`sign` 请求头、成功双条件 `status == 0 && success == true`。名称或链接为 null 时不进入签名和 JSON。

- [ ] **Step 2: 写失败的查询状态测试**

断言 `/open/filing/query` 请求只包含 `pid/timestamp/type/media/accountId`，并覆盖：

```java
assertThat(queryWithRemoteStatus(0).status()).isEqualTo(FilingStatus.PENDING);
assertThat(queryWithRemoteStatus(1).status()).isEqualTo(FilingStatus.APPROVED);
assertThat(queryWithRemoteStatus(2).status()).isEqualTo(FilingStatus.FAILED);
```

另测未知状态、业务失败、429、5xx、超时和畸形响应。未知状态不能映射成 `APPROVED`。

- [ ] **Step 3: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=GoodShortFilingAdapterTest test
```

- [ ] **Step 4: 扩展 GoodShortAdapter**

让 `GoodShortAdapter` 实现 `AccountFilingProviderAdapter`，声明四种媒体类型以及 `ACCOUNT_FILING/FILING_STATUS_QUERY` 能力。复用模块 1 的 signer、RestClient、base URL、Clock 和统一响应模型，不复制签名实现。

第三方临时失败抛出可识别的 `ProviderTransientException`；明确业务拒绝或状态 2 返回/抛出最终失败；配置或密钥问题在远程调用前失败。异常对象不得包含请求签名前原文和密钥。

- [ ] **Step 5: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=GoodShortSignerTest,GoodShortAdapterTest,GoodShortFilingAdapterTest test
```

- [ ] **Step 6: 提交 GoodShort 报备适配**

```powershell
git add src/main/java/com/kasi/backend/provider/goodshort src/test/java/com/kasi/backend/provider/goodshort/GoodShortFilingAdapterTest.java
git commit -m "feat: integrate GoodShort account filing"
```

### Task 5: 实现推广用户媒体账号业务

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/dto/CreateMediaAccountDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/dto/UpdateMediaAccountDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/dto/UpdateMediaAccountStatusDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/MediaAccountVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/MediaAccountDetailVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/MediaAccountService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/MediaAccountOwnershipService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountOwnershipServiceImpl.java`
- Create: `src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`

- [ ] **Step 1: 写失败的 Service 测试**

覆盖：

- 一个用户绑定多个不同账号。
- 不同用户绑定相同 `mediaType + externalAccountId` 返回重复错误。
- 输入 trim，名称/链接空串转 null，链接只允许 HTTPS。
- 创建账号和首次 `PENDING + SUBMIT` 报备在一个事务语义中完成。
- 用户只能查询/修改/启停本人账号。
- 任一报备 `APPROVED` 后媒体类型和账号 ID 锁定。
- 仅名称/链接变化：资料版本递增，已加白保持 `APPROVED` 但重新排 `SUBMIT`。
- 身份变化：所有未加白报备回到 `PENDING + SUBMIT`。
- 停用账号不能修改、首次报备或重试；重新启用成功。
- `PENDING` 重试幂等，`FAILED` 重置，`APPROVED` 拒绝重试。
- 向第二个平台报备不改变第一个平台状态。

核心用例：

```java
@Test
@DisplayName("旧资料任务不能覆盖用户修正后的新资料")
void updatingIdentityAdvancesTaskDataVersion() {
    PromotionMediaAccount account = pendingAccount(1);
    when(mediaMapper.findByIdForUpdate(account.getId())).thenReturn(account);
    when(filingMapper.findByMediaAccountId(account.getId())).thenReturn(List.of(pendingFiling(1)));

    service.update(account.getUserId(), account.getId(), update("TIKTOK", "correct-id"));

    verify(filingMapper).enqueue(
            anyLong(), eq(FilingStatus.PENDING), eq(FilingAction.SUBMIT), eq(2), any());
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountServiceTest test
```

- [ ] **Step 3: 添加 DTO 校验**

```java
@Data
public class CreateMediaAccountDTO {
    @NotNull private MediaType mediaType;
    @NotBlank @Size(max = 128) private String externalAccountId;
    @Size(max = 128) private String accountName;
    @Pattern(regexp = "^https://.+", message = "主页链接必须使用HTTPS")
    @Size(max = 512) private String accountLink;
    @NotNull @Positive private Long providerId;
}
```

更新 DTO 不接收 `userId`、状态、资料版本或报备状态。状态 DTO 使用 `@NotNull @Min(0) @Max(1)`。

- [ ] **Step 4: 添加可达错误码**

在 `ErrorCode` 注释增加 `7xxx - 推广媒体与报备错误`，新增并仅新增：

```java
MEDIA_ACCOUNT_NOT_FOUND(7001, "媒体账号不存在"),
MEDIA_ACCOUNT_DUPLICATE(7002, "该媒体账号已被绑定"),
MEDIA_ACCOUNT_IDENTITY_LOCKED(7003, "已加白账号的平台和账号ID不能修改"),
MEDIA_ACCOUNT_DISABLED(7004, "媒体账号已停用"),
MEDIA_TYPE_UNSUPPORTED(7005, "当前平台不支持该媒体类型"),
MEDIA_FILING_NOT_FOUND(7006, "平台报备不存在"),
MEDIA_FILING_APPROVED(7007, "已加白报备不需要重试"),
```

- [ ] **Step 5: 实现事务业务**

```java
public interface MediaAccountService {
    List<MediaAccountVO> getMine(Long userId);
    MediaAccountDetailVO getMineById(Long userId, Long id);
    MediaAccountDetailVO create(Long userId, CreateMediaAccountDTO request);
    MediaAccountDetailVO update(Long userId, Long id, UpdateMediaAccountDTO request);
    void updateStatus(Long userId, Long id, UpdateMediaAccountStatusDTO request);
    MediaFilingVO submitOrRetry(Long userId, Long id, Long providerId);
}
```

所有归属查询使用 `id + userId`；创建/修改/重试使用事务。先解析平台和能力，再写本地任务，不在事务中调用第三方。数据库唯一键冲突统一映射为 `MEDIA_ACCOUNT_DUPLICATE`。

- [ ] **Step 6: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountServiceTest test
```

- [ ] **Step 7: 提交用户业务**

```powershell
git add src/main/java/com/kasi/backend/promotion/dto src/main/java/com/kasi/backend/promotion/vo src/main/java/com/kasi/backend/promotion/service src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java
git commit -m "feat: manage promotion media accounts"
```

### Task 6: 实现持久报备任务、轮询和重试

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/config/MediaFilingProperties.java`
- Create: `src/main/java/com/kasi/backend/promotion/config/MediaFilingTaskConfig.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/MediaFilingTaskService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/promotion/task/MediaFilingScheduler.java`
- Create: `src/test/java/com/kasi/backend/promotion/service/MediaFilingTaskServiceTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`

- [ ] **Step 1: 写失败的任务测试**

使用固定 Clock，覆盖：

- 到期任务只有 `claimLease == 1` 时执行。
- `SUBMIT` 成功后保持原业务状态，设置 `QUERY`，首次查询时间为 1 分钟后。
- `QUERY` 返回 0/1/2 分别更新三态。
- `PENDING` 临时失败按 1、5、15、30、60 分钟退避，之后每次 60 分钟。
- `PENDING` 连续 10 次失败转 `FAILED + NONE`。
- `APPROVED` 查询网络失败保持 `APPROVED` 并继续重试。
- `APPROVED` 明确返回 2 才转 `FAILED`。
- 任务完成前资料版本变化时，条件更新返回 0，旧结果不覆盖新任务。
- 租约到期后其他实例可重新领取。
- 一个任务失败不阻止同批次其他任务处理。

```java
@Test
@DisplayName("旧资料版本的远程结果写回零行并保持新任务")
void staleTaskResultIsDiscarded() {
    ProviderMediaFiling task = claimedSubmitTask(1);
    when(filingMapper.completeSubmit(
            task.getId(), owner, 1, now, now.plusMinutes(1))).thenReturn(0);

    service.process(task.getId(), owner);

    verify(filingMapper, never()).enqueue(
            anyLong(), any(), any(), eq(1), any());
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=MediaFilingTaskServiceTest test
```

- [ ] **Step 3: 添加强类型配置**

```properties
app.promotion.filing.scheduler-enabled=true
app.promotion.filing.fixed-delay=30s
app.promotion.filing.batch-size=50
app.promotion.filing.lease-duration=2m
app.promotion.filing.first-query-delay=1m
app.promotion.filing.pending-query-interval=5m
app.promotion.filing.approved-query-interval=24h
app.promotion.filing.max-pending-retries=10
app.promotion.filing.retry-delays=1m,5m,15m,30m,60m
```

测试 profile 设置 `scheduler-enabled=false`，避免后台线程影响确定性测试。`MediaFilingProperties` 使用 `@ConfigurationProperties` 和 Jakarta Validation，不在 Scheduler 中硬编码数值。

- [ ] **Step 4: 实现任务处理**

Scheduler 只调用 `processDueBatch()`。Service 先查到期 ID，再逐条条件领取租约，加载媒体账号和运行时连接，然后在事务外调用适配器。每次写回必须匹配租约持有者和 `task_data_version`。

任务日志只记录报备内部 ID、平台编码、动作、耗时和结果，不记录账号主页、`pid`、明文/密文 key 或签名前原文。

- [ ] **Step 5: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=MediaFilingTaskServiceTest test
```

- [ ] **Step 6: 提交任务处理**

```powershell
git add src/main/java/com/kasi/backend/promotion/config src/main/java/com/kasi/backend/promotion/service/MediaFilingTaskService.java src/main/java/com/kasi/backend/promotion/service/impl/MediaFilingTaskServiceImpl.java src/main/java/com/kasi/backend/promotion/task src/main/resources/application.properties src/test/resources/application-test.properties src/test/java/com/kasi/backend/promotion/service/MediaFilingTaskServiceTest.java
git commit -m "feat: process media filing tasks"
```

### Task 7: 暴露推广用户媒体账号 API

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/controller/UserMediaAccountController.java`
- Create: `src/test/java/com/kasi/backend/promotion/controller/UserMediaAccountControllerTest.java`

- [ ] **Step 1: 写失败的控制器和权限测试**

测试继承 `BaseAuthTest`。覆盖无 Token 401、ADMIN Token 403、USER 正常访问、跨用户资源返回 7001、DTO 校验返回 1006、创建/修改/启停/第二平台报备/失败重试和响应不暴露内部 `userId`、`connectionId`、`pid`、密钥或租约字段。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=UserMediaAccountControllerTest test
```

Expected: 404 或编译失败，因为路由不存在。

- [ ] **Step 3: 实现用户控制器**

```java
@RestController
@RequestMapping("/api/user/promotion/media-accounts")
@RequiredArgsConstructor
public class UserMediaAccountController {
    private final MediaAccountService mediaAccountService;

    @GetMapping
    public ApiResponse<List<MediaAccountVO>> getMine() {
        return ApiResponse.success(mediaAccountService.getMine(AuthContextHolder.getUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaAccountDetailVO> getMineById(@PathVariable Long id) {
        return ApiResponse.success(mediaAccountService.getMineById(AuthContextHolder.getUserId(), id));
    }

    @PostMapping
    public ApiResponse<MediaAccountDetailVO> create(@Valid @RequestBody CreateMediaAccountDTO request) {
        return ApiResponse.success(mediaAccountService.create(AuthContextHolder.getUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MediaAccountDetailVO> update(@PathVariable Long id,
            @Valid @RequestBody UpdateMediaAccountDTO request) {
        return ApiResponse.success(mediaAccountService.update(AuthContextHolder.getUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateMediaAccountStatusDTO request) {
        mediaAccountService.updateStatus(AuthContextHolder.getUserId(), id, request);
        return ApiResponse.successMessage("媒体账号状态修改成功");
    }

    @PostMapping("/{id}/filings/{providerId}")
    public ApiResponse<MediaFilingVO> submitOrRetry(@PathVariable Long id,
            @PathVariable Long providerId) {
        return ApiResponse.success(mediaAccountService.submitOrRetry(
                AuthContextHolder.getUserId(), id, providerId));
    }
}
```

从 `AuthContextHolder` 取得推广用户内部 ID，任何 DTO 和路径都不接受任意 `userId`。现有 `/api/user/**` 安全规则已经覆盖这些接口，不增加更宽的放行规则。

- [ ] **Step 4: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=UserMediaAccountControllerTest,SecurityPermissionTest test
```

- [ ] **Step 5: 提交用户 API**

```powershell
git add src/main/java/com/kasi/backend/promotion/controller/UserMediaAccountController.java src/test/java/com/kasi/backend/promotion/controller/UserMediaAccountControllerTest.java
git commit -m "feat: expose user media account APIs"
```

### Task 8: 实现管理员查询和报备重试

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/dto/AdminMediaAccountPageQueryDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountListItemVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountDetailVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountPageVO.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Create: `src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Modify: `src/main/java/com/kasi/backend/promotion/mapper/PromotionMediaAccountMapper.java`
- Modify: `src/main/resources/mapper/PromotionMediaAccountMapper.xml`

- [ ] **Step 1: 写失败的管理员测试**

覆盖：

- 匿名 401、USER 403、普通管理员和超级管理员均可访问。
- 默认 `page=1,size=20`，稳定按媒体账号 `id DESC`。
- 按 `userNo/mediaType/accountStatus/providerId/filingStatus` 筛选。
- 详情包含用户 `userNo`、昵称/姓名和全部平台报备。
- 管理员可重试 `FAILED`，不能重试 `APPROVED`，不能修改归属或删除账号。
- 管理员错误详情脱敏，不返回 key、密文、签名和租约持有者。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=AdminMediaAccountControllerTest test
```

- [ ] **Step 3: 实现分页和管理员服务**

```java
public interface MediaAccountAdminService {
    AdminMediaAccountPageVO getPage(AdminMediaAccountPageQueryDTO query);
    AdminMediaAccountDetailVO getById(Long id);
    MediaFilingVO retry(Long id, Long providerId);
}
```

分页主查询以 `promotion_media_account` 为主表，用户和报备筛选通过参数化 `EXISTS`；不得拼接枚举字符串或排序字段。管理员重试与用户重试复用同一个报备编排方法，不复制状态转换。

- [ ] **Step 4: 实现管理员控制器**

路由：

```text
GET  /api/admin/promotion/media-accounts
GET  /api/admin/promotion/media-accounts/{id}
POST /api/admin/promotion/media-accounts/{id}/filings/{providerId}/retry
```

现有 `/api/admin/**` 规则要求 `ROLE_ADMIN`，不把该接口误放到仅超级管理员可访问的 `/api/admin/management/**`。

- [ ] **Step 5: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=AdminMediaAccountControllerTest,SecurityPermissionTest test
```

- [ ] **Step 6: 提交管理员能力**

```powershell
git add src/main/java/com/kasi/backend/promotion/dto/AdminMediaAccountPageQueryDTO.java src/main/java/com/kasi/backend/promotion/vo src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java src/main/java/com/kasi/backend/promotion/mapper/PromotionMediaAccountMapper.java src/main/resources/mapper/PromotionMediaAccountMapper.xml src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java
git commit -m "feat: administer media account filings"
```

### Task 9: 阻止删除已绑定媒体账号的推广用户

**Files:**
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementMutationTest.java`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementServiceTest.java`

- [ ] **Step 1: 写失败的删除保护测试**

控制器测试先给目标用户插入一条媒体账号，再调用现有 DELETE：

```java
mockMvc.perform(delete("/api/user/management/{id}", userId)
        .header("Authorization", "Bearer " + loginAsAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(3014));

assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM promotion_user WHERE id = ?", Integer.class, userId)).isOne();
```

Service 测试断言存在媒体账号时不调用 `sessionService.beginMutation` 和 `promotionUserMapper.deleteById`；未绑定用户仍按现有流程删除。

- [ ] **Step 2: 运行并确认 RED**

```powershell
.\mvnw.cmd --% -Dtest=UserManagementMutationTest,UserManagementServiceTest test
```

- [ ] **Step 3: 添加错误码和删除前检查**

```java
USER_MEDIA_ACCOUNT_BOUND(3014, "该推广用户已绑定媒体账号，只能禁用"),
```

在 `UserManagementServiceImpl.delete` 中：

1. 锁定并确认推广用户存在。
2. 调用 `MediaAccountOwnershipService.hasBoundAccount(id)`。
3. 存在时立即抛出 3014，不进入 Redis `MUTATING`。
4. 不存在时继续现有 Redis-first 删除流程。
5. 捕获并识别并发插入导致的 `fk_media_account_user` 数据完整性异常，同样映射为 3014；其他数据库异常原样抛出。

- [ ] **Step 4: 运行并确认 GREEN**

```powershell
.\mvnw.cmd --% -Dtest=UserManagementMutationTest,UserManagementServiceTest test
```

- [ ] **Step 5: 提交删除保护**

```powershell
git add src/main/java/com/kasi/backend/user/service/impl/UserManagementServiceImpl.java src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/test/java/com/kasi/backend/user/UserManagementMutationTest.java src/test/java/com/kasi/backend/user/UserManagementServiceTest.java
git commit -m "feat: protect media account ownership"
```

### Task 10: 更新当前文档并完成模块验证

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-17-media-account-filing-design.md`
- Modify: `docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md`

- [ ] **Step 1: 更新已实现边界**

README 和 AGENTS 只写本计划实际完成的能力：媒体账号 CRUD、GoodShort 报备、轮询、三态、管理员查询/重试和删除保护。明确短剧目录、分佣、链接、订单、导出和分析仍未实现。

将专项设计文档状态改为“已实现并通过验收”只能发生在全部测试通过且用户验收后。路线图只更新模块 3，不把模块 2 或模块 4 标记为开始。

- [ ] **Step 2: 运行所有聚焦测试**

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest,MediaAccountFilingPersistenceTest,ProviderRuntimeConnectionServiceTest,GoodShortFilingAdapterTest,MediaAccountServiceTest,MediaFilingTaskServiceTest,UserMediaAccountControllerTest,AdminMediaAccountControllerTest,UserManagementMutationTest,UserManagementServiceTest,SecurityPermissionTest test
```

Expected: 零失败、零错误。

- [ ] **Step 3: 运行 Java 25 完整套件**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% test
.\mvnw.cmd --% -DskipTests compile
git diff --check
```

Expected: 完整测试 `BUILD SUCCESS`、零失败、零错误，编译成功且无空白问题。

- [ ] **Step 4: 安全与范围审计**

检查：

```powershell
rg -n "apiKey|api_key_ciphertext|partnerId|leaseOwner" src/main/java/com/kasi/backend/promotion
git diff --stat d77c23d...HEAD
```

推广用户响应不得包含内部 `userId`、`connectionId`、`pid`、密钥/密文、租约字段。范围中不得出现短剧、佣金、链接、订单、导出或分析业务实现。

- [ ] **Step 5: 提交文档**

```powershell
git add README.md AGENTS.md docs/superpowers/specs/2026-08-17-media-account-filing-design.md docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md
git commit -m "docs: document media account filing module"
```

## 完成门槛

以下条件全部满足后，本模块才算完成：

- GoodShort 平台接入模块前置能力已完成并验收。
- 用户可以绑定多个媒体账号，同一账号不能归属不同用户。
- 同一媒体账号可以向多个短剧平台保存独立报备。
- GoodShort 提交和查询请求、签名、状态映射通过固定测试。
- 旧资料版本任务无法覆盖新资料任务。
- 多实例租约、重试上限、已加白网络失败保护通过测试。
- 用户和管理员权限边界通过测试，响应不泄漏平台凭据和内部关联 ID。
- 绑定过媒体账号的推广用户只能禁用，不能物理删除。
- Java 25 完整测试、编译和差异检查全部通过。
- 用户完成本模块验收。
