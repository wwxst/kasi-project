# Frontend Direct MP4 Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Download the current drama's available free MP4 episodes directly in the browser without backend download tasks, FFmpeg, or ZIP archives.

**Architecture:** The drama page will trigger native browser downloads from each episode's `downloadUrl`. The existing playback path remains unchanged. The header download-task center and its task polling UI will be removed because browser-native downloads do not expose reliable page-level progress.

**Tech Stack:** React 19, TypeScript strict, TDesign React, Vitest, Testing Library, Vite.

---

### Task 1: Define direct-download behavior with failing tests

**Files:**

- Modify: `src/pages/drama/DramaPage.test.tsx`

- [ ] **Step 1: Replace the backend-task batch test with a browser-download test**

Mock two free MP4 episodes, spy on `document.createElement`, and assert that clicking `下载全部` creates two anchors with the original URLs and `.mp4` filenames. Keep the assertion that no backend task API is called.

- [ ] **Step 2: Add a single-episode direct-download test**

Open the drama viewer, click the first episode's `下载` button, and assert exactly one anchor is clicked with that episode's URL and filename.

- [ ] **Step 3: Run the focused test file and verify the new tests fail**

Run `pnpm vitest run src/pages/drama/DramaPage.test.tsx` from `E:\JavaProjects\kasi-project\kasi-user-web`.

Expected: the new tests fail because the page still calls `createDramaDownloadTask` and does not create browser download anchors.

### Task 2: Implement native MP4 downloads in the drama page

**Files:**

- Modify: `src/pages/drama/DramaPage.tsx`

- [ ] **Step 1: Add a small local helper for native downloads**

Create a helper that receives a URL and filename, creates an anchor, sets `href`, `download`, and `rel`, appends it to `document.body`, clicks it, and removes it. Use the existing drama title and episode sequence to generate names such as `The Story-第01集.mp4`.

- [ ] **Step 2: Replace task creation with single-episode direct download**

The episode action calls the helper when `episode.downloadUrl` exists and shows a short success message. It must not call the backend task API.

- [ ] **Step 3: Replace batch task creation with direct downloads for current free episodes**

Filter only `free` episodes with a non-empty `downloadUrl`, sort by `sequenceNo`, and trigger one native download per episode. The button remains disabled when the filtered list is empty. Show one summary message after triggering the batch.

- [ ] **Step 4: Remove page-local backend task state and UI**

Remove task creation, task polling, ZIP fetching, task status rendering, and related imports/query invalidation. Keep playback and promotion-link behavior unchanged.

- [ ] **Step 5: Run the focused drama tests and confirm they pass**

Run `pnpm vitest run src/pages/drama/DramaPage.test.tsx`.

Expected: all drama page tests pass, including the direct-download tests.

### Task 3: Remove the obsolete header download center

**Files:**

- Modify: `src/layout/components/Header/index.tsx`
- Delete: `src/layout/components/Header/DownloadTaskCenter.tsx`
- Delete: `src/layout/components/Header/DownloadTaskCenter.module.less`
- Delete: `src/layout/components/Header/DownloadTaskCenter.test.tsx`

- [ ] **Step 1: Remove the header component import and render**

Keep the menu and search controls unchanged; remove only the download-task center from the header.

- [ ] **Step 2: Delete the component, styles, and tests**

These files have no remaining user-facing consumer after the drama page stops creating backend tasks.

- [ ] **Step 3: Run targeted user-web tests and type checks**

Run `pnpm vitest run src/pages/drama/DramaPage.test.tsx src/layout/components/Header/HeaderIcon.test.tsx`, then `pnpm typecheck`.

Expected: targeted tests pass and TypeScript reports no errors introduced by the removal.

### Task 4: Verify formatting and document the current behavior

**Files:**

- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Update user-web documentation**

Document that drama episode downloads are browser-native MP4 downloads, batch download produces separate files for available free episodes, and no page-level progress or ZIP is provided.

- [ ] **Step 2: Run final checks**

Run `pnpm format:check`, `pnpm vitest run src/pages/drama/DramaPage.test.tsx src/layout/components/Header/HeaderIcon.test.tsx`, `pnpm typecheck`, and `git diff --check`.

Expected: the focused checks and formatting pass. Any pre-existing unrelated workspace failures must be reported separately.
