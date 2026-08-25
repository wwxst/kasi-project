# Drama Catalog Response Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Return all persisted GoodShort catalog metadata fields from admin and user drama catalog responses.

**Architecture:** Add the seven remote metadata properties to the existing list/detail VOs. Convert the stored JSON label string to `List<String>` in the existing service mapping methods; keep persistence and synchronization unchanged.

**Tech Stack:** Spring Boot, MyBatis, Jackson, JUnit 5, MockMvc.

---

### Task 1: Lock the response contract with failing controller tests

**Files:**
- Modify: `src/test/java/com/kasi/backend/drama/controller/AdminDramaCatalogControllerTest.java`
- Modify: `src/test/java/com/kasi/backend/drama/controller/UserPromotionDramaControllerTest.java`

- [ ] Add representative persisted metadata to the test drama fixtures and assert `titleZh`, `labelNames`, `categoryName`, `remoteRank`, `novelType`, `novelSubType`, and `remoteCreatedAt` in list/detail JSON responses.
- [ ] Run the two controller test classes and confirm compilation/test failure because the response VOs do not expose the new properties.

### Task 2: Add fields and mappings

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaListItemVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaDetailVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogAdminServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`

- [ ] Add the seven fields using `List<String>` for `labelNames`.
- [ ] Parse the stored JSON label string with the existing Jackson mapper style, treating null/blank/malformed input as an empty list.
- [ ] Map the fields in admin list/detail and user list conversions.

### Task 3: Verify

**Files:**
- No additional files.

- [ ] Run the focused controller tests and the existing drama sync/persistence/adapter tests.
- [ ] Run `git diff --check` and confirm no unrelated files were changed by this task.
