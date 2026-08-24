# 默认超级管理员初始化设计

日期：2026-08-14

## 1. 背景

当前认证与管理员管理功能依赖系统中存在唯一的超级管理员，但生产用 Flyway 迁移只创建 `sys_admin_user` 表，没有初始化管理员。`kasiadmin / kasi123456` 目前仅由 `BaseAuthTest` 插入 H2 测试数据库，删除并重新创建开发数据库后无法直接登录后台。

项目尚处于开发阶段，没有需要兼容的生产 Flyway 历史，现有开发数据库可以删除重建。因此本次直接完善 `V1__kasi_promotion.sql`，不新增补丁迁移或运行时初始化器。未来生产环境首次建库时也执行同一份 `V1`，获得相同的初始超级管理员。

## 2. 目标

- Flyway 执行 `V1__kasi_promotion.sql` 后，`sys_admin_user` 中存在一个可登录的初始超级管理员。
- 初始账号固定为 `kasiadmin`，初始密码固定为 `kasi123456`。
- 数据库存储 BCrypt 哈希，不存储明文密码。
- 初始管理员状态为启用，且 `is_super_admin = 1`。
- 开发环境与未来生产环境使用相同的首次建库行为。

## 3. 非目标

- 不新增 `V2` 或其他兼容已部署数据库的增量迁移。
- 不新增 `ApplicationRunner`、`CommandLineRunner` 或其他启动时账号初始化逻辑。
- 不实现默认密码首次登录强制修改功能。
- 不修改管理员登录、权限派生、会话或密码修改流程。
- 不建设多个超级管理员、超级管理员转让或 RBAC。

## 4. 数据库初始化

在 `V1__kasi_promotion.sql` 创建完 `sys_admin_user` 和 `promotion_user` 后，直接插入一条管理员记录：

| 字段 | 初始化值 |
|------|----------|
| `username` | `kasiadmin` |
| `password` | 与明文 `kasi123456` 匹配的 BCrypt 哈希 |
| `real_name` | `系统管理员` |
| `status` | `1` |
| `is_super_admin` | `1` |

其余可空字段使用数据库默认值。BCrypt 使用随机盐，因此迁移中的固定哈希不要求与测试运行时生成的哈希字符串相同，只要求 `BCryptPasswordEncoder.matches("kasi123456", storedHash)` 返回 `true`。

`V1` 是一次性版本迁移，不使用 `INSERT IGNORE`、`ON DUPLICATE KEY UPDATE` 或运行时覆盖逻辑。开发数据库如果已经记录了 `V1` 的旧校验和，应删除并重新创建数据库后启动应用，不对旧开发数据做升级兼容。

## 5. 安全边界

- 固定初始密码属于已知凭据，README 必须明确提示部署后立即登录并修改密码。
- 数据库迁移和文档可以记录账号及初始密码契约，但 `password` 列只能保存 BCrypt 哈希。
- 现有管理员改密流程继续负责生成新 BCrypt 哈希，并通过 Redis 会话版本机制使旧 Token 失效。
- 本次不增加明文密码日志、配置项或 API 返回字段。

## 6. 测试设计

新增独立的 `DefaultSuperAdminMigrationTest`。测试使用 Flyway API 对 H2 MySQL 模式内存数据库执行生产目录中的完整迁移，再通过 JDBC 查询结果，覆盖以下行为：

1. 执行生产迁移后恰好存在一个 `username = 'kasiadmin'` 的管理员。
2. 该记录的 `real_name = '系统管理员'`、`status = 1`、`is_super_admin = 1`。
3. `password` 列不等于明文 `kasi123456`。
4. Spring Security 的 `BCryptPasswordEncoder` 能用 `kasi123456` 匹配迁移中的哈希。
5. 现有管理员登录测试继续使用相同凭据并保持通过。

已验证当前完整 `V1__kasi_promotion.sql` 可以由 H2 2.4.240 的 MySQL 模式执行，因此测试不复制建表或初始化 SQL，也不使用只检查源码字符串的替代断言。

## 7. 文档同步

- 更新 `README.md` 的项目结构、首次启动、数据库现状和测试现状，记录默认超级管理员及重建开发数据库的要求。
- 更新 `AGENTS.md` 的当前项目现状、数据库迁移和安全说明，删除“该凭据仅用于测试”的旧认知。
- 更新现有管理员管理设计文档中与本设计冲突的说明，明确初始超级管理员现由生产 Flyway `V1` 提供；历史实施计划保持不变。

## 8. 验收标准

- 删除并重建数据库后启动应用，Flyway 成功执行 `V1`。
- 使用 `kasiadmin / kasi123456` 可以通过管理员登录接口认证。
- 数据库中该账号启用且是唯一的初始化超级管理员，密码不是明文。
- 针对性测试、完整测试套件和 `git diff --check` 均通过。
- README、AGENTS 和管理员设计文档与代码行为一致。
