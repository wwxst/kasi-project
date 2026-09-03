# Root Git Repository Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Make `E:\JavaProjects\kasi-project` the single Git repository root while preserving the three application directories and all existing user work.

**Architecture:** The root repository will contain `kasi-backend`, `kasi-admin-web`, and `kasi-user-web` as ordinary subdirectories. Existing child repository histories will be preserved as safety artifacts and reconciled into the root history; no force push or destructive reset is allowed. The migration stops before cutover whenever dirty files, active worktrees, or remote divergence cannot be accounted for.

**Tech Stack:** Git, PowerShell, existing Java/Maven and frontend toolchains.

---

### Task 1: Freeze and inventory repository state

**Files:**
- Read-only: `E:\JavaProjects\kasi-project\kasi-backend\.git`
- Read-only: `E:\JavaProjects\kasi-project\kasi-admin-web\.git`
- Read-only: `E:\JavaProjects\kasi-project\kasi-user-web`

- [ ] Record root, child repository, branch, remote, dirty-file, and worktree state.
- [ ] Confirm the user has frozen parallel editing and identify every dirty or untracked path.
- [ ] Stop and report if any active worktree or dirty file lacks an explicit disposition.

### Task 2: Create reversible safety artifacts

**Files:**
- Create outside repositories: `E:\JavaProjects\kasi-project-migration-backup-20260828\`

- [ ] Create Git bundles for each child repository and export status/diff/untracked manifests.
- [ ] Verify bundle integrity with `git bundle verify` and record commit IDs.
- [ ] Do not delete or alter child `.git` directories during this task.

### Task 3: Assemble the root repository

**Files:**
- Create: `E:\JavaProjects\kasi-project\.git\`
- Modify only as required: root `.gitignore`, root `.gitattributes`, root `AGENTS.md`, root `DEVELOPMENT.md`

- [ ] Initialize the root repository only after the freeze checkpoint passes.
- [ ] Import the approved `master` baselines for backend and admin history without importing dirty files or backend `codex/*` worktree state.
- [ ] Add `kasi-user-web` as the current source snapshot because it has no child history.
- [ ] Preserve child `.git` directories until root verification is complete; then remove nested metadata only with an explicit recorded cutover step.

### Task 4: Verify root build and repository boundaries

**Files:**
- Read-only verification across `kasi-backend`, `kasi-admin-web`, and `kasi-user-web`

- [ ] Run backend compile/tests with Java 25.
- [ ] Run the existing admin frontend checks excluding known worktree copies and record baseline failures separately.
- [ ] Run the user frontend build or documented smoke check.
- [ ] Verify `git status`, staged paths, and history from the root; confirm no nested `.git` is used for normal commits.

### Task 5: Publish without overwriting the remote

- [ ] Compare root `HEAD`, tracking branch, and `git ls-remote` before publication.
- [ ] Push a new migration branch first if root `master` is not a fast-forward descendant of the remote.
- [ ] Never force-push or overwrite the existing remote `master`.
- [ ] Report the exact branch, commit, remote URL, and any follow-up merge/PR required.
