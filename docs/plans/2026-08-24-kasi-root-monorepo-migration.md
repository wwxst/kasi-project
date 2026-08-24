# Kasi Root Monorepo Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `kasi-admin-web`、`kasi-backend` 和 `kasi-user-web` 安全迁移到 `E:/JavaProjects/kasi-project` 根级 Git monorepo，保留可用历史、统一文档和未来推送入口。

**Architecture:** 原工作区只作为冻结来源，在同盘临时目录中完成 bundle、历史路径改写、源码快照叠加、文档重组和验证。根仓库验证并推送到新的私有 `wwxst/kasi-project` 远程后，再通过同盘目录改名完成切换；旧工作区保留为回滚副本，不直接删除嵌套 `.git`。

本计划建立一个真正的根级仓库，不使用 Git submodule；三个应用保持独立构建和运行边界，只有 Git、文档和跨项目发布入口统一。

**Tech Stack:** Git 2.45.1、Python 3.13、`git-filter-repo==2.47.0`、PowerShell、pnpm、React/Vite/Vitest、Java 25、Maven Wrapper。

---

## File Responsibilities

- `E:/JavaProjects/kasi-project/docs/superpowers/specs/2026-08-24-kasi-root-monorepo-migration-design.md`: 已批准的迁移设计和验收边界。
- `E:/JavaProjects/kasi-project/docs/superpowers/plans/2026-08-24-kasi-root-monorepo-migration.md`: 本实施计划；迁移时纳入根仓库。
- `E:/JavaProjects/kasi-project-monorepo-migration`: 临时根仓库，仅在验证成功后切换到正式路径。
- `E:/JavaProjects/kasi-project-migration-backup-20260824`: bundle、patch、源码快照、commit map、哈希清单和审计输出。
- `E:/JavaProjects/kasi-project-legacy-20260824`: 切换后保留的旧工作区，不在本计划内删除。
- 根 `AGENTS.md`: 三个项目共同遵守的强制开发规则。
- 根 `DEVELOPMENT.md`: 变更分级、根因分析、阶段闸门、验证和文档同步流程。
- 根 `README.md`: 整体项目入口、三个应用边界、快速启动和文档索引。
- 根 `docs/README.md`: 当前架构、稳定契约、项目文档、ADR、计划和归档的唯一导航。
- 根 `docs/architecture/current.md`: 三应用当前拓扑、调用方向和数据所有权。
- 根 `docs/adr/ADR-0001-root-monorepo.md`: 本次 monorepo 架构决策和实施证据。
- 根 `docs/development/gaps.md`: 跨项目尚未统一的工程事项。
- 根 `docs/projects/*.md`: 每个应用的当前职责、技术栈、命令和专属边界。
- 子项目 `README.md`: 只保留该应用的启动、环境变量和构建说明。
- 子项目 `AGENTS.md`: 只保留该应用特有的技术约束；没有特有约束时不创建。

### Task 1: Freeze the workspace and create recovery evidence

**Files:**
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/`
- Read: `E:/JavaProjects/kasi-project/kasi-admin-web/`
- Read: `E:/JavaProjects/kasi-project/kasi-backend/`
- Read: `E:/JavaProjects/kasi-project/kasi-user-web/`

- [ ] **Step 1: Stop at the migration-window checkpoint**

Report the current dirty files, current branches, and registered worktrees. The default integration baseline is `master` for both Git repositories; current feature branches and dirty files are backup-only and are not merged into root `master`. Ask the user to confirm both the freeze and this baseline. If current feature work must be in the first root release, stop and require that work to be separately reviewed and merged into the source `master` before restarting this plan.

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
git -C "$Root\kasi-admin-web" status --short --branch
git -C "$Root\kasi-backend" status --short --branch
git -C "$Root\kasi-backend" worktree list --porcelain
```

Expected: output is captured in the task transcript; user explicitly confirms the freeze.

- [ ] **Step 2: Verify all migration paths before creating anything**

Run from `E:/JavaProjects`:

```powershell
$Root = (Resolve-Path -LiteralPath 'E:\JavaProjects\kasi-project').Path
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$Legacy = 'E:\JavaProjects\kasi-project-legacy-20260824'

if ($Root -ne 'E:\JavaProjects\kasi-project') { throw "Unexpected root: $Root" }
foreach ($path in @($Stage, $Backup, $Legacy)) {
    if (Test-Path -LiteralPath $path) { throw "Migration path already exists: $path" }
    if (-not $path.StartsWith('E:\JavaProjects\')) { throw "Path outside migration parent: $path" }
}
```

Expected: no output and exit code 0.

- [ ] **Step 3: Verify the selected baseline refs**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$adminBase = (git -C "$Root\kasi-admin-web" rev-parse --verify refs/heads/master).Trim()
$backendBase = (git -C "$Root\kasi-backend" rev-parse --verify refs/heads/master).Trim()
"ADMIN_BASE=$adminBase"
"BACKEND_BASE=$backendBase"
```

Expected: both commands resolve a commit. If either `master` ref is absent or not approved as the integration baseline, stop before creating the temporary repository.

- [ ] **Step 4: Create backup directories**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
@('bundles','patches','snapshots','manifests','imports','tools','audit') | ForEach-Object {
    New-Item -ItemType Directory -Path (Join-Path $Backup $_) | Out-Null
}
Get-ChildItem -LiteralPath $Backup | Select-Object Name
```

Expected: exactly the seven named directories.

- [ ] **Step 5: Record repository and worktree state**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

git -C "$Root\kasi-admin-web" status --porcelain=v2 --branch |
    Set-Content -Encoding utf8 "$Backup\audit\kasi-admin-web-status.txt"
git -C "$Root\kasi-admin-web" show-ref |
    Set-Content -Encoding utf8 "$Backup\audit\kasi-admin-web-refs.txt"
git -C "$Root\kasi-backend" status --porcelain=v2 --branch |
    Set-Content -Encoding utf8 "$Backup\audit\kasi-backend-status.txt"
git -C "$Root\kasi-backend" show-ref |
    Set-Content -Encoding utf8 "$Backup\audit\kasi-backend-refs.txt"
git -C "$Root\kasi-backend" worktree list --porcelain |
    Set-Content -Encoding utf8 "$Backup\audit\kasi-backend-worktrees.txt"

Get-ChildItem "$Backup\audit" | Select-Object Name,Length
```

Expected: five non-empty audit files.

- [ ] **Step 6: Create and verify full Git bundles**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

git -C "$Root\kasi-admin-web" bundle create "$Backup\bundles\kasi-admin-web.bundle" --all
git -C "$Root\kasi-backend" bundle create "$Backup\bundles\kasi-backend.bundle" --all
git -C "$Root\kasi-admin-web" bundle verify "$Backup\bundles\kasi-admin-web.bundle"
git -C "$Root\kasi-backend" bundle verify "$Backup\bundles\kasi-backend.bundle"
Get-FileHash "$Backup\bundles\*.bundle" -Algorithm SHA256
```

Expected: both bundles report that they are okay and both SHA-256 hashes are printed.

- [ ] **Step 7: Export tracked working changes with monorepo path prefixes**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

git -C "$Root\kasi-admin-web" diff HEAD --binary `
    --src-prefix=a/kasi-admin-web/ --dst-prefix=b/kasi-admin-web/ `
    --output="$Backup\patches\kasi-admin-web-working.patch"
git -C "$Root\kasi-backend" diff HEAD --binary `
    --src-prefix=a/kasi-backend/ --dst-prefix=b/kasi-backend/ `
    --output="$Backup\patches\kasi-backend-working.patch"

Get-Item "$Backup\patches\*.patch" | Select-Object Name,Length
```

Expected: both patch files exist; zero length is allowed only when the corresponding working tree has no tracked changes.

- [ ] **Step 8: Archive current source trees without local/generated state**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

tar -a -cf "$Backup\snapshots\kasi-admin-web.zip" `
    --exclude=.git --exclude=.worktrees --exclude=node_modules --exclude=dist `
    --exclude=coverage --exclude=.idea --exclude=.env --exclude=.env.local `
    -C "$Root\kasi-admin-web" .

tar -a -cf "$Backup\snapshots\kasi-backend.zip" `
    --exclude=.git --exclude=.worktrees --exclude=target --exclude=.idea `
    --exclude=dump.rdb --exclude=.env --exclude=.env.local `
    -C "$Root\kasi-backend" .

tar -a -cf "$Backup\snapshots\kasi-user-web.zip" `
    --exclude=.git --exclude=.superpowers --exclude=node_modules --exclude=dist `
    --exclude=coverage --exclude=.idea --exclude=.env --exclude=.env.local `
    -C "$Root\kasi-user-web" .

Get-FileHash "$Backup\snapshots\*.zip" -Algorithm SHA256
```

Expected: three snapshot hashes are printed.

- [ ] **Step 9: Verify recovery evidence before history rewriting**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$required = @(
    "$Backup\bundles\kasi-admin-web.bundle",
    "$Backup\bundles\kasi-backend.bundle",
    "$Backup\patches\kasi-admin-web-working.patch",
    "$Backup\patches\kasi-backend-working.patch",
    "$Backup\snapshots\kasi-admin-web.zip",
    "$Backup\snapshots\kasi-backend.zip",
    "$Backup\snapshots\kasi-user-web.zip"
)
$missing = $required | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { throw "Missing recovery evidence: $($missing -join ', ')" }
'recovery-evidence=complete'
```

Expected: `recovery-evidence=complete`.

### Task 2: Install the pinned history-rewrite tool in isolation

**Files:**
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/tools/venv/`

- [ ] **Step 1: Create an isolated Python environment**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
py -3 -m venv "$Backup\tools\venv"
& "$Backup\tools\venv\Scripts\python.exe" -m pip install --disable-pip-version-check git-filter-repo==2.47.0
& "$Backup\tools\venv\Scripts\python.exe" -m pip show git-filter-repo
```

Expected: installation succeeds and the reported version corresponds to `2.47.0`.

### Task 3: Rewrite backend and admin history in disposable mirror clones

**Files:**
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/imports/kasi-backend.git/`
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/imports/kasi-admin-web.git/`
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/manifests/*-commit-map.txt`

- [ ] **Step 1: Create independent mirror clones**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

git clone --mirror --no-hardlinks "$Root\kasi-backend" "$Backup\imports\kasi-backend.git"
git clone --mirror --no-hardlinks "$Root\kasi-admin-web" "$Backup\imports\kasi-admin-web.git"
git -C "$Backup\imports\kasi-backend.git" fsck --full
git -C "$Backup\imports\kasi-admin-web.git" fsck --full
```

Expected: both clones complete and both `git fsck` commands exit 0.

- [ ] **Step 2: Rewrite all backend refs under `kasi-backend/`**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$FilterRepo = "$Backup\tools\venv\Scripts\git-filter-repo.exe"
Push-Location "$Backup\imports\kasi-backend.git"
try {
    & $FilterRepo --to-subdirectory-filter kasi-backend --force
    if ($LASTEXITCODE -ne 0) { throw 'Backend history rewrite failed' }
} finally {
    Pop-Location
}
$backendMap = Get-ChildItem -LiteralPath "$Backup\imports\kasi-backend.git" -Filter commit-map -Recurse -File | Select-Object -First 1
if (-not $backendMap) { throw 'Backend commit map missing' }
Copy-Item -LiteralPath $backendMap.FullName -Destination "$Backup\manifests\kasi-backend-commit-map.txt"
```

Expected: rewrite exits 0 and the commit map is non-empty.

- [ ] **Step 3: Rewrite all admin refs under `kasi-admin-web/`**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$FilterRepo = "$Backup\tools\venv\Scripts\git-filter-repo.exe"
Push-Location "$Backup\imports\kasi-admin-web.git"
try {
    & $FilterRepo --to-subdirectory-filter kasi-admin-web --force
    if ($LASTEXITCODE -ne 0) { throw 'Admin history rewrite failed' }
} finally {
    Pop-Location
}
$adminMap = Get-ChildItem -LiteralPath "$Backup\imports\kasi-admin-web.git" -Filter commit-map -Recurse -File | Select-Object -First 1
if (-not $adminMap) { throw 'Admin commit map missing' }
Copy-Item -LiteralPath $adminMap.FullName -Destination "$Backup\manifests\kasi-admin-web-commit-map.txt"
```

Expected: rewrite exits 0 and the commit map is non-empty.

- [ ] **Step 4: Verify rewritten trees and commit maps**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

git -C "$Backup\imports\kasi-backend.git" fsck --full
git -C "$Backup\imports\kasi-admin-web.git" fsck --full
git -C "$Backup\imports\kasi-backend.git" ls-tree --name-only HEAD
git -C "$Backup\imports\kasi-admin-web.git" ls-tree --name-only HEAD
Get-Item "$Backup\manifests\*-commit-map.txt" | Select-Object Name,Length
```

Expected: `ls-tree` shows only `kasi-backend` or `kasi-admin-web` at the repository root, and both maps are non-empty.

### Task 4: Assemble the temporary root repository from approved baselines

**Files:**
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/.git/`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/kasi-admin-web/`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/kasi-backend/`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/kasi-user-web/`

- [ ] **Step 1: Initialize the temporary root repository**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
git init -b master "$Stage"
git -C "$Stage" commit --allow-empty -m "chore: initialize Kasi monorepo"
```

Expected: one root commit on `master`.

- [ ] **Step 2: Fetch rewritten backend branches into an archive namespace**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$BackendBase = (git -C "$Root\kasi-backend" rev-parse --verify refs/heads/master).Trim()
if ([string]::IsNullOrWhiteSpace($BackendBase)) { throw 'Backend master baseline is unavailable' }

git -C "$Stage" remote add backend-import "$Backup\imports\kasi-backend.git"
git -C "$Stage" fetch backend-import '+refs/heads/*:refs/heads/archive/backend/*'
git -C "$Stage" merge --allow-unrelated-histories --no-ff `
    "archive/backend/master" -m "chore: import kasi-backend history"
```

Expected: merge succeeds and `kasi-backend/pom.xml` exists.

- [ ] **Step 3: Fetch rewritten admin history into an archive namespace**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$AdminBase = (git -C "$Root\kasi-admin-web" rev-parse --verify refs/heads/master).Trim()
if ([string]::IsNullOrWhiteSpace($AdminBase)) { throw 'Admin master baseline is unavailable' }

git -C "$Stage" remote add admin-import "$Backup\imports\kasi-admin-web.git"
git -C "$Stage" fetch admin-import '+refs/heads/*:refs/heads/archive/admin/*'
git -C "$Stage" merge --allow-unrelated-histories --no-ff `
    "archive/admin/master" -m "chore: import kasi-admin-web history"
```

Expected: merge succeeds and `kasi-admin-web/package.json` exists.

- [ ] **Step 4: Materialize approved baseline trees and add the user frontend**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

New-Item -ItemType Directory -Force "$Stage\kasi-user-web" | Out-Null
git -C "$Root\kasi-admin-web" archive master | tar -xf - -C "$Stage"
git -C "$Root\kasi-backend" archive master | tar -xf - -C "$Stage"
tar -xf "$Backup\snapshots\kasi-user-web.zip" -C "$Stage\kasi-user-web"

git -C "$Stage" add -- kasi-admin-web kasi-backend kasi-user-web
git -C "$Stage" diff --cached --check
git -C "$Stage" commit -m "chore: add kasi-user-web source snapshot"
```

Expected: root `master` contains only the approved admin/backend `master` trees plus the user frontend snapshot; feature-branch and dirty-worktree changes remain only in backup and legacy sources. Ignored build and local-state directories are absent.

- [ ] **Step 5: Remove import remotes without deleting archive refs**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
git -C "$Stage" remote remove backend-import
git -C "$Stage" remote remove admin-import
git -C "$Stage" branch --list 'archive/*'
```

Expected: no remotes remain and archive branches are still listed.

### Task 5: Create root governance and ignore rules

**Files:**
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/.gitignore`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/AGENTS.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/DEVELOPMENT.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/README.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/README.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/architecture/current.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/adr/ADR-0001-root-monorepo.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/development/gaps.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/development/testing.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/development/git-and-release.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/projects/kasi-admin-web.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/projects/kasi-backend.md`
- Create: `E:/JavaProjects/kasi-project-monorepo-migration/docs/projects/kasi-user-web.md`

- [ ] **Step 1: Add the root ignore contract**

Create `.gitignore` with this content:

```gitignore
# Dependencies and build output
**/node_modules/
**/dist/
**/coverage/
**/target/
**/build/
**/*.tsbuildinfo

# Local environment and runtime state
**/.env
**/.env.local
**/dump.rdb
**/.superpowers/
**/.worktrees/
*.log

# Editors and operating systems
**/.idea/
**/.vscode/*
!**/.vscode/extensions.json
**/.DS_Store
**/*.iml
```

- [ ] **Step 2: Create the root documentation entry points**

Use the approved design as the source of truth. The files must have these exact responsibilities and headings:

```text
README.md
  1. 项目定位
  2. 应用组成
  3. 快速开始
  4. 跨项目开发流程
  5. 文档导航
  6. Git 与发布

AGENTS.md
  适用范围
  仓库级强制开发流程
  工作区保护
  跨项目变更闸门
  验证要求
  文档规则

DEVELOPMENT.md
  基本原则
  变更分级与闸门
  缺陷修复流程
  功能开发流程
  跨项目契约变更
  暂停并汇报条件
  完成检查表

docs/README.md
  当前架构
  稳定契约
  项目文档
  架构决策
  开发规范
  当前计划
  历史归档
```

Root rules must include: root-cause evidence before fixes, one problem per stage, minimal changes, L2/L3 approval gates, no speculative abstractions, no compatibility layer for unpublished APIs, current/planned separation, and root-only commit/push after migration.

- [ ] **Step 3: Record the current three-application architecture**

`docs/architecture/current.md` must state:

```text
kasi-admin-web -> kasi-backend /api/admin/** and /api/user/management/**
kasi-user-web  -> kasi-backend /api/user/**
kasi-backend   -> MySQL, Redis, GoodShort
```

It must also state that each application keeps an independent build and deployment boundary and that the root repository is the Git/documentation boundary, not a shared runtime.

- [ ] **Step 4: Convert the approved design into ADR-0001**

Copy the approved migration design and execution plan into the new root:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
New-Item -ItemType Directory -Force "$Stage\docs\adr", "$Stage\docs\plans" | Out-Null
Copy-Item -LiteralPath "$Root\docs\superpowers\specs\2026-08-24-kasi-root-monorepo-migration-design.md" `
    -Destination "$Stage\docs\adr\ADR-0001-root-monorepo.md"
Copy-Item -LiteralPath "$Root\docs\superpowers\plans\2026-08-24-kasi-root-monorepo-migration.md" `
    -Destination "$Stage\docs\plans\2026-08-24-kasi-root-monorepo-migration.md"
```

Then normalize the ADR header to:

```markdown
# ADR-0001: 使用根级 Monorepo 管理三个 Kasi 应用

- 日期：2026-08-24
- 状态：已批准
- 范围：Git 仓库、文档治理、三个应用的提交与推送入口
```

Keep the alternatives, migration, rollback, verification, and risk sections. Change status to `已实施` only after Task 10 passes.

- [ ] **Step 5: Move governance gaps into the root docs**

Move the content of `kasi-backend/docs/development-gaps.md` to `docs/development/gaps.md`. Add these monorepo-specific gaps if absent:

```text
- 跨项目 API 契约变更和前后端联调验收矩阵
- 三个应用独立部署但统一版本标记的发布策略
- 根仓库 CI 按路径选择项目验证的规则
- 历史 archive 分支和旧远程仓库的保留期限
```

The gap document must keep P0/P1/P2 priority and must not describe gaps as implemented behavior.

- [ ] **Step 6: Add exact project verification and Git release docs**

`docs/development/testing.md` must list the commands from Task 8 and state that each project is verified independently. `docs/development/git-and-release.md` must state:

```text
- All new branches are created from the root repository.
- Commit paths may span projects only when one business change requires it.
- Stage only intended files.
- Push only the root origin after migration.
- Publish proof requires local HEAD, tracking ref, and git ls-remote parity.
- Old subproject remotes are historical sources and are not normal push targets.
```

- [ ] **Step 7: Add one current project page per application**

Each `docs/projects/*.md` file must contain: responsibility, runtime/build stack, source directory, API boundary, exact development/test/build commands, environment prerequisites, and links to the scoped README. Use live `package.json`, `pom.xml`, code, and tests as authority; do not promote historical plan statements to current facts.

- [ ] **Step 8: Validate and commit root governance**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
git -C "$Stage" add -- .gitignore AGENTS.md DEVELOPMENT.md README.md docs
git -C "$Stage" diff --cached --check
git -C "$Stage" commit -m "docs: establish monorepo development governance"
```

Expected: commit succeeds with only root governance and docs changes.

### Task 6: Reclassify existing project documentation

**Files:**
- Move: `kasi-admin-web/docs/superpowers/**` -> `docs/archive/kasi-admin-web/superpowers/**`
- Move: `kasi-backend/docs/superpowers/**` -> `docs/archive/kasi-backend/superpowers/**`
- Move: `kasi-user-web/docs/superpowers/**` -> `docs/archive/kasi-user-web/superpowers/**`
- Keep or move active plans after status review: `docs/plans/`
- Modify: subproject `README.md` and scoped `AGENTS.md`

- [ ] **Step 1: Inventory every Markdown document before moving it**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
rg --files "$Stage\kasi-admin-web" "$Stage\kasi-backend" "$Stage\kasi-user-web" `
    -g '*.md' -g '!node_modules/**' -g '!dist/**' -g '!target/**' -g '!.worktrees/**' `
    | Sort-Object
```

Expected: a complete, reviewable Markdown list without generated directories.

- [ ] **Step 2: Classify current, active, and historical documents**

For each document, verify against current code/tests and assign exactly one destination:

```text
CURRENT   -> root current architecture/contracts/projects or scoped README
ACTIVE    -> docs/plans/<date>-<topic>.md with explicit approved/not-implemented status
HISTORICAL-> docs/archive/<project>/<original-relative-path>
```

Any unresolved contradiction is recorded in `docs/development/gaps.md`; it is not silently resolved by choosing the newest date.

- [ ] **Step 3: Move historical superpowers documents without rewriting their claims**

Use `git mv` for tracked documents and normal filesystem move followed by explicit staging for previously untracked documents. Preserve original relative filenames under each project archive. Add `docs/archive/README.md` explaining that archived documents are historical context, not current contracts.

- [ ] **Step 4: Reduce subproject documentation to scoped truth**

Update each subproject README to link to root `README.md`, `DEVELOPMENT.md`, `docs/README.md`, and its project page. Keep only project-specific prerequisites, environment variables, commands, API boundary and troubleshooting. Backend and user scoped `AGENTS.md` retain only Java/Flyway/security or user-frontend/TDesign-specific rules; create an admin scoped `AGENTS.md` only for admin-specific React/Ant Design rules.

- [ ] **Step 5: Check links, status markers, and duplicate current contracts**

Run this relative-link checker over all tracked Markdown files:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$errors = @()
$markdownFiles = git -C $Stage ls-files | Where-Object { $_.EndsWith('.md') }
foreach ($relativeFile in $markdownFiles) {
    $fullFile = Join-Path $Stage $relativeFile
    $content = Get-Content -Raw -Encoding utf8 $fullFile
    $matches = [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')
    foreach ($match in $matches) {
        $target = $match.Groups[1].Value.Trim('<','>')
        if ($target.StartsWith('#') -or $target -match '^(https?://|mailto:)') { continue }
        $pathOnly = ($target -split '#', 2)[0]
        $decoded = [Uri]::UnescapeDataString($pathOnly).Replace('/', '\')
        $resolved = Join-Path (Split-Path -Parent $fullFile) $decoded
        if (-not (Test-Path -LiteralPath $resolved)) {
            $errors += "$relativeFile -> $target"
        }
    }
}
if ($errors) { $errors; throw 'Broken Markdown links found' }
"markdown-links=valid files=$($markdownFiles.Count)"
```

Then search:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$placeholderPattern = @('T' + 'BD', 'T' + 'ODO', '待' + '定') -join '|'
rg -n $placeholderPattern "$Stage" -g '*.md' -g '!docs/archive/**'
rg -n "PENDING|ACTIVE|ENDED|commission-rules" "$Stage\README.md" "$Stage\AGENTS.md" "$Stage\docs" -g '*.md'
```

Expected: no unresolved placeholders outside archive; commission-rule current-contract hits are reviewed against implementation rather than accepted mechanically.

- [ ] **Step 6: Commit documentation reclassification**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$docPaths = @(
    'README.md', 'AGENTS.md', 'DEVELOPMENT.md', 'docs',
    'kasi-admin-web/README.md', 'kasi-backend/README.md', 'kasi-user-web/README.md',
    'kasi-admin-web/AGENTS.md', 'kasi-backend/AGENTS.md', 'kasi-user-web/AGENTS.md',
    'kasi-backend/DEVELOPMENT.md'
) | Where-Object { Test-Path -LiteralPath (Join-Path $Stage $_) }
git -C "$Stage" add -- $docPaths
git -C "$Stage" diff --cached --check
git -C "$Stage" commit -m "docs: reorganize project documentation"
```

Expected: only intended docs are committed. If `kasi-admin-web/AGENTS.md` does not exist because no scoped rules are needed, omit that path explicitly rather than using broad staging.

### Task 7: Verify source parity and repository history

**Files:**
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/audit/approved-baseline/`
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/manifests/source-*.json`
- Create: `E:/JavaProjects/kasi-project-migration-backup-20260824/manifests/stage-*.json`

- [ ] **Step 1: Generate source and staged code manifests**

Run this function to generate sorted JSON entries containing normalized relative path, byte length, and SHA-256. It excludes Git/documentation/build/local-state files so documentation reclassification does not mask source drift.

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
$Baseline = "$Backup\audit\approved-baseline"

foreach ($project in @('kasi-admin-web','kasi-backend','kasi-user-web')) {
    New-Item -ItemType Directory -Force "$Baseline\$project" | Out-Null
}
git -C "$Root\kasi-admin-web" archive master | tar -xf - -C "$Baseline\kasi-admin-web"
git -C "$Root\kasi-backend" archive master | tar -xf - -C "$Baseline\kasi-backend"
tar -xf "$Backup\snapshots\kasi-user-web.zip" -C "$Baseline\kasi-user-web"

function New-CodeManifest {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Output
    )

    $resolvedRoot = (Resolve-Path -LiteralPath $Source).Path
    $excludedDirectories = @(
        '.git', 'docs', 'node_modules', 'dist', 'coverage', 'target', 'build',
        '.idea', '.worktrees', '.superpowers'
    )
    $excludedFiles = @('.env', '.env.local', 'dump.rdb')

    $entries = Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File -Force |
        Where-Object {
            $relative = [IO.Path]::GetRelativePath($resolvedRoot, $_.FullName).Replace('\', '/')
            $segments = $relative.Split('/')
            -not ($segments | Where-Object { $excludedDirectories -contains $_ }) -and
            $excludedFiles -notcontains $_.Name -and
            $_.Extension -ne '.md' -and
            $_.Extension -ne '.log' -and
            $_.Extension -ne '.tsbuildinfo'
        } |
        ForEach-Object {
            [PSCustomObject]@{
                Path = [IO.Path]::GetRelativePath($resolvedRoot, $_.FullName).Replace('\', '/')
                Length = $_.Length
                Sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            }
        } |
        Sort-Object Path

    $entries | ConvertTo-Json -Depth 3 | Set-Content -Encoding utf8 $Output
}

foreach ($project in @('kasi-admin-web','kasi-backend','kasi-user-web')) {
    New-CodeManifest -Source "$Baseline\$project" -Output "$Backup\manifests\source-$project.json"
    New-CodeManifest -Source "$Stage\$project" -Output "$Backup\manifests\stage-$project.json"
}
```

Expected: six JSON files, two per project. The source side is the approved `master` baseline for admin/backend and the frozen user-web snapshot; the current dirty worktrees are intentionally not used for this parity comparison.

- [ ] **Step 2: Compare all three manifests**

Run:

```powershell
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
foreach ($project in @('kasi-admin-web','kasi-backend','kasi-user-web')) {
    $source = Get-Content -Raw -Encoding utf8 "$Backup\manifests\source-$project.json" |
        ConvertFrom-Json | ForEach-Object { "$($_.Path)|$($_.Length)|$($_.Sha256)" }
    $stage = Get-Content -Raw -Encoding utf8 "$Backup\manifests\stage-$project.json" |
        ConvertFrom-Json | ForEach-Object { "$($_.Path)|$($_.Length)|$($_.Sha256)" }
    $difference = Compare-Object $source $stage
    if ($difference) { $difference; throw "Source drift found: $project" }
    "$project=identical"
}
```

Expected: all three projects print `identical`. Any difference requires an approved migration edit before continuing.

- [ ] **Step 3: Verify imported history and commit maps**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'

git -C "$Stage" fsck --full
git -C "$Stage" log --all --format='%an|%aI|%s' -- kasi-backend | Select-Object -First 20
git -C "$Stage" log --all --format='%an|%aI|%s' -- kasi-admin-web | Select-Object -First 20
Get-Item "$Backup\manifests\*-commit-map.txt" | Select-Object Name,Length
```

Expected: fsck exits 0, both project histories are visible, and both commit maps are non-empty.

- [ ] **Step 4: Verify repository boundary**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$nested = @(
    "$Stage\kasi-admin-web\.git",
    "$Stage\kasi-backend\.git",
    "$Stage\kasi-user-web\.git"
) | Where-Object { Test-Path -LiteralPath $_ }
if ($nested) { $nested; throw 'Nested Git repositories found' }
git -C "$Stage" status --short --branch
```

Expected: no nested `.git`; root status is clean.

### Task 8: Run all project validation from the temporary root

**Files:**
- No source edits expected; failures stop execution and are diagnosed separately.

- [ ] **Step 1: Verify toolchain versions**

Run:

```powershell
node --version
pnpm --version
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
E:\JavaProjects\kasi-project-monorepo-migration\kasi-backend\mvnw.cmd -v
```

Expected: Node and pnpm satisfy each package's engines; Maven reports Java 25.

- [ ] **Step 2: Validate the admin frontend**

Run from `E:/JavaProjects/kasi-project-monorepo-migration/kasi-admin-web`:

```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
```

Expected: every command exits 0 with zero test failures and zero lint/type errors.

- [ ] **Step 3: Validate the user frontend**

Run from `E:/JavaProjects/kasi-project-monorepo-migration/kasi-user-web`:

```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
```

Expected: every command exits 0 with zero test failures and zero lint/type errors.

- [ ] **Step 4: Validate the backend on Java 25**

Run from `E:/JavaProjects/kasi-project-monorepo-migration/kasi-backend`:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

Expected: Maven reports `BUILD SUCCESS`; tests show zero failures and zero errors.

- [ ] **Step 5: Verify the root diff and clean status**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
git -C "$Stage" diff --check
git -C "$Stage" status --short --branch
```

Expected: no diff-check output and a clean `master` branch.

### Task 9: Create and publish the new private root remote

**Files:**
- External state: `https://github.com/wwxst/kasi-project`
- Modify: temporary repository Git remote configuration.

- [ ] **Step 1: Stop at the external-state checkpoint**

Report all Task 7 and Task 8 evidence. Obtain explicit authorization to create a **private** GitHub repository named `wwxst/kasi-project` and push `master` plus `archive/*` branches. Do not reuse or force-push `wwxst/kasi-backend`.

- [ ] **Step 2: Create the empty private GitHub repository**

GitHub CLI is not installed. Use the authenticated GitHub browser session to create `wwxst/kasi-project` as a private, empty repository with no generated README, license or `.gitignore`.

Expected: `git ls-remote https://github.com/wwxst/kasi-project.git` reaches an empty repository without `Repository not found`.

- [ ] **Step 3: Add the root remote and push**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
git -C "$Stage" remote add origin https://github.com/wwxst/kasi-project.git
git -C "$Stage" push -u origin master
git -C "$Stage" push origin 'refs/heads/archive/*:refs/heads/archive/*'
```

Expected: both pushes succeed without force.

- [ ] **Step 4: Verify remote parity**

Run:

```powershell
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$local = (git -C "$Stage" rev-parse HEAD).Trim()
$tracking = (git -C "$Stage" rev-parse origin/master).Trim()
$remote = ((git -C "$Stage" ls-remote origin refs/heads/master) -split '\s+')[0]
"LOCAL=$local"
"TRACKING=$tracking"
"REMOTE=$remote"
if ($local -ne $tracking -or $local -ne $remote) { throw 'Remote parity failed' }
```

Expected: `LOCAL`, `TRACKING`, and `REMOTE` are identical.

### Task 10: Cut over the validated repository at the original root path

**Files:**
- Move: `E:/JavaProjects/kasi-project` -> `E:/JavaProjects/kasi-project-legacy-20260824`
- Move: `E:/JavaProjects/kasi-project-monorepo-migration` -> `E:/JavaProjects/kasi-project`
- Preserve: old backend worktree links under the legacy repository.

- [ ] **Step 1: Recheck freeze and path invariants immediately before cutover**

Run from `E:/JavaProjects`:

```powershell
$Root = (Resolve-Path -LiteralPath 'E:\JavaProjects\kasi-project').Path
$Stage = (Resolve-Path -LiteralPath 'E:\JavaProjects\kasi-project-monorepo-migration').Path
$Legacy = 'E:\JavaProjects\kasi-project-legacy-20260824'

if ($Root -ne 'E:\JavaProjects\kasi-project') { throw "Unexpected root: $Root" }
if ($Stage -ne 'E:\JavaProjects\kasi-project-monorepo-migration') { throw "Unexpected stage: $Stage" }
if (Test-Path -LiteralPath $Legacy) { throw "Legacy target exists: $Legacy" }
git -C $Stage status --short --branch
git -C $Root\kasi-admin-web status --short --branch
git -C $Root\kasi-backend status --short --branch
```

Expected: staged monorepo is clean; source statuses exactly match the frozen audit. Any drift stops cutover and returns to Task 1.

- [ ] **Step 2: Obtain final cutover confirmation**

Report the exact resolved paths, remote parity hash and rollback commands. Obtain explicit confirmation before moving either directory.

- [ ] **Step 3: Rename the old root and staged repository on the same volume**

Run from `E:/JavaProjects` only:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
$Stage = 'E:\JavaProjects\kasi-project-monorepo-migration'
$Legacy = 'E:\JavaProjects\kasi-project-legacy-20260824'

foreach ($path in @($Root, $Stage, $Legacy)) {
    if (-not $path.StartsWith('E:\JavaProjects\')) { throw "Unsafe path: $path" }
}
Move-Item -LiteralPath $Root -Destination $Legacy
Move-Item -LiteralPath $Stage -Destination $Root
```

Expected: the new monorepo now resolves at `E:/JavaProjects/kasi-project`; the old workspace resolves at the legacy path.

- [ ] **Step 4: Repair legacy backend worktree references**

Run:

```powershell
$LegacyBackend = 'E:\JavaProjects\kasi-project-legacy-20260824\kasi-backend'
$Worktrees = @(
    'C:\Users\Administrator\.config\superpowers\worktrees\kasi-backend\admin-avatar-upload',
    'C:\Users\Administrator\.config\superpowers\worktrees\kasi-backend\admin-management',
    'C:\Users\Administrator\.config\superpowers\worktrees\kasi-backend\promotion-filing-mode'
)
git -C $LegacyBackend worktree repair @Worktrees
git -C $LegacyBackend worktree list --porcelain
foreach ($worktree in $Worktrees) { git -C $worktree status --short --branch }
```

Expected: all three legacy worktrees remain readable on their original branches.

- [ ] **Step 5: Verify the new root after cutover**

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
git -C $Root rev-parse --show-toplevel
git -C $Root status --short --branch
$nested = @(
    "$Root\kasi-admin-web\.git",
    "$Root\kasi-backend\.git",
    "$Root\kasi-user-web\.git"
) | Where-Object { Test-Path -LiteralPath $_ }
if ($nested) { $nested; throw 'Nested Git repositories found after cutover' }
$local = (git -C $Root rev-parse HEAD).Trim()
$tracking = (git -C $Root rev-parse origin/master).Trim()
$remote = ((git -C $Root ls-remote origin refs/heads/master) -split '\s+')[0]
if ($local -ne $tracking -or $local -ne $remote) { throw 'Post-cutover parity failed' }
"ROOT=$Root"
"HEAD=$local"
```

Expected: root path is exact, status is clean, no nested `.git` exists, and hashes match.

- [ ] **Step 6: Mark ADR-0001 implemented and publish final documentation state**

Change `docs/adr/ADR-0001-root-monorepo.md` from `已批准` to `已实施`. Add the validation date, root HEAD hash, imported commit-map locations, three-project verification results and legacy rollback path.

Run:

```powershell
$Root = 'E:\JavaProjects\kasi-project'
git -C $Root add -- docs/adr/ADR-0001-root-monorepo.md
git -C $Root diff --cached --check
git -C $Root commit -m "docs: record monorepo migration evidence"
git -C $Root push origin master

$local = (git -C $Root rev-parse HEAD).Trim()
$tracking = (git -C $Root rev-parse origin/master).Trim()
$remote = ((git -C $Root ls-remote origin refs/heads/master) -split '\s+')[0]
if ($local -ne $tracking -or $local -ne $remote) { throw 'Final parity failed' }
git -C $Root status --short --branch
```

Expected: final docs commit is pushed; all three hashes match and the root worktree is clean.

### Task 11: Preserve rollback sources and close the migration

**Files:**
- Preserve: `E:/JavaProjects/kasi-project-legacy-20260824`
- Preserve: `E:/JavaProjects/kasi-project-migration-backup-20260824`

- [ ] **Step 1: Confirm both rollback sources remain available**

Run:

```powershell
$Legacy = 'E:\JavaProjects\kasi-project-legacy-20260824'
$Backup = 'E:\JavaProjects\kasi-project-migration-backup-20260824'
if (-not (Test-Path -LiteralPath $Legacy)) { throw 'Legacy workspace missing' }
if (-not (Test-Path -LiteralPath $Backup)) { throw 'Migration backup missing' }
$Root = 'E:\JavaProjects\kasi-project'
git -C $Root bundle verify "$Backup\bundles\kasi-admin-web.bundle"
git -C $Root bundle verify "$Backup\bundles\kasi-backend.bundle"
```

Expected: both paths exist and both bundles verify.

- [ ] **Step 2: Record cleanup as a separate future decision**

Do not delete the legacy workspace, bundles, commit maps or snapshots in this plan. Add a P2 gap requiring one successful post-migration feature branch, merge and root push before cleanup can be proposed.

- [ ] **Step 3: Report final evidence**

Report:

```text
- New root path and branch
- Root HEAD / tracking / remote hashes
- Imported backend/admin commit-map files
- Admin test/lint/typecheck/format/build results
- User test/lint/typecheck/format/build results
- Backend test/compile results and Java version
- Legacy workspace and backup paths
- Old kasi-backend remote unchanged
- Root worktree clean
```
