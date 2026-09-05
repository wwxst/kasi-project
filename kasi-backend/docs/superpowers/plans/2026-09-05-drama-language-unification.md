# Drama Language Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the backend-owned configured drama language list drive localized labels and filter options in both web clients while preserving raw language codes.

**Architecture:** Reuse the effective `DramaSyncProperties.languages` list as the only supported-language source. Add a small backend language-label mapping for display names, expose `/api/drama/languages` to authenticated admin and user roles, and add `languageLabel` only to drama list/detail response VOs. Both frontends fetch `{value,label}` options and display backend labels while submitting raw values.

**Tech Stack:** Spring Boot, Java records/Lombok VOs, Spring Security, MyBatis services, React/TypeScript, TanStack Query, TDesign/Ant Design.

---

### Task 1: Backend language metadata contract

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/vo/DramaLanguageOptionVO.java`
- Create: `src/main/java/com/kasi/backend/drama/service/DramaLanguageService.java`
- Create: `src/main/java/com/kasi/backend/drama/service/impl/DramaLanguageServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/drama/controller/DramaLanguageController.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Test: `src/test/java/com/kasi/backend/drama/service/DramaLanguageServiceTest.java`
- Test: `src/test/java/com/kasi/backend/drama/controller/DramaLanguageControllerTest.java`

- [ ] Write failing service/controller tests for configured-language ordering, labels, and both-role access.
- [ ] Run the focused tests and confirm failure because the service and endpoint do not exist.
- [ ] Implement a label map and iterate `DramaSyncProperties.getLanguages()` for options; expose `GET /api/drama/languages`; permit authenticated `ADMIN` and `USER` roles through one matcher.
- [ ] Run focused backend tests and confirm pass.

### Task 2: Backend drama response labels

**Files:**
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaListItemVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaDetailVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogAdminServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Test: existing affected drama service/controller tests only.

- [ ] Add assertions that list/detail responses preserve `language` and return the matching `languageLabel`.
- [ ] Run affected tests and confirm the new assertions fail.
- [ ] Inject/reuse the language service in the two response mappers and populate `languageLabel` without changing persistence or query values.
- [ ] Run affected backend tests and confirm pass.

### Task 3: Admin client options and labels

**Files:**
- Modify: `kasi-admin-web/src/features/drama/dramaCatalogApi.ts`
- Modify: `kasi-admin-web/src/features/drama/dramaCatalogTypes.ts`
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaSyncModal.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaContentSyncModal.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaSyncStatusDrawer.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaSyncCenterPage.tsx`
- Delete: `kasi-admin-web/src/features/drama/dramaCatalogLocale.ts`
- Test: affected admin API/page tests.

- [ ] Add a client API/type test for `/api/drama/languages`.
- [ ] Run affected tests and confirm failure.
- [ ] Remove the local language map, fetch options through the query layer, render backend `languageLabel`, and keep filter/sync request values as raw codes.
- [ ] Run admin focused tests and typecheck.

### Task 4: User client options and labels

**Files:**
- Modify: `kasi-user-web/src/features/dramas/dramasApi.ts`
- Modify: `kasi-user-web/src/features/dramas/types.ts`
- Modify: `kasi-user-web/src/pages/drama/components/SearchForm.tsx`
- Modify: `kasi-user-web/src/pages/drama/DramaPage.tsx`
- Test: affected user API/search/page tests.

- [ ] Add a client API/type test for the shared language-options endpoint and a search test covering `JAPANESE` value with `日语` label.
- [ ] Run affected tests and confirm failure.
- [ ] Fetch options from the backend, remove hardcoded `ENGLISH/CHINESE` mappings, render `languageLabel`, and preserve raw query values.
- [ ] Run user focused tests and typecheck.

### Task 5: Verification

- [ ] Run backend focused tests, both frontend checks, and `git diff --check`.
- [ ] Verify only language-related files changed and report any unrelated baseline failures without altering them.
