# kasi-backend

Spring Boot 4 + Java 25 + MyBatis 单模块后端，根包为 `com.kasi.backend`。Java 版本以 `pom.xml` 的 `java.version` 为机器真相；详细业务边界见子项目 `README.md` 和 scoped `AGENTS.md`。

生产 schema 的版本真相是 `src/main/resources/db/migration/V*.sql`，通过 Maven `migration` profile 独立执行。应用没有 Flyway 运行时依赖且不自动创建或升级数据库；`src/main/resources/db/kasi_promotion.sql` 只用于开发空库重建，本地开发 seed 位于 `scripts/dev/`，二者都不是生产升级入口。

业务时间是 `Asia/Shanghai`；MySQL 连接 session 使用等价 `+08:00`，H2 test profile 不执行 MySQL 专用 session SQL。

```powershell
cd kasi-backend
.\mvnw.cmd verify
```

普通 Gate 使用 H2，不依赖本机 MySQL。聚焦的 MySQL 8.4 Contract 和 GoodShort Real Smoke 命令及 SKIP/FAIL 语义见 [测试规范](../development/testing.md)。
