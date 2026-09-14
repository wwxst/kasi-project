# Shared GoodShort Code Order Attribution Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with test-first verification. No commit is authorized for this task.

**Goal:** Keep GoodShort order attribution correct when LANDING and ONELINK share one external code, without selecting an arbitrary link variant.

**Architecture:** Replace the order link-row lookup with a projection that returns only the uniquely determined user and local drama. `searchCode` remains the provider's order evidence; new orders leave `promotion_link_id` and `tracking_no` null. Commission calculation and analytical-report code-level attribution remain unchanged.

**Tech Stack:** Java 25, Spring, MyBatis XML, JUnit 5, Mockito, H2 test database.

---

### Task 1: Prove the shared-code attribution contract

**Files:**
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionOrderServiceTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkPersistenceTest.java`

- [ ] Add a service test with a mapper projection containing only `userId` and `dramaId`; assert the inserted paid order preserves `searchCode`, sets those two fields, leaves `promotionLinkId/trackingNo` null, and still calculates the existing commission snapshot.
- [ ] Add a persistence test with two successful links sharing one `externalCode` and differing only by `linkVariant`; assert the attribution query returns one user/drama projection without relying on row order.
- [ ] Run the focused tests and confirm the new expectations fail against the current `PromotionLink` row lookup.

### Task 2: Remove arbitrary link selection from the attribution boundary

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/PromotionOrderAttribution.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`
- Modify: `kasi-backend/src/main/resources/mapper/PromotionLinkMapper.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`

- [ ] Define a projection carrying only `userId` and `dramaId`.
- [ ] Change `findForOrderAttribution` to return that projection and select the matching joined identity without `ORDER BY l.id LIMIT 1`; keep filters on connection, partner, external drama, user number, and external code.
- [ ] Set only `userId` and `dramaId` from the projection in `PromotionOrderServiceImpl`; do not set `promotionLinkId` or `trackingNo`.
- [ ] Leave `searchCode` copying, commission rule lookup, commission calculation, and analytical-report mappers untouched.

### Task 3: Verify all downstream consumers

**Files:**
- Inspect: `kasi-backend/src/main/resources/mapper/PromotionOrderMapper.xml`
- Inspect: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderUserServiceImpl.java`
- Inspect: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderAdminServiceImpl.java`
- Inspect: related VO and controller tests

- [ ] Confirm nullable `tracking_no/promotion_link_id` do not filter attributed order queries or commission summaries.
- [ ] Confirm user/admin VOs tolerate null tracking numbers; preserve `searchCode` in order records and responses where already exposed.
- [ ] Run the focused order, mapper, analytical-report, and commission tests, then the backend canonical Gate and `git diff --check`.
