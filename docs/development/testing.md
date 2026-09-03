# 测试、CI 与 Real Verification

## 本地核心 Gate

后端：

```powershell
cd kasi-backend
.\mvnw.cmd verify
```

管理端：

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm check
```

用户端：

```powershell
cd kasi-user-web
pnpm install --frozen-lockfile
pnpm check
```

`pnpm check` 按顺序执行 lint、format:check、test 和 build；build 已运行 `tsc -b`，不重复增加 typecheck。管理端 Vitest 当前使用 `maxWorkers=2`，这是重复验证后的稳定测试配置，不是永久架构规则。用户端 `pnpm-lock.yaml` 是生成文件且保持当前未提交内容，不进入 Prettier 扫描。

Java 版本由 `kasi-backend/pom.xml` 的 `java.version` 拥有；Node/pnpm 版本由各前端 `package.json` 的 `engines` 和 `packageManager` 拥有。CI 读取这些字段，不维护第二份版本号。

## 后端测试边界

普通 `mvn verify` 保持现有完整 Gate。分类执行使用 JUnit tag 和 Maven profile：

```powershell
cd kasi-backend

# 不启动 Spring/H2/Redis，也不访问真实外部环境
.\mvnw.cmd -Punit-tests verify

# Spring、H2、初始化 SQL 和嵌入式 Redis
.\mvnw.cmd -Pintegration-tests verify

# 真实 MySQL；未配置环境时必须明确 SKIP
.\mvnw.cmd -Pmysql-contract-tests -Dtest='*MySqlContractIT' test

# 真实 GoodShort；四项环境变量必须完整
.\mvnw.cmd -Preal-smoke-tests -Dtest=GoodShortFreeContentIntegrationTest test

# 高置信度静态分析
.\mvnw.cmd -Pstatic-analysis -DskipTests verify
```

`unit-tests` 排除 `integration`、`mysql-contract` 和 `real-smoke`；`integration-tests` 只包含 `integration` 并继续排除两个真实环境类别。`mysql-contract-tests` 和 `real-smoke-tests` 只选择各自专用 tag。结构测试保护这些分类，避免真实环境测试被普通 Gate 误执行。

Unit 和 Integration 的 `verify` 分别生成 `target/site/jacoco-unit` 与 `target/site/jacoco-integration` HTML/XML 报告。JaCoCo 当前只提供可见性，不设置覆盖率硬阈值；是否增加阈值属于后续基于报告的决策，不是当前 Gate。

SpotBugs 使用 `Max` effort、`High` threshold，发现高置信度问题或分析错误时阻断。它位于独立 `static-analysis` profile，不重复加入默认 `mvn verify`。

## GitHub CI

push 和 pull request 分别显示以下 Job：

```text
Backend Unit            Java 25 -> unit-tests verify -> JaCoCo artifact
Backend Integration     Java 25 -> integration-tests verify -> JaCoCo artifact
Backend Static Analysis Java 25 -> SpotBugs High Gate
MySQL Contract          Java 25 + MySQL 8.4 -> Flyway migrate/validate + all *MySqlContractIT
Admin                   Node/pnpm -> frozen install -> pnpm check
User                    Node/pnpm -> frozen install -> pnpm check
Dependency Review       pull_request only -> block high/critical additions
```

Backend 分类 Job 和 MySQL Contract 不重复完整套件。Dependency Review 不下载独立漏洞库，也不需要 NVD key。MySQL service 提供 `kasi_contract` 与 `kasi_migration` 两个空 schema：前者由 Spring `ResourceDatabasePopulator` 原样执行开发重建脚本 `db/kasi_promotion.sql`，后者由 Maven `migration` profile 执行完整 Flyway 链并校验 checksum。Contract 分别从 `MYSQL_CONTRACT_*` 和 `MYSQL_MIGRATION_*` 环境变量取得连接。

MySQL Contract 只检查：

- `INFORMATION_SCHEMA` 中物理外键数量为 0；
- 当前关键 UNIQUE INDEX；
- 金额/费率 DECIMAL precision、scale 和 round-trip；
- 订单 `READ_COMMITTED` upsert 在 `(connection_id, external_order_id)` 并发冲突后的回读和最终唯一行；
- 生产 Mapper 的 `FOR UPDATE` 阻塞与释放后提交；
- Spring 代理 `REQUIRES_NEW` 与外层事务回滚隔离；
- 两个事务竞争同一到期任务时的租约唯一领取；
- `SystemScheduledTaskMapper` 到期和完成 SQL；
- Java `Asia/Shanghai`、MySQL session `+08:00`、`next_run_at` 和 due 判断一致。
- 开发重建与完整 Flyway 链生成相同表、列、索引、约束和固定初始数据；只排除 `flyway_schema_history`、动态时间和生成 ID。

本地聚焦命令：

```powershell
cd kasi-backend
.\mvnw.cmd -Pmysql-contract-tests -Dtest='*MySqlContractIT' test
```

未设置 `MYSQL_CONTRACT_URL` 时现有 MySQL Contract 由 JUnit 明确 SKIP；未设置 `MYSQL_MIGRATION_URL` 时迁移一致性 Contract 明确 SKIP。专用承诺环境设置了对应 URL 但缺用户名/密码时必须 FAIL；Flyway 执行、schema 初始化、结构一致性、SQL、并发、事务、精度或时间断言失败也必须 FAIL。

## GoodShort Real Smoke

普通本地运行：

```powershell
cd kasi-backend
.\scripts\dev\smoke-goodshort-free-content.ps1
```

缺少 `GOODSHORT_BASE_URL`、`GOODSHORT_PARTNER_ID`、`GOODSHORT_API_KEY` 或 `DRAMA_EXTERNAL_ID` 时输出缺失名称、明确 SKIP 并退出 0。

承诺执行：

```powershell
.\scripts\dev\smoke-goodshort-free-content.ps1 -Required
```

Required 缺配置必须 FAIL；配置完整后的签名、请求、响应或断言失败也必须 FAIL，不得降级为 SKIP。该 smoke 不需要 FFmpeg。普通 CI 不访问 GoodShort、不读取私人 Secret；`.github/workflows/real-smoke.yml` 只允许手动 `workflow_dispatch`，从 GitHub Secrets 注入四项配置并以 `-Required` 执行，不设定时任务。

## 结果记录

- `PASS`：命令在所声明环境完整执行并以 0 退出，断言/测试零失败、零错误。
- `FAIL`：命令、构建、请求或断言非零失败。
- `SKIP`：明确检测到允许缺失的真实环境，输出缺失项并按约定跳过。

没有最新输出时不得沿用历史 PASS。环境缺失的 MySQL/GoodShort 只能写 SKIP；warning 与 error 分开记录，不为消除无关历史 warning 扩大治理范围。
