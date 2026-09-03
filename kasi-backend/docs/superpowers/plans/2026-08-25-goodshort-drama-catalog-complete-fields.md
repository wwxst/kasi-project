# GoodShort Drama Catalog Complete Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Persist and expose every GoodShort drama-list request/response field using local domain names.

**Architecture:** Keep provider DTOs aligned with GoodShort JSON, map them into provider-neutral records, persist normalized fields on `provider_drama`, and expose them through existing admin/user VOs. Keep the existing millisecond checkpoint internally and format it as `utimeStart`/`utimeEnd` at the HTTP boundary.

**Tech Stack:** Spring Boot, Jackson 3, MyBatis XML, Flyway, H2 MySQL mode, JUnit 5.

---

### Task 1: Add failing adapter coverage for every documented field and incremental parameters

**Files:**
- Modify: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortCatalogAdapterTest.java`

- [ ] Add a response fixture containing `bookNameZh`, `bookCover`, `labelNames`, `introduce`, `typeTwoName`, `rank`, `showStatus`, `novelType`, `novelSubType`, `ctime`, and `utime`; assert every corresponding `ProviderDramaRecord` value.
- [ ] Change the incremental request fixture to expect `utimeStart` and `utimeEnd` formatted as `yyyy-MM-dd HH:mm:ss`.
- [ ] Run the focused test and record the expected failures before production changes.

### Task 2: Extend provider DTO and neutral record mapping

**Files:**
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/dto/GoodShortBookData.java`
- Modify: `src/main/java/com/kasi/backend/provider/spi/ProviderDramaRecord.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `src/main/java/com/kasi/backend/provider/spi/DramaCatalogFetchRequest.java`

- [ ] Add aliases/types for all documented book fields, including `introduce` and `utime`.
- [ ] Add neutral fields `titleZh`, `labelNames`, `categoryName`, `remoteRank`, `novelType`, `novelSubType`, and `remoteCreatedAt`.
- [ ] Format the internal incremental watermark into `utimeStart`; add optional end time support and calculate the next watermark from the maximum returned `utime` when the response envelope has no custom watermark.
- [ ] Run the focused adapter tests and verify they pass.

### Task 3: Persist the complete catalog model

**Files:**
- Create: `src/main/resources/db/migration/V16__goodshort_drama_catalog_complete_fields.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/main/java/com/kasi/backend/drama/entity/ProviderDrama.java`
- Modify: `src/main/resources/mapper/ProviderDramaMapper.xml`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogSyncServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java`
- Modify: `src/test/java/com/kasi/backend/drama/service/DramaCatalogSyncServiceTest.java`

- [ ] Add nullable columns for Chinese title, labels JSON text, category, remote rank, novel type/subtype, and remote creation time.
- [ ] Map all neutral record fields in the upsert and result map without touching local status or promotion metadata.
- [ ] Add persistence and sync assertions for all new columns.
- [ ] Run the focused persistence and sync tests.

### Task 4: Return complete fields from existing catalog APIs

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaListItemVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaDetailVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogAdminServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Modify: relevant controller tests

- [ ] Add all new domain fields to list/detail VOs and both service mappings.
- [ ] Assert administrator and user responses include the complete field set.

### Task 5: Documentation and verification

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] Document the V16 migration, complete field mapping, and incremental request format.
- [ ] Run Java 25 compile, focused adapter/persistence/sync/controller tests, full tests where the existing worktree constructor mismatch is resolved, and `git diff --check`.
