# Kasi Monorepo Development

## 目录边界

```text
kasi-backend/    Spring Boot 4 后端、Flyway、MyBatis、后端测试
kasi-admin-web/  React + Ant Design Pro 管理端
kasi-user-web/   React + TDesign 用户端
docs/            根级架构、ADR、计划、规范和历史归档
```

三个应用不共享前端构建配置，也不把一个应用的源码移动到另一个应用。跨端契约以后端 API、对应 DTO/VO 和前端 API 类型为准。

## 日常流程

1. 在根目录检查 `git status --short --branch`，确认任务范围。
2. 阅读相关子项目 README 和 `docs/projects/*.md`。
3. 记录问题现象、根因、最小修改面和验证命令。
4. 先补或调整针对性测试，再修改实现。
5. 分别验证受影响的子项目，最后运行 `git diff --check`。
6. 只提交当前任务文件；发布前确认本地、跟踪分支和远端哈希。

## 验证命令

管理端：

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
```

用户端：

```powershell
cd kasi-user-web
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
```

后端（Java 25）：

```powershell
cd kasi-backend
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```

测试必须使用仓库配置的 H2/测试 profile，不依赖个人 MySQL。生产凭据只通过环境变量注入。

## 变更约束

- Controller 只做协议和参数校验，Service 接口与 `impl` 分离，Mapper 只负责单表持久化。
- 多表写操作在 Service 层定义事务；DTO 使用 Jakarta Validation；响应使用 VO。
- 数据库结构通过 Flyway 管理。开发阶段可重建数据库，已执行迁移不要静默覆盖。
- 新增功能必须同步 README、相关设计文档和测试；历史资料只能放在 `docs/archive/`。
