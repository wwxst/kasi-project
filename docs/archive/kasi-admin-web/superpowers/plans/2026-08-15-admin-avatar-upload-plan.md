# Admin Avatar Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow administrators to upload a cropped avatar from the administrator detail header while removing avatar URL text fields from administrator forms.

**Architecture:** The React client crops an image to a square and sends multipart data to a self or managed-admin endpoint. Spring Boot validates and stores the image under a configurable local directory, updates `sys_admin_user.avatar_url`, exposes UUID-named files through a public resource path, and cleans up replaced local files.

**Tech Stack:** React 19, Ant Design 6, Vitest/MSW, Spring Boot 4, MyBatis, MockMvc, Java 25

---

### Task 1: Backend avatar storage contract

**Files:**

- Create: `src/main/java/com/kasi/backend/admin/service/AdminAvatarStorageService.java`
- Create: `src/main/java/com/kasi/backend/admin/service/impl/AdminAvatarStorageServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/admin/config/AdminAvatarWebConfig.java`
- Test: `src/test/java/com/kasi/backend/admin/service/AdminAvatarStorageServiceTest.java`

- [x] Write tests that reject empty, oversized and non-image files and that store supported images with server-generated names.
- [x] Run the focused test and verify it fails because the storage service does not exist.
- [x] Implement configurable storage, content sniffing, UUID filenames, public URL generation and safe local-file deletion.
- [x] Run the focused test and verify it passes.

### Task 2: Backend avatar endpoints and persistence

**Files:**

- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminAuthController.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminAuthService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Test: `src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java`
- Test: `src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java`

- [x] Add MockMvc tests for self upload, managed upload and invalid files; retain the existing wildcard management permission coverage.
- [x] Run focused tests and verify the new routes fail before implementation.
- [x] Add `updateAvatar`, service orchestration, multipart endpoints and public resource authorization.
- [x] Run focused tests and verify they pass.

### Task 3: Frontend upload API and detail interaction

**Files:**

- Modify: `package.json`
- Modify: `pnpm-lock.yaml`
- Modify: `src/features/auth/authApi.ts`
- Modify: `src/features/management/adminManagementApi.ts`
- Modify: `src/features/management/ManagementTablePage.tsx`
- Modify: `src/pages/management/AdminManagementPage.tsx`
- Modify: `src/pages/management/management-page.css`
- Test: `src/App.test.tsx`

- [x] Add failing tests proving administrator forms omit avatar URL and the detail avatar invokes multipart upload.
- [x] Run the focused frontend test and verify the expected controls are missing.
- [x] Add crop/upload UI, per-record upload callback and loading/error behavior.
- [x] Route self uploads to `/api/admin/auth/avatar`, other uploads to `/api/admin/management/{id}/avatar`, and refresh the auth store for self uploads.
- [x] Run the focused tests and verify they pass.

### Task 4: Documentation and verification

**Files:**

- Modify: backend `README.md`, `AGENTS.md`, `.gitignore`, `application.properties`
- Modify: frontend `README.md`

- [x] Document upload directory, limits, routes and development behavior.
- [x] Run backend focused tests and the full Java 25 Maven suite.
- [x] Run frontend tests, typecheck, lint, format check and production build.
- [x] Run `git diff --check` in the backend worktree.
- [x] Verify the detail avatar interaction at desktop and mobile widths in the local browser.
