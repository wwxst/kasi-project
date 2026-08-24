# kasi-backend

Spring Boot 4 + Java 25 + MyBatis + Flyway 后端，根包 `com.kasi.backend`。主要模块为 `admin`、`user`、`auth`、`security`、`provider`、`promotion`、`drama` 和 `common`。

数据库迁移位于 `src/main/resources/db/migration/`，临时开发 seed 位于 `scripts/dev/`。测试使用 H2，不依赖本机 MySQL。详细认证、Redis 会话、错误码、DTO/VO 和迁移约束以子项目现有 `AGENTS.md` 与 README 为准；根级流程见 `../DEVELOPMENT.md`。

```powershell
cd kasi-backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
```
