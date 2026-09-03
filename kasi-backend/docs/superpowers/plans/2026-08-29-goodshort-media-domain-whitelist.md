# GoodShort Media Domain Whitelist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a provider-connection `mediaRootDomain` setting that strictly permits the configured root hostname and its DNS-boundary subdomains for GoodShort media URLs.

**Architecture:** Persist one nullable root domain on `short_drama_connection`, expose it through the existing provider connection DTO/VO, and pass it explicitly into the existing media URL validator. The sync worker and user playback path both resolve the current connection before accepting a URL; no global hostname property, automatic learning, independent whitelist page, or cache is added.

**Tech Stack:** Java 25, Spring Boot, MyBatis XML, MySQL/H2, Jakarta Validation, JUnit 5/Mockito/MockMvc, React 19, TypeScript, Ant Design, Vitest, Testing Library.

---

## Scope And Working-Tree Rules

- Work from repository root `E:/JavaProjects/kasi-project` for every Git command.
- The current checkout contains unrelated and overlapping uncommitted changes. Read each current diff before editing and preserve all existing work.
- Do not use `git reset`, `git checkout`, bulk staging, or file-level staging that would include unrelated hunks.
- Do not commit implementation changes until the intended hunks can be isolated and reviewed. The plan records verification checkpoints instead of unsafe intermediate commits.
- Do not implement the separately diagnosed UTC/MySQL scheduling-time mismatch in this plan.
- Do not drop or recreate the local database without a separate action-time confirmation, even though the schema contract requires a rebuild before runtime acceptance.

## File Map

```text
kasi-backend/src/main/resources/db/kasi_promotion.sql
    Add the sole production-schema source column.
kasi-backend/src/test/resources/test-schema.sql
    Keep the H2 application test schema aligned.
kasi-backend/scripts/dev/seed_goodshort_drama_catalog.sql
    Preserve the disabled MANUAL fixture with a null media root.
kasi-backend/src/main/java/com/kasi/backend/provider/entity/ShortDramaConnection.java
kasi-backend/src/main/resources/mapper/ShortDramaConnectionMapper.xml
    Persist media_root_domain with the existing connection.
kasi-backend/src/main/java/com/kasi/backend/provider/dto/UpsertProviderConnectionDTO.java
kasi-backend/src/main/java/com/kasi/backend/provider/vo/ProviderConnectionVO.java
kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java
    Validate, normalize, save, and return mediaRootDomain.
kasi-backend/src/main/java/com/kasi/backend/drama/service/DramaMediaUrlValidator.java
    Validate one URL against an explicitly supplied root domain.
kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/DramaContentSyncServiceImpl.java
    Validate GoodShort results against the owning connection before persistence.
kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java
    Revalidate persisted URLs against the current connection before returning them.
kasi-admin-web/src/features/provider/providerTypes.ts
kasi-admin-web/src/pages/provider/ProviderManagementPage.tsx
    Add the field to the existing connection contract and form above the API URL.
```

### Task 1: Persist The Media Root Domain

**Files:**
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql:101`
- Modify: `kasi-backend/src/test/resources/test-schema.sql:11`
- Modify: `kasi-backend/scripts/dev/seed_goodshort_drama_catalog.sql:36`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/entity/ShortDramaConnection.java`
- Modify: `kasi-backend/src/main/resources/mapper/ShortDramaConnectionMapper.xml`
- Test: `kasi-backend/src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/mapper/ProviderPersistenceTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java`

- [ ] **Step 1: Add failing schema and persistence assertions**

Extend `MediaAccountFilingMigrationTest` with:

```java
assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'SHORT_DRAMA_CONNECTION' "
                + "AND COLUMN_NAME = 'MEDIA_ROOT_DOMAIN' AND IS_NULLABLE = 'YES'",
        Integer.class)).isEqualTo(1);
```

In `ProviderPersistenceTest.connectionIsUniquePerProviderAndUpdatable`, set and verify the field:

```java
connection.setMediaRootDomain("novelopen.com");
// after mapper update and reload
assertThat(stored.getMediaRootDomain()).isEqualTo("novelopen.com");
```

Extend the seed fixture assertion so the MANUAL fixture explicitly remains null:

```java
Map<String, Object> connection = jdbc.queryForMap(
        "SELECT connection_name, currency, status, filing_mode, partner_id, "
                + "api_key_ciphertext, base_url, media_root_domain FROM short_drama_connection");
assertThat(connection.get("MEDIA_ROOT_DOMAIN")).isNull();
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run from `kasi-backend` with Java 25:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest,ProviderPersistenceTest,GoodShortDramaCatalogSeedTest test
```

Expected: compilation or assertion failure because `media_root_domain` and `get/setMediaRootDomain` do not exist.

- [ ] **Step 3: Add the production and test schema column**

Add this nullable field beside `base_url` in both schemas:

```sql
`media_root_domain` VARCHAR(253) DEFAULT NULL COMMENT '媒体资源允许根域（API报备必填）',
```

Use the H2 equivalent without the comment:

```sql
media_root_domain VARCHAR(253) DEFAULT NULL,
```

Do not add an `ALTER TABLE` compatibility script. Keep the development rebuild contract.

- [ ] **Step 4: Extend the entity and mapper**

Add to `ShortDramaConnection`:

```java
private String mediaRootDomain;
```

Include `media_root_domain` in the mapper insert and update:

```xml
INSERT INTO short_drama_connection
    (provider_id, connection_name, base_url, media_root_domain, partner_id,
     api_key_ciphertext, currency, filing_mode, status, created_by, updated_by)
VALUES
    (#{providerId}, #{connectionName}, #{baseUrl}, #{mediaRootDomain}, #{partnerId},
     #{apiKeyCiphertext}, #{currency}, COALESCE(#{filingMode}, 'API'), #{status},
     #{createdBy}, #{updatedBy})
```

```xml
SET connection_name = #{connectionName},
    base_url = #{baseUrl},
    media_root_domain = #{mediaRootDomain},
    partner_id = #{partnerId},
```

Update the seed guard and insert column list so the disabled MANUAL fixture requires and writes `media_root_domain IS NULL`.

- [ ] **Step 5: Run Task 1 tests and verify GREEN**

Run the same Maven command. Expected: all three test classes pass with zero failures and errors.

### Task 2: Extend The Existing Provider Connection Contract

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/dto/UpsertProviderConnectionDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/vo/ProviderConnectionVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/service/ProviderConnectionServiceTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/provider/controller/ProviderAdminControllerTest.java`

- [ ] **Step 1: Add failing service and HTTP contract assertions**

Update the service request helper to set:

```java
request.setMediaRootDomain(" NovelOpen.COM ");
```

Assert normalized persistence and response:

```java
assertThat(inserted.getMediaRootDomain()).isEqualTo("novelopen.com");
assertThat(result.getMediaRootDomain()).isEqualTo("novelopen.com");
```

Add `mediaRootDomain` to `ProviderAdminControllerTest.validRequest()` and assert the response:

```json
"mediaRootDomain": "novelopen.com"
```

```java
.andExpect(jsonPath("$.data.mediaRootDomain").value("novelopen.com"))
```

Add invalid request cases for `https://novelopen.com`, `*.novelopen.com`, `novelopen.com/path`, `evil..com`, and a blank value in API mode. Each must return `VALIDATION_ERROR(1006)`.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=ProviderConnectionServiceTest,ProviderAdminControllerTest test
```

Expected: compile/assertion failures because the DTO, entity-to-VO mapping, and request contract do not yet expose `mediaRootDomain`.

- [ ] **Step 3: Add DTO validation and API-mode requirement**

Add the field:

```java
@Size(max = 253)
@Pattern(
        regexp = "(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
        message = "媒体根域必须是有效 hostname，不包含协议、端口、路径或通配符")
private String mediaRootDomain;
```

Extend `isApiConfigurationPresent()`:

```java
return baseUrl != null && !baseUrl.isBlank()
        && mediaRootDomain != null && !mediaRootDomain.isBlank()
        && partnerId != null && !partnerId.isBlank();
```

Update its message to include the media root domain.

- [ ] **Step 4: Normalize, persist, and return the field**

In `buildConnection`:

```java
connection.setMediaRootDomain(normalizeDomain(request.getMediaRootDomain()));
```

Add:

```java
private String normalizeDomain(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
}
```

Include the field in `toConnectionVO`:

```java
.mediaRootDomain(connection.getMediaRootDomain())
```

Add `private String mediaRootDomain;` to `ProviderConnectionVO`.

- [ ] **Step 5: Run Task 2 tests and verify GREEN**

Run the same two test classes. Expected: zero failures and errors, existing secret-redaction assertions remain green.

### Task 3: Enforce The Root Domain During Synchronization

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/service/DramaMediaUrlValidator.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/DramaContentSyncServiceImpl.java`
- Modify: `kasi-backend/src/main/resources/application.properties`
- Test: `kasi-backend/src/test/java/com/kasi/backend/drama/service/DramaMediaUrlValidatorTest.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/drama/service/DramaContentSyncServiceTest.java`

- [ ] **Step 1: Replace global-list tests with failing root-domain tests**

Construct the validator without configuration:

```java
private final DramaMediaUrlValidator validator = new DramaMediaUrlValidator();
```

Cover accepted boundaries:

```java
assertThat(validator.isAllowed("https://novelopen.com/1.m3u8", "novelopen.com")).isTrue();
assertThat(validator.isAllowed("https://v-koc.novelopen.com/1.m3u8", "novelopen.com")).isTrue();
assertThat(validator.isAllowed("https://a.b.novelopen.com/1.m3u8", "NovelOpen.COM")).isTrue();
```

Cover rejected values:

```java
assertThat(validator.isAllowed("https://evilnovelopen.com/1.m3u8", "novelopen.com")).isFalse();
assertThat(validator.isAllowed("https://novelopen.com.evil.com/1.m3u8", "novelopen.com")).isFalse();
assertThat(validator.isAllowed("https://v-koc.novelopen.com/1.m3u8", null)).isFalse();
assertThat(validator.isAllowed("https://user:pass@v-koc.novelopen.com/1.m3u8", "novelopen.com")).isFalse();
assertThat(validator.isAllowed("https://v-koc.novelopen.com:8443/1.m3u8", "novelopen.com")).isFalse();
assertThat(validator.isAllowed("http://127.0.0.1/1.m3u8", "127.0.0.1")).isFalse();
```

Update the sync fixture connection:

```java
connection.setMediaRootDomain("novelopen.com");
```

Use `https://v-koc.novelopen.com/...` in the successful remote result and an unrelated hostname in the failure case.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
.\mvnw.cmd --% -Dtest=DramaMediaUrlValidatorTest,DramaContentSyncServiceTest test
```

Expected: compilation failures because the validator still accepts only one URL and reads a global list.

- [ ] **Step 3: Implement strict DNS-boundary validation**

Remove the `@Value` constructor and stored host list. Keep the existing URI and unsafe-literal checks, and expose:

```java
public boolean isAllowed(String value, String mediaRootDomain) {
    if (value == null || value.isBlank()
            || mediaRootDomain == null || mediaRootDomain.isBlank()) {
        return false;
    }
    try {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return false;
        if (host == null || uri.getUserInfo() != null) return false;
        if (port != -1 && port != 80 && port != 443) return false;
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedRoot = mediaRootDomain.trim().toLowerCase(Locale.ROOT);
        if (isUnsafeLiteral(normalizedHost)) return false;
        return normalizedHost.equals(normalizedRoot)
                || normalizedHost.endsWith("." + normalizedRoot);
    } catch (IllegalArgumentException exception) {
        return false;
    }
}
```

- [ ] **Step 4: Pass the owning connection root into the worker**

Replace the sync validation predicate with:

```java
if (remote.stream().anyMatch(item -> item == null
        || !urlValidator.isAllowed(item.contentUrl(), connection.getMediaRootDomain()))) {
    finalFailure(task, "INVALID_MEDIA_URL", "GoodShort returned an invalid media URL");
    return;
}
```

Delete this obsolete property from `application.properties`:

```properties
app.goodshort.media-hosts=${GOODSHORT_MEDIA_HOSTS:}
```

- [ ] **Step 5: Run Task 3 tests and verify GREEN**

Run the same two test classes. Expected: zero failures and errors; unknown domains remain terminal failures with no content upsert.

### Task 4: Revalidate Persisted URLs For Playback And Download

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Test: `kasi-backend/src/test/java/com/kasi/backend/drama/controller/UserPromotionDramaControllerTest.java`

- [ ] **Step 1: Add failing user API assertions**

In test setup, save the connection root:

```java
jdbcTemplate.update(
        "UPDATE short_drama_connection SET media_root_domain='novelopen.com' WHERE provider_id=?",
        providerId);
```

Use `https://v-koc.novelopen.com/episode-1.m3u8` for the accepted resource. Insert a second free resource with `https://unknown.example/episode-2.m3u8` and assert its `playUrl` and `downloadUrl` are null.

- [ ] **Step 2: Run the controller test and verify RED**

```powershell
.\mvnw.cmd '-Dtest=UserPromotionDramaControllerTest' test
```

Expected: the accepted subdomain is rejected by the old global configuration path or the unknown hostname is not evaluated against the connection field.

- [ ] **Step 3: Resolve the owning connection once per request**

Inject `ShortDramaConnectionMapper`. Reuse the drama returned by `requirePublishedDrama` and load its connection:

```java
ProviderDrama drama = requirePublishedDrama(id);
ShortDramaConnection connection = connectionMapper.findById(drama.getConnectionId());
String mediaRootDomain = connection == null ? null : connection.getMediaRootDomain();
return dramaMapper.findContents(id).stream().map(content -> {
    String url = Boolean.TRUE.equals(content.getFree())
            && mediaUrlValidator.isAllowed(content.getContentUrl(), mediaRootDomain)
            ? content.getContentUrl() : null;
    return DramaContentResourceVO.builder()
            .id(content.getId())
            .sequenceNo(content.getSequenceNo())
            .title(content.getTitle())
            .free(Boolean.TRUE.equals(content.getFree()))
            .playUrl(url)
            .downloadUrl(url)
            .build();
}).toList();
```

Do not query the connection inside the stream and do not add a cache.

- [ ] **Step 4: Run Task 4 test and verify GREEN**

Run the same controller test. Expected: matched root/subdomain URLs are returned; unknown URLs remain present as episode metadata but contain null play/download URLs.

### Task 5: Add The Field Above API URL In The Admin Form

**Files:**
- Modify: `kasi-admin-web/src/features/provider/providerTypes.ts`
- Modify: `kasi-admin-web/src/features/provider/providerApi.test.ts`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.tsx`
- Modify: `kasi-admin-web/src/pages/provider/ProviderManagementPage.test.tsx`

- [ ] **Step 1: Add failing API and page tests**

Extend the provider fixture and request contract:

```ts
mediaRootDomain: 'novelopen.com',
```

Assert the API request includes it:

```ts
expect(requestBody).toEqual({
  mediaRootDomain: 'novelopen.com',
  baseUrl: 'https://api.test',
  partnerId: 'p1',
  status: 1,
  filingMode: 'API',
})
```

In `ProviderManagementPage.test.tsx`, assert the field exists, precedes the API URL in DOM order, displays `novelopen.com`, and is disabled for ordinary administrators. Add a save interaction that verifies whitespace is trimmed before submission.

- [ ] **Step 2: Run focused frontend tests and verify RED**

Run from `kasi-admin-web`:

```powershell
pnpm exec vitest run src/features/provider/providerApi.test.ts src/pages/provider/ProviderManagementPage.test.tsx --exclude '.worktrees/**'
```

Expected: missing type/form field and request-body assertions fail.

- [ ] **Step 3: Extend TypeScript contracts**

Add:

```ts
export interface ProviderConnection {
  // existing fields
  mediaRootDomain: string | null
}

export interface UpsertProviderConnectionRequest {
  mediaRootDomain?: string
  // existing fields
}
```

- [ ] **Step 4: Add and submit the form field**

Add `mediaRootDomain: string` to `ProviderFormValues`, load it from the active connection, and include the trimmed value in API-mode requests.

Place this `Form.Item` immediately before `baseUrl`:

```tsx
<Form.Item
  label="域名白名单"
  name="mediaRootDomain"
  extra="填写允许的视频根域，根域及其正规子域均可访问，不包含协议、端口或路径"
  rules={[
    { required: true, message: '请输入域名白名单' },
    {
      pattern:
        /^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/,
      message: '请输入不包含协议、端口、路径或通配符的有效域名',
    },
  ]}
>
  <Input placeholder="例如：novelopen.com" />
</Form.Item>
```

Submit:

```ts
mediaRootDomain: values.mediaRootDomain.trim().toLowerCase(),
```

- [ ] **Step 5: Run Task 5 tests and verify GREEN**

Run the same two frontend tests. Expected: both files pass with zero failures.

### Task 6: Synchronize Documentation And Verify The Complete Change

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-backend/docs/superpowers/specs/2026-08-29-goodshort-media-domain-whitelist-design.md`
- Modify: `kasi-admin-web/README.md`

- [ ] **Step 1: Update current-behavior documentation**

Document these implemented facts:

```text
GoodShort 平台接入配置保存单个媒体根域；当前配置为 novelopen.com。
媒体 URL 只允许根域本身及符合点分隔边界的正规子域。
未知域名、相似字符串域名、内网地址、用户信息和非标准端口继续拒绝。
GOODSHORT_MEDIA_HOSTS 环境变量已删除；白名单不自动学习。
schema 变化后必须删除并重建开发数据库，再重新配置 GoodShort 接入账号。
```

Keep the official-document evidence separate from implemented behavior. Do not claim GoodShort guarantees a fixed `novelopen.com` media root.

- [ ] **Step 2: Run all focused backend tests**

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest,GoodShortDramaCatalogSeedTest,ProviderPersistenceTest,ProviderConnectionServiceTest,ProviderAdminControllerTest,DramaMediaUrlValidatorTest,DramaContentSyncServiceTest,UserPromotionDramaControllerTest test
```

Expected: Maven `BUILD SUCCESS`, zero failures and zero errors.

- [ ] **Step 3: Compile the backend**

```powershell
.\mvnw.cmd -DskipTests compile
```

Expected: Maven `BUILD SUCCESS`.

- [ ] **Step 4: Run frontend tests and build**

From `kasi-admin-web`:

```powershell
pnpm exec vitest run src/features/provider/providerApi.test.ts src/pages/provider/ProviderManagementPage.test.tsx --exclude '.worktrees/**'
pnpm build
```

Expected: focused Vitest tests pass and Vite build exits zero.

- [ ] **Step 5: Review diffs and whitespace from the repository root**

```powershell
git status --short --branch
git diff --check
git diff -- kasi-backend/src/main/resources/db/kasi_promotion.sql `
  kasi-backend/src/main/java/com/kasi/backend/provider `
  kasi-backend/src/main/java/com/kasi/backend/drama `
  kasi-admin-web/src/features/provider `
  kasi-admin-web/src/pages/provider `
  kasi-backend/README.md kasi-backend/AGENTS.md kasi-admin-web/README.md
```

Expected: no whitespace errors; every changed hunk belongs either to this plan or to a clearly preserved pre-existing user change.

- [ ] **Step 6: Stop before destructive local database rebuild**

Report that source verification is complete and request action-time confirmation before dropping and recreating `kasi_promotion`. Do not issue `DROP DATABASE`, delete schema data, or rerun initialization automatically.

## Plan Self-Review

- The plan covers the approved schema, API, validation, sync, playback, UI, permissions, documentation, and rebuild boundary.
- `mediaRootDomain` is consistently used in Java and TypeScript; `media_root_domain` is consistently used in SQL.
- The validator accepts only exact root or `.`-delimited suffix matches and never learns unknown hosts.
- No task introduces an independent whitelist page, list table, wildcard, fallback, cache, compatibility migration, or time-zone change.
- Existing dirty-file overlap is explicitly preserved and implementation commits are deferred until hunks can be isolated safely.
