# GoodShort Code Conversion Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Present one code-level conversion summary for LANDING/ONELINK and explicitly report cross-media code conflicts without changing code generation.

**Architecture:** Keep two `promotion_link` rows as persistence records, but make user/admin list queries return one projection row per `pid + customParams + bookId + externalCode`. The projection carries both URLs, one set of code-level metrics, and an explicit conflict flag; conflicting media identities receive no silently selected metrics.

**Tech Stack:** Spring Boot/MyBatis/H2 tests, React/TDesign user web, React/Ant Design admin web, TypeScript/Vitest.

---

### Task 1: Lock the backend projection contract with failing tests

**Files:**
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkPersistenceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkPersistenceTest.java` fixture helpers
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/vo` only if an existing VO test location is required

- [ ] Add a test with LANDING and ONELINK rows sharing one code and one media type; assert the user/admin query returns one row, both URLs, and one metrics set (`clickCount=10`, `orderCount=7`, `orderAmount` remains outside this link projection).
- [ ] Add a test with the same report identity and code across `TIKTOK` and `YOUTUBE`; assert both queries expose `analyticsConflict=true` and return no selected conversion metrics instead of choosing the minimum link id.
- [ ] Run the focused mapper tests and confirm they fail because the current SQL returns two rows and silently canonicalizes with `MIN(id)`.

Run from `kasi-backend`:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd "-Dtest=PromotionLinkPersistenceTest" test
```

Expected RED: row count remains two or the conflict is not surfaced.

### Task 2: Implement one-row code-level backend projections

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/PromotionLinkVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/AdminPromotionLinkVO.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionLinkMapper.xml`

- [ ] Add `landingUrl`, `oneLinkUrl`, and `analyticsConflict` to both link list VOs; keep `externalCode` and existing identity fields for compatibility, and do not add `orderAmount` to this API because the current contract intentionally excludes it.
- [ ] Replace each row-level list query with a grouped projection keyed by GoodShort report identity (`pid`, `custom_params`, `book_id`, `code`), selecting `MIN(l.id)` only as a stable projection id, conditional LANDING/ONELINK URLs, and `COUNT(DISTINCT l.media_type) > 1` as the conflict flag.
- [ ] Join analytical reports only when the grouped identity has one media type; when `analyticsConflict=true`, return zero/null metrics and never attach them to any media variant.
- [ ] Update count queries to count grouped projections, preserving filters for user/provider/code/tracking where applicable without changing order attribution queries.
- [ ] Run the focused mapper tests and confirm GREEN for one-row aggregation, both URLs, single metrics, and explicit conflict.

### Task 3: Render code-level conversion in both frontends

**Files:**
- Modify: `kasi-user-web/src/features/promotionLinks/types.ts`
- Modify: `kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`
- Modify: `kasi-admin-web/src/features/promotion/promotionLinkTypes.ts`
- Modify: `kasi-admin-web/src/pages/promotion/PromotionLinkPage.tsx`
- Modify: corresponding user/admin page tests

- [ ] Extend TypeScript types with `landingUrl`, `oneLinkUrl`, and `analyticsConflict`.
- [ ] Replace the separate variant URL row presentation with one code row containing the code, LANDING URL, ONELINK URL, and a single “口令转化” metric section.
- [ ] Render an explicit conflict message when `analyticsConflict` is true and suppress metric values for that row.
- [ ] Keep `orderAmount` absent from the link pages because current product documentation excludes it from this endpoint; do not touch the order page.
- [ ] Add tests proving two variant records are displayed as one code summary, metrics are not doubled or assigned to a variant, and conflict text is visible.

### Task 4: Documentation and regression coverage

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `docs/architecture/current.md`

- [ ] Document that conversion belongs to the GoodShort code identity, not `linkVariant`; list both URLs in one projection and the conflict behavior for cross-media code collisions.
- [ ] Do not alter the generation, `20005`, unique-key, order, or commission sections.
- [ ] Run backend focused tests plus user/admin `pnpm check`.

### Task 5: Final verification

- [ ] Run `kasi-backend/.\mvnw.cmd verify` under Java 25.
- [ ] Run `pnpm check` in `kasi-user-web` and `kasi-admin-web`.
- [ ] Run root `git diff --check` and inspect `git status --short`.
- [ ] Confirm no files under `promotion_order`, commission, settlement, or GoodShort generation adapter changed.

