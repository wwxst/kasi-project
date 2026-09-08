# Module 4 Promotion Links Final Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete PromotionLink creation semantics and expose GoodShort analytical metrics on the user promotion-task list without adding user-visible states or actions.

**Architecture:** Keep `promotion_link` as the task model. Aggregate `promotion_analytical_report` during the existing user-owned link query using code plus PID, book, and user identifiers; enforce request identity in the existing persistence service and apply a small provider-specific two-second limiter in the GoodShort adapter.

**Tech Stack:** Java 25, Spring Boot, Jakarta Validation, MyBatis XML, JUnit 5, React 19, TypeScript, TDesign, Vitest.

---

### Task 1: Lock backend behavior with failing tests

**Files:**
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkPersistenceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/UserPromotionLinkControllerTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkRateLimiterTest.java`

- [ ] Add a real mapper test proving daily metrics aggregate only when code, PID, book and user all match.
- [ ] Add service tests proving a reused request key cannot change provider, drama, media set or link variant.
- [ ] Add HTTP validation coverage for duplicate `mediaTypes`.
- [ ] Add a deterministic limiter test proving the same provider tuple waits two seconds and a different tuple does not.
- [ ] Run the focused tests and confirm they fail for the missing behavior.

### Task 2: Implement backend metrics and creation constraints

**Files:**
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/CreatePromotionLinkDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/entity/PromotionLink.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/PromotionLinkVO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionLinkMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkRateLimiter.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`

- [ ] Reject duplicate media types with Jakarta/Hibernate validation.
- [ ] Validate reused request-key batch identity before resetting any row.
- [ ] Aggregate seven counters through the existing link query and expose them in `PromotionLinkVO`; do not expose `orderAmount`.
- [ ] Invoke the dedicated two-second limiter immediately before the GoodShort generation HTTP call.
- [ ] Run the focused backend suite and make it green.

### Task 3: Lock and implement user behavior

**Files:**
- Modify: `kasi-user-web/src/pages/drama/DramaPage.test.tsx`
- Modify: `kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.test.tsx`
- Modify: `kasi-user-web/src/features/promotionLinks/types.ts`
- Modify: `kasi-user-web/src/pages/drama/DramaPage.tsx`
- Modify: `kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`

- [ ] Add failing tests for `complete=false` and the final thirteen columns.
- [ ] Keep the creation dialog open and show a non-success message for incomplete batches.
- [ ] Render the final fields, remove the link-type column and unused `customParams` type, and keep status/error/action/orderAmount absent.
- [ ] Run the focused user-web tests and make them green.

### Task 4: Synchronize current documentation and verify

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-user-web/README.md`
- Modify: `kasi-user-web/AGENTS.md`

- [ ] Replace stale module-four and analytical-report boundaries with the verified current chain.
- [ ] Run backend `mvnw.cmd verify`, user-web `pnpm check`, and root `git diff --check`.
- [ ] Inspect the final diff and confirm module-three working changes were preserved and not expanded.
