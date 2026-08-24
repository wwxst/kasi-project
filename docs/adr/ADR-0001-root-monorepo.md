# Kasi 根级 Monorepo 迁移设计

- 日期：2026-08-24
- 状态：已实施（根仓库迁移和文档整理完成；管理端完整套件仍需在 LF 检出环境复核）
- 目标目录：`E:/JavaProjects/kasi-project`
- 默认分支：`master`
- 目标远程：新的 `https://github.com/wwxst/kasi-project.git`

## 1. 背景与已确认事实

Kasi 当前由三个同级应用组成：

- `kasi-admin-web`：管理端 React 应用，有独立 Git 历史，`master` 共 27 个提交，没有配置远程，当前存在未提交改动。
- `kasi-backend`：Spring Boot 后端，有独立 Git 历史和 `https://github.com/wwxst/kasi-backend.git` 远程，共 131 个提交，当前分支存在未提交改动；另有三个已注册的外部 Git worktree。
- `kasi-user-web`：用户端 React 应用，当前没有 `.git`，因此没有可导入的独立提交历史。

外层目录 `E:/JavaProjects/kasi-project` 当前不是 Git 仓库。拟使用的 `wwxst/kasi-project` GitHub 仓库当前不存在或不可访问。当前环境尚未安装 `git-filter-repo`。

## 2. 目标

1. 将三个应用纳入一个根级 Git 仓库，由根目录统一提交和推送。
2. 保留 `kasi-backend` 和 `kasi-admin-web` 的作者、时间、提交说明和可追溯历史。
3. 将 `kasi-user-web` 的当前受控源码作为首次纳管快照。
4. 将通用开发原则、架构决策、跨项目契约和文档索引上移到根目录。
5. 保持三个应用的源码、依赖、构建、测试和部署边界独立。
6. 在根仓库验证和远程一致性确认前，保留现有仓库作为可回滚来源。

## 3. 非目标

- 不把三个应用合并为一个运行进程或一个构建产物。
- 不在本次迁移中统一前端组件库、包版本、环境变量或部署平台。
- 不修改业务 API、数据库 schema、权限模型或页面行为。
- 不使用 Git submodule，也不要求以后分别推送三个子目录。
- 不把 `node_modules`、`dist`、`target`、IDE 配置、Redis dump、临时 worktree 或 `.superpowers` 运行状态纳入版本控制。
- 不直接覆盖或改写现有 `wwxst/kasi-backend` 远程仓库。

## 4. 目标仓库结构

```text
kasi-project/
  .git/
  .gitignore
  AGENTS.md
  DEVELOPMENT.md
  README.md
  docs/
    README.md
    architecture/
      current.md
    contracts/
    adr/
    development/
      gaps.md
      git-and-release.md
      testing.md
    projects/
      kasi-admin-web.md
      kasi-backend.md
      kasi-user-web.md
    plans/
    archive/
      kasi-admin-web/
      kasi-backend/
      kasi-user-web/
  kasi-admin-web/
  kasi-backend/
  kasi-user-web/
```

根目录负责仓库治理和跨项目事实，子目录继续负责各自应用代码。子项目可以保留短 `README.md` 和 scoped `AGENTS.md`，但只记录该应用特有的构建命令、技术约束和模块边界，不重复根级规则。

## 5. Git 历史迁移策略

### 5.1 迁移工具

使用固定版本的 `git-filter-repo` 在临时克隆中改写路径：

- 后端历史统一增加 `kasi-backend/` 前缀。
- 管理端历史统一增加 `kasi-admin-web/` 前缀。

`git-filter-repo` 当前未安装，因此安装与版本确认属于实施前置步骤。工具不可用或输出异常时停止迁移，不改用未经验证的字符串替换脚本处理 Git fast-export 数据。

### 5.2 基线选择

根仓库 `master` 只接收每个项目已确认的集成基线：

- 后端默认使用迁移时已经稳定并完成验证的 `master`。
- 管理端默认使用迁移时已经稳定并完成验证的 `master`。
- 用户端使用迁移冻结时的受控源码快照。

未合并功能分支不会自动进入根 `master`。后端现有分支在路径改写后保留为 `archive/backend/<原分支名>`；管理端历史保留为 `archive/admin/master`。后续开发统一从根仓库创建 `codex/*` 或业务分支。

### 5.3 未提交内容

迁移不得丢失或偷偷吸收未提交内容。冻结时对每个项目记录：

- 当前分支和 HEAD；
- `git status --short`；
- tracked diff 和 staged diff；
- untracked 文件清单；
- 排除构建产物后的文件 SHA-256 清单。

已有 Git 的项目必须先把有效工作提交到明确命名的分支，或经确认导出 patch 和未跟踪文件快照。用户端没有历史，其首次根仓库提交必须排除本地构建产物、环境文件和 `.superpowers` 运行状态。

### 5.4 Worktree 处理

后端三个外部 worktree 在切换前逐一检查状态：

- 有效分支全部保留到迁移历史；
- 有未提交内容时暂停迁移；
- 未经确认不删除 worktree 目录或分支；
- 根仓库切换前必须解除旧主仓库与外部 worktree 的活动依赖，或将旧仓库保留在固定归档路径并修复 worktree 引用。

禁止直接删除 `kasi-backend/.git`，因为这会破坏现有 worktree 管理数据。

## 6. 迁移与切换流程

迁移分为五个独立阶段，每阶段只解决一个问题。

### 阶段一：冻结和备份

1. 暂停三个项目的并行编辑。
2. 处理或保护全部未提交内容。
3. 为管理端和后端创建 `git bundle --all` 备份。
4. 生成三个项目的文件清单和 SHA-256 校验清单。
5. 记录所有分支、worktree、远程和 HEAD。

完成标准：任何当前代码、分支和未提交内容都有两个独立恢复来源。

### 阶段二：临时仓库导入

1. 在根目录之外创建临时迁移目录。
2. 从本地仓库的独立克隆执行路径改写，禁止在原仓库运行历史重写。
3. 合并后端和管理端已确认的 `master` 历史。
4. 保留其他有效分支为 `archive/*` 引用。
5. 加入用户端受控源码快照。

完成标准：临时仓库只有一个 `.git`，三个项目都位于目标子目录且历史可追溯。

### 阶段三：文档重组

1. 根 `AGENTS.md` 保存通用强制流程。
2. 根 `DEVELOPMENT.md` 保存变更分级、根因分析、验证和文档同步流程。
3. 根 `README.md` 保存整体项目入口、三应用边界和统一操作入口。
4. `docs/README.md` 作为当前架构、稳定契约、项目文档、ADR、计划和归档的唯一索引。
5. 已验证且仍有效的内容进入 `architecture`、`contracts`、`development` 或 `projects`。
6. 历史方案和已完成计划进入 `archive/<project>`，标注历史状态，不作为当前事实。
7. 重复或冲突文档先依据代码和测试确认当前行为，再合并；无法确认的内容进入缺口清单。

完成标准：每条当前规则只有一个真理源，所有其他文档通过链接引用，不复制易漂移的长段落。

### 阶段四：验证

迁移前后比较：

- 路径改写提交映射、提交数量、作者、时间和提交说明抽样；
- 三个项目受控文件的 SHA-256 清单；
- 根仓库忽略规则，确认构建产物和本地状态未纳管；
- 所有 Markdown 相对链接和当前/规划状态标记；
- `git diff --check`。

应用验证命令：

```powershell
cd kasi-admin-web
pnpm test
pnpm lint
pnpm typecheck
pnpm build

cd ../kasi-user-web
pnpm test
pnpm lint
pnpm typecheck
pnpm build

cd ../kasi-backend
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

完成标准：迁移证据完整，三个项目分别显示零失败、零错误；任何既有失败必须在迁移前后对比并明确归因，不能被描述为迁移成功。

### 阶段五：根目录切换与发布

1. 根仓库验证通过后再安排切换窗口。
2. 现有目录保存为只读回滚副本，不先删除嵌套 `.git`。
3. 将验证后的临时 monorepo 放到 `E:/JavaProjects/kasi-project`。
4. 再次运行状态、文件清单和关键构建验证。
5. 经明确授权后创建新的 `wwxst/kasi-project` 远程仓库并推送根 `master` 和需要保留的 archive 分支。
6. 比较本地 `HEAD`、跟踪分支和 `git ls-remote` 结果。
7. 远程一致且工作区干净后，宣布以后只从根仓库提交和推送。

现有 `wwxst/kasi-backend` 保持不变并标记为旧仓库；是否归档或只读由后续独立决策处理。

## 7. 回滚方案

在根仓库远程一致性确认前，任何阶段失败都停止并保留现场：

- 阶段一失败：不进入历史迁移，继续使用原项目。
- 阶段二或三失败：删除临时迁移目录即可，原项目不变。
- 阶段四失败：根据文件清单、提交映射或测试差异定位，修正临时仓库后重新验证。
- 阶段五本地切换失败：恢复原目录名称和原项目入口。
- 推送失败：不删除本地迁移仓库和旧仓库，确认远程状态后再决定重试。

旧仓库备份、bundle、提交映射和校验清单至少保留到根仓库完成一次正常后续开发与推送后，再单独确认清理。

## 8. 验收标准

- `E:/JavaProjects/kasi-project/.git` 是唯一活动 Git 根。
- 三个项目目录内不存在活动的嵌套 `.git`。
- 后端和管理端历史能从根仓库追溯，提交映射已保存。
- 用户端源码首次纳管完整，未包含本地环境和构建产物。
- 根文档覆盖通用规则，子项目文档只保留项目特有规则。
- 三个项目的测试、lint/typecheck/编译或构建按各自契约通过。
- 根仓库本地 `HEAD`、`origin/master` 和远程 `master` 哈希一致。
- `git status --short --branch` 干净。
- 旧仓库仍可恢复，且未被远程覆盖。

## 9. 明确风险

- 当前并行未提交工作可能在冻结前继续变化，必须在迁移窗口重新审计。
- 后端外部 worktree 会放大直接移动或删除 `.git` 的风险。
- 历史路径改写会产生新的提交哈希，必须保存 old-to-new commit map。
- 文档中已有当前契约与历史方案并存的漂移，重组不能只做机械移动。
- 新 GitHub 远程尚不存在，创建远程属于外部状态变更，实施时需要明确授权。
