# GoodShort Platform Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first independently testable module: secure GoodShort platform connection management, capability declaration, request signing, and an administrator-only connection probe.

**Architecture:** Add a focused `provider` module with persisted platform/connection metadata, AES-GCM encrypted API keys, a provider adapter contract, and a GoodShort adapter. This module exposes only platform connection administration; it deliberately does not persist dramas or implement filing, links, orders, commissions, downloads, or analytics.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Spring MVC `RestClient`, Spring Security, Jakarta Validation, MyBatis, MySQL 8, H2, JUnit 5, AssertJ, MockMvc, `MockRestServiceServer`.

---

## Module Boundary

### Delivered by this plan

- `short_drama_provider` and `short_drama_connection` schema.
- Seeded `GOODSHORT` provider definition.
- One active connection per provider for the first release.
- AES-256-GCM encryption for platform API keys using an environment-injected master key.
- Super-administrator connection create/update and connectivity-test endpoints.
- Administrator read-only provider/connection endpoint with no secret material.
- Provider capability declaration and GoodShort signing/client foundation.
- GoodShort connectivity probe through `initBooks` with `pageNo=1`, `pageSize=1`, and `language=ENGLISH`; returned dramas are not persisted in this module.

### Explicitly excluded

- Short-drama catalog tables and synchronization.
- Media accounts and filing.
- Commission rules.
- Free content and downloads.
- Promotion links and tracking numbers.
- Orders, commission calculation, exports, and analytics.
- Frontend changes.

## File Map

```text
src/main/java/com/kasi/backend/provider/
├─ controller/ProviderAdminController.java
├─ dto/UpsertProviderConnectionDTO.java
├─ entity/ShortDramaProvider.java
├─ entity/ShortDramaConnection.java
├─ enums/ProviderCapability.java
├─ mapper/ShortDramaProviderMapper.java
├─ mapper/ShortDramaConnectionMapper.java
├─ service/ProviderConnectionService.java
├─ service/ProviderCredentialCipher.java
├─ service/impl/ProviderConnectionServiceImpl.java
├─ service/impl/AesGcmProviderCredentialCipher.java
├─ spi/ProviderAdapter.java
├─ spi/ProviderConnectionSecret.java
├─ goodshort/GoodShortAdapter.java
├─ goodshort/GoodShortProperties.java
├─ goodshort/GoodShortSigner.java
├─ goodshort/dto/GoodShortConnectionProbeRequest.java
├─ goodshort/dto/GoodShortResponse.java
└─ vo/ProviderConnectionVO.java
   vo/ProviderVO.java
   vo/ProviderConnectionTestVO.java

src/main/resources/mapper/
├─ ShortDramaProviderMapper.xml
└─ ShortDramaConnectionMapper.xml
```

Each Mapper operates on exactly one primary table. The controller depends only on `ProviderConnectionService`; the service selects a `ProviderAdapter`; only the GoodShort adapter knows GoodShort field names and signing rules.

### Task 1: Add the provider and connection schema contract

**Files:**
- Create: `src/test/java/com/kasi/backend/provider/ProviderConnectionMigrationTest.java`
- Modify: `src/main/resources/db/migration/V1__kasi_promotion.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`

- [ ] **Step 1: Write the failing production-migration test**

Create an isolated Flyway/H2 MySQL-mode test following `DefaultSuperAdminMigrationTest`. Execute production V1 and assert both tables, seed data, unique constraints, and encrypted-secret column exist:

```java
@Test
@DisplayName("V1创建短剧平台和单平台接入账号结构")
void migrateV1CreatesProviderConnectionSchema() {
    assertThat(tableExists("SHORT_DRAMA_PROVIDER")).isTrue();
    assertThat(tableExists("SHORT_DRAMA_CONNECTION")).isTrue();
    assertThat(column("SHORT_DRAMA_CONNECTION", "API_KEY_CIPHERTEXT")).isNotNull();
    assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM short_drama_provider WHERE provider_code = 'GOODSHORT'",
            Long.class)).isEqualTo(1L);
}
```

Add a duplicate-provider assertion that a second `GOODSHORT` insert fails, and a duplicate-connection assertion that the same `provider_id` cannot have two connection rows in this first module.

- [ ] **Step 2: Run the migration test and verify RED**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=ProviderConnectionMigrationTest test
```

Expected: FAIL because `short_drama_provider` and `short_drama_connection` do not exist.

- [ ] **Step 3: Add the MySQL tables and GoodShort seed**

Append to V1 before the default administrator insert:

```sql
CREATE TABLE `short_drama_provider`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_code` VARCHAR(32)     NOT NULL COMMENT '平台编码',
    `provider_name` VARCHAR(64)     NOT NULL COMMENT '平台名称',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_provider_code` (`provider_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='短剧平台';

CREATE TABLE `short_drama_connection`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`        BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `connection_name`    VARCHAR(64)     NOT NULL COMMENT '接入账号名称',
    `partner_id`         VARCHAR(64)     NOT NULL COMMENT '平台机构标识',
    `api_key_ciphertext` TEXT            NOT NULL COMMENT '平台密钥密文',
    `currency`           CHAR(3)         NOT NULL COMMENT 'ISO 4217币种',
    `status`             TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `created_by`         BIGINT UNSIGNED DEFAULT NULL COMMENT '创建管理员',
    `updated_by`         BIGINT UNSIGNED DEFAULT NULL COMMENT '更新管理员',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_connection_provider` (`provider_id`),
    CONSTRAINT `fk_drama_connection_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='短剧平台接入账号';

INSERT INTO `short_drama_provider` (`provider_code`, `provider_name`, `status`)
VALUES ('GOODSHORT', 'GoodShort', 1);
```

Mirror the schema in `test-schema.sql` using H2-compatible types and seed the same provider.

- [ ] **Step 4: Reset provider data safely in BaseAuthTest**

Before deleting `promotion_user` and `sys_admin_user`, add child-first cleanup and restore the platform seed:

```java
jdbcTemplate.execute("DELETE FROM short_drama_connection");
jdbcTemplate.execute("DELETE FROM short_drama_provider");
jdbcTemplate.update(
        "INSERT INTO short_drama_provider (provider_code, provider_name, status) VALUES (?, ?, ?)",
        "GOODSHORT", "GoodShort", 1);
```

Do not add provider data to existing authentication fixtures beyond this deterministic seed.

- [ ] **Step 5: Run schema tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd --% -Dtest=ProviderConnectionMigrationTest,DefaultSuperAdminMigrationTest test
```

Expected: both classes pass with zero failures.

- [ ] **Step 6: Commit the schema increment**

```powershell
git add src/main/resources/db/migration/V1__kasi_promotion.sql src/test/resources/test-schema.sql src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/provider/ProviderConnectionMigrationTest.java
git commit -m "feat: add short drama provider connection schema"
```

### Task 2: Encrypt provider API keys with AES-GCM

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/service/ProviderCredentialCipher.java`
- Create: `src/main/java/com/kasi/backend/provider/service/impl/AesGcmProviderCredentialCipher.java`
- Create: `src/main/java/com/kasi/backend/provider/config/ProviderCredentialProperties.java`
- Create: `src/test/java/com/kasi/backend/provider/service/ProviderCredentialCipherTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`

- [ ] **Step 1: Write failing cipher tests**

Cover round-trip, random IVs, wrong master key, malformed ciphertext, and absence of plaintext:

```java
@Test
@DisplayName("同一密钥每次加密产生不同密文且均可解密")
void encryptUsesRandomIvAndDecrypts() {
    ProviderCredentialCipher cipher = cipher(TEST_MASTER_KEY);

    String first = cipher.encrypt("goodshort-secret");
    String second = cipher.encrypt("goodshort-secret");

    assertThat(first).startsWith("v1:").doesNotContain("goodshort-secret");
    assertThat(second).isNotEqualTo(first);
    assertThat(cipher.decrypt(first)).isEqualTo("goodshort-secret");
    assertThat(cipher.decrypt(second)).isEqualTo("goodshort-secret");
}
```

Use a fixed Base64-encoded 32-byte test master key. Never use a production-looking secret in tests.

- [ ] **Step 2: Run the cipher test and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=ProviderCredentialCipherTest test
```

Expected: FAIL because the cipher types do not exist.

- [ ] **Step 3: Define the cipher contract and validated configuration**

```java
public interface ProviderCredentialCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
```

Bind `app.provider-credentials.master-key` through `@ConfigurationProperties`. Decode it as Base64 and require exactly 32 bytes. Add:

```properties
app.provider-credentials.master-key=${PROVIDER_CREDENTIAL_MASTER_KEY}
```

Add a non-production fixed Base64 key to `application-test.properties`.

- [ ] **Step 4: Implement versioned AES-256-GCM ciphertext**

Use `AES/GCM/NoPadding`, a fresh 12-byte IV, and a 128-bit tag. Persist this format:

```text
v1:<Base64(IV || ciphertext-and-tag)>
```

The implementation must reject blank plaintext, unknown versions, payloads shorter than the IV plus tag, and authentication failures. Convert those failures to `IllegalStateException("平台密钥无法解密")` without including ciphertext or plaintext in the message.

- [ ] **Step 5: Run the cipher test and verify GREEN**

```powershell
.\mvnw.cmd --% -Dtest=ProviderCredentialCipherTest test
```

Expected: all cipher tests pass.

- [ ] **Step 6: Commit credential encryption**

```powershell
git add src/main/java/com/kasi/backend/provider/config src/main/java/com/kasi/backend/provider/service src/test/java/com/kasi/backend/provider/service src/main/resources/application.properties src/test/resources/application-test.properties
git commit -m "feat: encrypt provider credentials"
```

### Task 3: Add provider and connection persistence

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/entity/ShortDramaProvider.java`
- Create: `src/main/java/com/kasi/backend/provider/entity/ShortDramaConnection.java`
- Create: `src/main/java/com/kasi/backend/provider/mapper/ShortDramaProviderMapper.java`
- Create: `src/main/java/com/kasi/backend/provider/mapper/ShortDramaConnectionMapper.java`
- Create: `src/main/resources/mapper/ShortDramaProviderMapper.xml`
- Create: `src/main/resources/mapper/ShortDramaConnectionMapper.xml`
- Create: `src/test/java/com/kasi/backend/provider/mapper/ProviderPersistenceTest.java`

- [ ] **Step 1: Write failing Mapper tests**

The test extends `BaseAuthTest` and verifies provider lookup, connection insert, update, and no plaintext key exposure through SQL fixtures:

```java
@Test
@DisplayName("接入账号按平台唯一并可更新非密钥资料")
void connectionIsUniquePerProviderAndUpdatable() {
    ShortDramaProvider provider = providerMapper.findByCode("GOODSHORT");
    ShortDramaConnection connection = connection(provider.getId(), "v1:ciphertext");

    assertThat(connectionMapper.insert(connection)).isEqualTo(1);
    connection.setConnectionName("GoodShort默认账号");
    connection.setCurrency("USD");
    assertThat(connectionMapper.update(connection)).isEqualTo(1);

    assertThat(connectionMapper.findByProviderId(provider.getId()).getConnectionName())
            .isEqualTo("GoodShort默认账号");
}
```

Also assert duplicate provider connection insertion throws `DuplicateKeyException`.

- [ ] **Step 2: Run Mapper tests and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=ProviderPersistenceTest test
```

Expected: FAIL because Entity, Mapper, and XML files do not exist.

- [ ] **Step 3: Add focused entities**

Use Lombok `@Data` and no business methods. `ShortDramaProvider` mirrors provider columns. `ShortDramaConnection` mirrors connection columns, including ciphertext but never implements `toString()` manually or logs itself.

- [ ] **Step 4: Add one-table Mapper contracts**

```java
@Mapper
public interface ShortDramaProviderMapper {
    List<ShortDramaProvider> findAll();
    ShortDramaProvider findById(@Param("id") Long id);
    ShortDramaProvider findByCode(@Param("providerCode") String providerCode);
}

@Mapper
public interface ShortDramaConnectionMapper {
    ShortDramaConnection findByProviderId(@Param("providerId") Long providerId);
    int insert(ShortDramaConnection connection);
    int update(ShortDramaConnection connection);
}
```

The connection update SQL must only replace `api_key_ciphertext` when the service supplies a non-null new ciphertext:

```xml
<if test="apiKeyCiphertext != null">
    api_key_ciphertext = #{apiKeyCiphertext},
</if>
```

Always update `connection_name`, `partner_id`, `currency`, `status`, `updated_by`, and `updated_at`.

- [ ] **Step 5: Run Mapper tests and verify GREEN**

```powershell
.\mvnw.cmd --% -Dtest=ProviderPersistenceTest test
```

Expected: all Mapper tests pass.

- [ ] **Step 6: Commit provider persistence**

```powershell
git add src/main/java/com/kasi/backend/provider/entity src/main/java/com/kasi/backend/provider/mapper src/main/resources/mapper/ShortDramaProviderMapper.xml src/main/resources/mapper/ShortDramaConnectionMapper.xml src/test/java/com/kasi/backend/provider/mapper/ProviderPersistenceTest.java
git commit -m "feat: persist short drama provider connections"
```

### Task 4: Implement provider connection administration service

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/dto/UpsertProviderConnectionDTO.java`
- Create: `src/main/java/com/kasi/backend/provider/vo/ProviderConnectionVO.java`
- Create: `src/main/java/com/kasi/backend/provider/vo/ProviderVO.java`
- Create: `src/main/java/com/kasi/backend/provider/service/ProviderConnectionService.java`
- Create: `src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Create: `src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`

- [ ] **Step 1: Write failing service tests**

Use Mockito for the two Mappers and cipher. Cover list-without-secret, create requiring API key, update retaining an omitted key, update replacing a supplied key, missing provider, and disabled status validation.

```java
@Test
@DisplayName("查询接入账号只返回是否配置密钥")
void listDoesNotExposeCredential() {
    when(providerMapper.findAll()).thenReturn(List.of(provider()));
    when(connectionMapper.findByProviderId(1L)).thenReturn(connection("v1:secret-ciphertext"));

    ProviderVO result = service.getProviders().getFirst();

    assertThat(result.getConnection().isCredentialConfigured()).isTrue();
    assertThat(result.getConnection().toString()).doesNotContain("secret-ciphertext");
}
```

- [ ] **Step 2: Run service tests and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=ProviderConnectionServiceTest test
```

Expected: FAIL because the service types do not exist.

- [ ] **Step 3: Add provider error codes**

Extend the `ErrorCode` range comment and enum:

```java
PROVIDER_NOT_FOUND(6001, "短剧平台不存在"),
PROVIDER_CONNECTION_NOT_FOUND(6002, "平台接入账号未配置"),
PROVIDER_CONNECTION_INVALID(6003, "平台接入账号配置不完整"),
PROVIDER_CREDENTIAL_UNAVAILABLE(6004, "平台密钥不可用"),
PROVIDER_REMOTE_UNAVAILABLE(6005, "短剧平台暂时不可用"),
PROVIDER_REMOTE_REJECTED(6006, "短剧平台拒绝请求"),
```

Do not preallocate errors for catalog, filing, orders, or commissions in this module.

- [ ] **Step 4: Add validated request and secret-free response models**

```java
@Data
public class UpsertProviderConnectionDTO {
    @NotBlank @Size(max = 64)
    private String connectionName;
    @NotBlank @Size(max = 64)
    private String partnerId;
    @Size(max = 256)
    private String apiKey;
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;
    @NotNull @Min(0) @Max(1)
    private Integer status;
}
```

`ProviderConnectionVO` exposes `id`, `connectionName`, `partnerId`, `currency`, `status`, `credentialConfigured`, `createdAt`, and `updatedAt`. It must not contain ciphertext, plaintext key, masked key fragments, or a setter for any key.

- [ ] **Step 5: Implement transactional upsert behavior**

```java
public interface ProviderConnectionService {
    List<ProviderVO> getProviders();
    ProviderConnectionVO upsert(Long operatorId, Long providerId, UpsertProviderConnectionDTO request);
}
```

The implementation must:

1. Load the provider or throw `PROVIDER_NOT_FOUND`.
2. Normalize `connectionName`, `partnerId`, and uppercase currency.
3. Require nonblank `apiKey` when no connection exists.
4. Encrypt a supplied key and never pass plaintext to a Mapper.
5. Leave ciphertext unchanged when update omits or blanks `apiKey`.
6. Set `createdBy/updatedBy` from `AuthContextHolder` input, never from the DTO.
7. Return a fresh Mapper read converted to the secret-free VO.

- [ ] **Step 6: Run service tests and verify GREEN**

```powershell
.\mvnw.cmd --% -Dtest=ProviderConnectionServiceTest test
```

Expected: all service tests pass.

- [ ] **Step 7: Commit the administration service**

```powershell
git add src/main/java/com/kasi/backend/provider/dto src/main/java/com/kasi/backend/provider/vo src/main/java/com/kasi/backend/provider/service/ProviderConnectionService.java src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java
git commit -m "feat: manage provider connection settings"
```

### Task 5: Add capability declaration and GoodShort connectivity probe

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/enums/ProviderCapability.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/ProviderAdapter.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/ProviderConnectionSecret.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortProperties.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortClientConfig.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortSigner.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortConnectionProbeRequest.java`
- Create: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortResponse.java`
- Create: `src/main/java/com/kasi/backend/provider/vo/ProviderConnectionTestVO.java`
- Create: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortSignerTest.java`
- Create: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortAdapterTest.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`
- Modify: `src/main/java/com/kasi/backend/provider/service/ProviderConnectionService.java`
- Modify: `src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`

- [ ] **Step 1: Write the failing GoodShort signature-vector test**

```java
@Test
@DisplayName("按官方固定向量生成大写MD5签名")
void signMatchesOfficialVector() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("pid", "123456");
    params.put("timestamp", 1681810530092L);
    params.put("pageNo", 1);
    params.put("pageSize", 10);

    assertThat(signer.sign(params, "aaabbbccc"))
            .isEqualTo("973FB9A689D3924CAC1967EF6E0BD012");
}
```

Add cases proving `sign`, null, and blank-string values are excluded and parameter names are case-sensitive.

- [ ] **Step 2: Run signer tests and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=GoodShortSignerTest test
```

Expected: FAIL because `GoodShortSigner` does not exist.

- [ ] **Step 3: Implement the signer exactly once**

`GoodShortSigner.sign` must:

1. Remove key `sign` and null/blank values.
2. Sort parameter names with natural order, which matches ASCII for these ASCII names.
3. Join `key=value` pairs with `&` without adding URL encoding not specified by GoodShort.
4. Append `&key=<secret>`.
5. MD5 the UTF-8 bytes and return uppercase hexadecimal.

Do not log the pre-sign string.

- [ ] **Step 4: Define the platform capability contract**

```java
public enum ProviderCapability {
    FULL_DRAMA_SYNC,
    INCREMENTAL_DRAMA_SYNC,
    FREE_CONTENT_PREVIEW,
    SINGLE_DOWNLOAD,
    BATCH_DOWNLOAD,
    ACCOUNT_FILING,
    FILING_STATUS_QUERY,
    PROMOTION_LINK,
    PROMOTION_CODE,
    TIKTOK_ANCHOR,
    ORDER_SYNC,
    ANALYTICS_SYNC
}

public interface ProviderAdapter {
    String providerCode();
    Set<ProviderCapability> capabilities();
    ProviderConnectionTestVO testConnection(ProviderConnectionSecret connection);
}
```

GoodShort capabilities must exclude `TIKTOK_ANCHOR`.

- [ ] **Step 5: Write the failing HTTP adapter tests**

Bind `MockRestServiceServer` to the `RestClient.Builder` and construct the adapter with `Clock.fixed(...)`. Assert the probe sends:

```json
{
  "pageNo": 1,
  "pageSize": 1,
  "language": "ENGLISH",
  "pid": "partner-1",
  "timestamp": 1681810530092
}
```

Assert the `sign` header matches the signer, successful `{ "status":0,"success":true,"message":"success" }` returns reachable, nonzero status throws `PROVIDER_REMOTE_REJECTED`, and I/O/5xx failure becomes provider-unavailable behavior without leaking credentials.

- [ ] **Step 6: Implement the GoodShort adapter**

Add configuration:

```properties
app.providers.goodshort.base-url=https://api.novelopen.com/creek
app.providers.goodshort.connect-timeout=3s
app.providers.goodshort.read-timeout=10s
```

`GoodShortAdapter.testConnection` posts to `/open/book/initBooks`, generates the timestamp server-side, signs the same nonempty request fields, and checks both `status == 0` and `success == true`. It must not persist returned drama records.

`GoodShortClientConfig` provides a production `Clock.systemUTC()` bean. The adapter receives `Clock` through constructor injection and calls `clock.millis()`; tests use a fixed Clock and never depend on wall-clock timing.

- [ ] **Step 7: Route service connectivity tests through adapters**

Extend the service contract:

```java
ProviderConnectionTestVO testConnection(Long providerId);
```

The service loads and decrypts the connection, resolves the adapter by exact provider code, and calls `testConnection`. Missing/disabled/incomplete connections fail before network access.

- [ ] **Step 8: Run GoodShort and service tests and verify GREEN**

```powershell
.\mvnw.cmd --% -Dtest=GoodShortSignerTest,GoodShortAdapterTest,ProviderConnectionServiceTest test
```

Expected: all listed tests pass.

- [ ] **Step 9: Commit the adapter foundation**

```powershell
git add src/main/java/com/kasi/backend/provider/enums src/main/java/com/kasi/backend/provider/spi src/main/java/com/kasi/backend/provider/goodshort src/main/java/com/kasi/backend/provider/vo/ProviderConnectionTestVO.java src/main/java/com/kasi/backend/provider/service src/test/java/com/kasi/backend/provider/goodshort src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java src/main/resources/application.properties src/test/resources/application-test.properties
git commit -m "feat: add GoodShort connection probe"
```

### Task 6: Expose secured administrator APIs

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/controller/ProviderAdminController.java`
- Create: `src/test/java/com/kasi/backend/provider/controller/ProviderAdminControllerTest.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`

- [ ] **Step 1: Write failing permission and contract tests**

Cover:

- Anonymous GET returns 401.
- Promotion user GET returns 403.
- Ordinary administrator GET succeeds.
- Ordinary administrator PUT and connection test return 403.
- Super administrator PUT and connection test succeed.
- GET and PUT responses never contain `apiKey`, `ciphertext`, the submitted plaintext, or masked secret fragments.
- Invalid currency, status, blank partner ID, and oversized names return validation code `1006`.

The test class extends `BaseAuthTest` and declares `@MockitoBean GoodShortAdapter goodShortAdapter`. Stub `providerCode()` as `GOODSHORT`, return the exact GoodShort capability set, and stub `testConnection(...)` with a successful `ProviderConnectionTestVO`. This guarantees controller tests never contact the real GoodShort host.

Example security assertion:

```java
mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD))
        .contentType(MediaType.APPLICATION_JSON)
        .content(validRequestJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(1003));
```

- [ ] **Step 2: Run controller tests and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=ProviderAdminControllerTest test
```

Expected: FAIL because the routes do not exist.

- [ ] **Step 3: Add the controller**

```java
@RestController
@RequestMapping("/api/admin/drama/providers")
@RequiredArgsConstructor
public class ProviderAdminController {
    private final ProviderConnectionService providerConnectionService;

    @GetMapping
    public ApiResponse<List<ProviderVO>> getProviders() {
        return ApiResponse.success(providerConnectionService.getProviders());
    }

    @PutMapping("/{providerId}/connection")
    public ApiResponse<ProviderConnectionVO> upsertConnection(
            @PathVariable Long providerId,
            @Valid @RequestBody UpsertProviderConnectionDTO request) {
        return ApiResponse.success(providerConnectionService.upsert(
                AuthContextHolder.getAdminId(), providerId, request));
    }

    @PostMapping("/{providerId}/connection/test")
    public ApiResponse<ProviderConnectionTestVO> testConnection(@PathVariable Long providerId) {
        return ApiResponse.success(providerConnectionService.testConnection(providerId));
    }
}
```

- [ ] **Step 4: Add method-specific security rules before the broad admin matcher**

```java
.requestMatchers(HttpMethod.PUT,
        "/api/admin/drama/providers/*/connection").hasRole("SUPER_ADMIN")
.requestMatchers(HttpMethod.POST,
        "/api/admin/drama/providers/*/connection/test").hasRole("SUPER_ADMIN")
.requestMatchers("/api/admin/drama/**").hasRole("ADMIN")
```

Import `org.springframework.http.HttpMethod`. Keep these matchers before `.requestMatchers("/api/admin/**")`.

- [ ] **Step 5: Run controller and security tests and verify GREEN**

```powershell
.\mvnw.cmd --% -Dtest=ProviderAdminControllerTest,SecurityPermissionTest test
```

Expected: all tests pass, with ordinary administrators still unable to mutate credentials.

- [ ] **Step 6: Commit secured APIs**

```powershell
git add src/main/java/com/kasi/backend/provider/controller/ProviderAdminController.java src/main/java/com/kasi/backend/security/config/SecurityConfig.java src/test/java/com/kasi/backend/provider/controller/ProviderAdminControllerTest.java
git commit -m "feat: expose provider connection administration"
```

### Task 7: Document and verify module 1 only

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md`
- Test: all module 1 and repository tests

- [ ] **Step 1: Update current-state documentation**

README must list only the implemented module 1 behavior:

- `provider/` package exists for platform connection administration.
- GoodShort connection credentials are encrypted and never returned.
- Administrator read and super-administrator mutation endpoints.
- Environment prerequisite `PROVIDER_CREDENTIAL_MASTER_KEY` as a Base64-encoded 32-byte key.
- Explicit statement that catalog, filing, promotion links, orders, commissions, exports, and analytics remain planned.

AGENTS.md must add the same verified module boundary and new test commands without describing modules 2-7 as implemented.

Update the roadmap status to “module 1 implemented and awaiting user acceptance”; do not mark module 2 started.

- [ ] **Step 2: Run focused module tests**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=ProviderConnectionMigrationTest,ProviderCredentialCipherTest,ProviderPersistenceTest,ProviderConnectionServiceTest,GoodShortSignerTest,GoodShortAdapterTest,ProviderAdminControllerTest test
```

Expected: zero failures and zero errors.

- [ ] **Step 3: Run the complete Java 25 suite**

```powershell
.\mvnw.cmd --% test
```

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 4: Run compile and whitespace verification**

```powershell
.\mvnw.cmd --% -DskipTests compile
git diff --check
```

Expected: compile succeeds and `git diff --check` reports no errors.

- [ ] **Step 5: Confirm scope containment**

Run:

```powershell
git status --short
git diff --stat master...HEAD
```

Expected when executing on a feature worktree branched from `master`: no files for drama catalog persistence, media accounts, filing, commission rules, downloads, promotion links, orders, exports, or analytics. Preserve unrelated `dump.rdb` in the original worktree as untracked.

- [ ] **Step 6: Commit module documentation**

```powershell
git add README.md AGENTS.md docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md
git commit -m "docs: document provider connection module"
```

## Module 1 Completion Gate

Do not begin the module 2 implementation plan until all conditions hold:

- Every task above is checked off.
- Focused and full tests show zero failures and errors.
- The GoodShort official signature vector passes.
- No API response, log assertion, or export contains a platform key or ciphertext.
- Ordinary administrators can read connection status but cannot write or test credentials.
- The user has reviewed and accepted module 1 behavior.
