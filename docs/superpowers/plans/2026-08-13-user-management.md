# 推广用户管理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 实现管理员可用的推广用户 CRUD，并将推广用户登录标识统一为手机号或邮箱，移除独立 `username`。

**架构：** 保留 `user` 模块传统 Controller、DTO、VO、Entity、Mapper、Service、ServiceImpl 分层；新增 `UserManagementController`、`UserManagementService` 和 `UserManagementServiceImpl`，复用 `PromotionUserMapper` 与 Redis `SessionService`。公开认证与管理员管理共用数据表但职责分离。

**技术栈：** Java 25、Spring Boot 4.0.7、Spring Security、Jakarta Validation、MyBatis、MySQL/H2、Redis、JUnit 5、MockMvc。

**规格：** `docs/superpowers/specs/2026-08-13-user-management-design.md`

---

## 任务分解

### Task 1：移除 username 数据契约

**文件：** `V1__kasi_promotion.sql`、`test-schema.sql`、`PromotionUser.java`、`PromotionUserMapper.xml`、`UserLoginVO.java`、`CurrentUserVO.java`、用户认证结构测试。

- [ ] 写失败测试：反射确认 `PromotionUser` 没有 `username`，认证响应没有该字段。
- [ ] 运行 `mvnw.cmd '-Dtest=UserAuthControllerTest,ServiceImplementationStructureTest' test`，确认 RED。
- [ ] 删除推广用户 `username` 列、唯一索引、Entity 属性、Mapper resultMap 字段和两个认证 VO 字段；保留 `nickname`、`userNo` 和联系方式。
- [ ] 运行同一组结构/编译测试，修正由旧 Service 引用产生的编译错误但不恢复 username。
- [ ] 提交：`refactor: remove promotion user username`。

### Task 2：调整公开注册、登录和当前用户响应

**文件：** `PromotionUserMapper.java/xml`、`UserAuthService.java`、`UserAuthServiceImpl.java`、`UserAuthControllerTest.java`、`BaseAuthTest.java`、`ErrorCode.java`、用户认证 Service 测试。

- [ ] 写失败测试：手机号注册只写 mobile；邮箱注册只写 email；同时拥有两者的用户可用两种方式登录；me 不返回 username。
- [ ] 运行 `mvnw.cmd '-Dtest=UserAuthControllerTest,UserAuthServiceTest' test`，确认 RED。
- [ ] 将 `findByAccount`/`findByAccountForUpdate` 改为 `mobile OR email`；注册按 target 写入对应联系方式，不写 username；插入后只回写 `userNo` 和昵称。
- [ ] 保留手机号 trim、邮箱 trim + lowercase；登录、验证码、忘记密码统一使用规范化 target；清理 `USER_USERNAME_DUPLICATE` 新引用。
- [ ] 运行 `mvnw.cmd '-Dtest=UserAuthControllerTest,UserAuthServiceTest,UserPasswordResetServiceTest' test`。
- [ ] 提交：`refactor: authenticate promotion users by contact`。

### Task 3：定义管理 DTO、VO、Service 和权限

**新建：** `UserManagementController`、`UserManagementService`、`UserPageQueryDTO`、`CreateUserDTO`、`UpdateUserDTO`、`ResetUserPasswordDTO`、`UpdateUserStatusDTO`、`UserListItemVO`、`UserDetailVO`、`UserPageVO`、权限/DTO/结构测试。

- [ ] 写失败测试：普通管理员和超级管理员访问管理路径 200；推广用户 403；匿名 401；DTO 覆盖联系方式至少一个、昵称、密码确认、状态和分页规则。
- [ ] 运行 `mvnw.cmd '-Dtest=UserManagementPermissionTest,UserManagementDtoValidationTest,ServiceImplementationStructureTest' test`，确认 RED。
- [ ] Controller 路径固定 `/api/user/management`，所有请求体 `@Valid`；SecurityConfig 增加 `.requestMatchers("/api/user/management/**").hasRole("ADMIN")`。
- [ ] 运行权限、DTO、结构测试并提交：`feat: define promotion user management contracts`。

### Task 4：实现分页搜索和详情

**文件：** `PromotionUserMapper.java/xml`、`UserManagementService.java`、`UserManagementServiceImpl.java`、`UserManagementController.java`、`UserManagementQueryTest.java`。

- [ ] 写失败测试：默认分页、关键词匹配 userNo/mobile/email/nickname/realName、空关键词、超大页码、详情不存在 `3011`、无 password。
- [ ] 运行 `mvnw.cmd '-Dtest=UserManagementQueryTest' test`，确认 RED。
- [ ] 增加 `countByKeyword` 和 `findPage(keyword,long offset,int size)`；SQL 使用括号关键词条件、`ORDER BY id ASC` 和参数绑定。
- [ ] Service 使用 `((long) page - 1) * size`，映射 Entity 到 VO 时不返回 password。
- [ ] 运行 `mvnw.cmd '-Dtest=UserManagementQueryTest,ServiceImplementationStructureTest' test`。
- [ ] 提交：`feat: query promotion users`。

### Task 5：实现管理员新增推广用户

**文件：** `UserManagementService/Impl`、`UserManagementController`、`PromotionUserMapper.java/xml`、`ErrorCode.java`、`UserManagementMutationTest.java`。

- [ ] 写失败测试：普通/超级管理员均可新增；只填手机号、只填邮箱、同时填写；邮箱小写和联系方式 trim；固定 `status=1`、`registerSource=ADMIN`、KS userNo；BCrypt；重复联系方式、同时为空、密码不一致错误。
- [ ] 运行 `mvnw.cmd '-Dtest=UserManagementMutationTest' test`，确认 RED。
- [ ] 新增 `create` Service：规范化、至少一个联系方式、唯一性、BCrypt、固定字段、临时唯一 userNo、插入后回写 `KS%06d`；并发 `DuplicateKeyException` 转 3006/3007，密码不一致新增 3013。
- [ ] 运行创建和 DTO 定向测试并提交：`feat: create promotion users`。

### Task 6：实现资料和联系方式编辑

**文件：** `UserManagementService/Impl`、Controller、Mapper.java/xml、`UserManagementMutationTest.java`、`UserManagementServiceTest.java`。

- [ ] 写失败测试：昵称/姓名/头像/备注编辑不失效 Token；手机号/邮箱变化失效旧 Token；邮箱小写；联系方式不能同时清空；重复联系方式；Redis-first 顺序。
- [ ] 运行 `mvnw.cmd '-Dtest=UserManagementMutationTest,UserManagementServiceTest' test`，确认 RED。
- [ ] 增加 `updateProfile(PromotionUser)`；更新资料和联系方式但不更新 userNo/status/registerSource/password。
- [ ] 仅 mobile/email 实际变化时 `beginMutation(USER,id)`；写入成功后事务 `afterCommit` 完成版本；并发唯一键转 3006/3007。
- [ ] 运行编辑、Service、SessionAuthentication 定向测试并提交：`feat: edit promotion user profiles`。

### Task 7：实现状态、密码重置和物理删除

**文件：** `UserManagementService/Impl`、Controller、Mapper.java/xml、Mutation/Service/Session 测试。

- [ ] 写失败测试：禁用/启用、重置密码、旧/新密码登录、旧 Token 401、物理删除、联系方式复用、Redis 失败不写数据库。
- [ ] 运行 `mvnw.cmd '-Dtest=UserManagementMutationTest,UserManagementServiceTest,SessionAuthenticationTest' test`，确认 RED。
- [ ] 增加 `updateStatus`、`updatePassword`、`deleteById` Mapper；删除 SQL 必须是物理 `DELETE`，不写 `deleted_at`。
- [ ] 每个敏感操作锁定目标、调用 `beginMutation(USER,id)`、执行单次 MySQL 写入、提交后 `completeMutation`；Redis 失败不得写库。
- [ ] 运行定向测试并提交：`feat: manage promotion user status and deletion`。

### Task 8：失败路径、结构、文档和完整认证回归

**文件：** `ErrorCode.java`、结构测试、UserAuth/UserManagement Service 测试、README、AGENTS。

- [ ] 测试 `beginMutation` 失败时联系方式、状态、密码、删除均不调用 Mapper；数据库写失败后不调用 `completeMutation`；并发冲突不返回 500。
- [ ] 用 `rg -n "getUsername|setUsername|USER_USERNAME_DUPLICATE|username" src/main/java/com/kasi/backend/user src/test/java/com/kasi/backend/user src/main/resources/mapper/PromotionUserMapper.xml` 清理旧模型生产引用，保留规格和迁移说明。
- [ ] README 记录 `/api/user/management/**`、ROLE_ADMIN、手机号/email 登录、物理删除和 Redis 规则；AGENTS 记录当前能力与非本期规划。
- [ ] 运行定向回归：`UserManagementPermissionTest`、`UserManagementQueryTest`、`UserManagementMutationTest`、`UserManagementServiceTest`、`UserAuthControllerTest`、`UserAuthServiceTest`、`UserPasswordResetServiceTest`、`SecurityPermissionTest`、`SessionAuthenticationTest`、`ServiceImplementationStructureTest`。
- [ ] 提交：`docs: document promotion user management`。

### Task 9：Java 25 最终验收

- [ ] 确认 `git status --short --branch` 和 `git diff --stat origin/master...HEAD` 只包含本期推广用户范围。
- [ ] 设置 Java 25，运行 `mvnw.cmd test`，要求零失败、零错误、`BUILD SUCCESS`。
- [ ] 运行 `mvnw.cmd -DskipTests compile`，要求 `BUILD SUCCESS`。
- [ ] 运行 `git diff --check`。
- [ ] 检查推广用户 Entity/DTO/VO/Mapper/SQL/测试不含 username；检查 schema 的 `promotion_user` 片段不含 username；确认工作区干净。
- [ ] 提交前不自动推送或合并，等待用户明确指示。
