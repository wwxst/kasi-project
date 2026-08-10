# Kasi Backend 开发文档

最后核对时间：2026-08-10

## 1. 项目定位

这是卡司推广平台的后端仓库，基于 Spring Boot 4.0.7 + MyBatis 4.0.1 + MySQL 8 + JWT 构建。

**第一阶段（已完成）**：实现了管理员（ADMIN）和推广用户（USER）双认证体系，包括登录、注册、密码管理、验证码、密码重置等完整功能。详见 [§6 API、认证与业务边界](#6-api认证与业务边界)。

## 2. 当前结构

```text
src/
  main/
    java/com/kasi/backend/
      KasiBackendApplication.java          # 启动入口
      admin/                                # 管理员认证模块
        controller/AdminAuthController.java # /api/admin/auth/* 控制器
        service/AdminAuthService.java       # 管理员认证业务逻辑
        entity/SysAdminUser.java            # 管理员实体
        mapper/SysAdminUserMapper.java      # 管理员 MyBatis Mapper
        dto/                                # 管理员 DTO（登录请求/响应、修改密码等）
      user/                                 # 推广用户认证模块
        controller/UserAuthController.java  # /api/user/auth/* 控制器
        service/UserAuthService.java        # 用户认证业务逻辑
        entity/PromotionUser.java           # 用户实体
        mapper/PromotionUserMapper.java     # 用户 MyBatis Mapper
        dto/                                # 用户 DTO（注册/登录/重置密码等）
      auth/                                 # 可复用的认证基础设施
        verification/                       # 验证码模块（发送、校验、哈希存储）
        password/                           # 密码重置 Token 模块
      security/                             # 安全基础
        config/SecurityConfig.java          # Spring Security 配置
        context/AuthContext.java            # 认证上下文
        context/AuthContextHolder.java      # 请求级上下文持有者（ThreadLocal）
        token/TokenService.java             # JWT 生成与解析
        token/JwtAuthenticationFilter.java  # JWT 认证过滤器
      common/                               # 公共组件
        response/ApiResponse.java           # 统一响应体
        exception/ErrorCode.java            # 错误码枚举
        exception/BusinessException.java    # 业务异常
        exception/GlobalExceptionHandler.java # 全局异常处理器
        enums/                              # SubjectType、VerificationScene、TargetType、UserStatus
    resources/
      application.properties                # 数据源、Flyway、MyBatis、JWT、验证码配置
      db/migration/
        V1__kasi_promotion.sql              # 数据库迁移脚本（2张表）
      mapper/                               # 2个 MyBatis XML 映射文件
  test/
    java/com/kasi/backend/
      BaseAuthTest.java                     # 测试基类（H2 + 数据初始化）
      admin/controller/AdminAuthControllerTest.java
      user/controller/UserAuthControllerTest.java
      security/SecurityPermissionTest.java
    resources/
      application-test.properties           # 测试环境配置（H2）
      test-schema.sql                       # 测试表结构
```

## 3. 构建工具链

- Maven 坐标：`com.kasi:kasi-backend:0.0.1-SNAPSHOT`。
- Spring Boot：`4.0.7`。
- Java：`25`，由 [pom.xml](pom.xml) 的 `java.version` 定义。
- Maven Wrapper：`3.9.16`，入口是 `mvnw.cmd`。
- 默认打包类型：JAR。

PowerShell 中先确认 Java 版本：

```powershell
java -version
.\mvnw.cmd -v
```

如果终端仍使用 Java 21，需要临时切换到 Java 25：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 4. 配置与启动

[application.properties](src/main/resources/application.properties) 已配置了以下内容：

| 配置项 | 说明 |
|--------|------|
| 数据源 | MySQL，通过环境变量 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 注入，默认连接 `localhost:3306/kasi_promotion`，**字符编码统一 UTF-8** |
| Flyway | 启用，迁移脚本路径 `classpath:db/migration`，`baseline-on-migrate=true` |
| MyBatis | Mapper XML 路径 `classpath:mapper/*.xml`，开启驼峰自动映射 |
| JWT | 密钥通过 `JWT_SECRET` 环境变量注入，过期时间 7200 秒 |
| 验证码 | 过期 300 秒，重发间隔 60 秒，每日上限 10 次 |
| 密码重置 Token | 过期 600 秒 |

应用要连接 MySQL，至少需要提供：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/kasi_promotion'
$env:SPRING_DATASOURCE_USERNAME = '<database-user>'
$env:SPRING_DATASOURCE_PASSWORD = '<database-password>'
```

之后启动：

```powershell
.\mvnw.cmd spring-boot:run
```

首次启动时 Flyway 会扫描 `db/migration/` 下的 `V1__kasi_promotion.sql` 创建所需表。

测试环境使用 H2 内存数据库（MySQL 兼容模式），通过 `@ActiveProfiles("test")` 激活 [application-test.properties](src/test/resources/application-test.properties)，无需本地 MySQL。

## 5. 数据库现状

### 已实现的表结构（V1__kasi_promotion.sql）

迁移脚本 `V1__kasi_promotion.sql` 定义 2 张持久表，验证码和密码重置 Token 等临时数据由 Redis 管理：

| 存储 | 表/Key | 说明 | 核心字段 |
|------|--------|------|----------|
| MySQL | `sys_admin_user` | 后台管理员用户 | username, password(BCrypt), nickname, mobile, email, status, is_super_admin |
| MySQL | `promotion_user` | 推广用户 | user_no(基于自增id生成), username, password(BCrypt), mobile, email, status, register_source |
| Redis | `vc:*` | 验证码（临时） | 5分钟过期，60秒重发间隔，每日上限10次 |
| Redis | `pwd:*` | 密码重置 Token（临时） | 10分钟过期，一次性消费后删除 |

> **说明**：`sys_sequence` 表已移除，`user_no` 改为基于 `promotion_user` 自增主键生成。`auth_verification_code` 和 `auth_password_reset_token` 表已移除，改用 Redis 存储（更高效、自动过期）。

## 6. API、认证与业务边界

### 6.1 认证架构

系统采用 **JWT 无状态认证**，通过 `subjectType` 字段区分两种身份，实现严格的权限隔离：

| 角色 | 路由前缀 | 权限 | 说明 |
|------|----------|------|------|
| ADMIN | `/api/admin/**` | `ROLE_ADMIN` | 管理员，仅可访问 admin 接口 |
| USER | `/api/user/**` | `ROLE_USER` | 推广用户，仅可访问 user 接口 |

- ADMIN Token **不可**访问 USER 接口（返回 403）
- USER Token **不可**访问 ADMIN 接口（返回 403）
- 无 Token 访问受保护接口返回 401

### 6.2 管理员认证 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/admin/auth/login` | 管理员登录（账号/手机号/邮箱 + 密码） | 否 |
| GET | `/api/admin/auth/me` | 获取当前管理员信息 | ADMIN |
| POST | `/api/admin/auth/logout` | 退出登录 | ADMIN |
| PUT | `/api/admin/auth/password` | 修改密码（需旧密码） | ADMIN |

### 6.3 推广用户认证 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/user/auth/register` | 用户注册（账号 + 验证码 + 密码） | 否 |
| POST | `/api/user/auth/login` | 用户登录（账号/手机号/邮箱 + 密码） | 否 |
| GET | `/api/user/auth/me` | 获取当前用户信息 | USER |
| POST | `/api/user/auth/logout` | 退出登录 | USER |
| PUT | `/api/user/auth/password` | 修改密码（需旧密码） | USER |
| POST | `/api/user/auth/password/forgot/code` | 发送忘记密码验证码 | 否 |
| POST | `/api/user/auth/password/forgot/verify` | 校验验证码，返回重置 Token | 否 |
| PUT | `/api/user/auth/password/forgot/reset` | 使用重置 Token 修改密码 | 否 |

### 6.4 统一响应格式

所有接口返回统一结构 `ApiResponse<T>`：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

- `code=0`：成功
- `code!=0`：失败（错误码定义见 [ErrorCode.java](src/main/java/com/kasi/backend/common/exception/ErrorCode.java)）

### 6.5 技术实现要点

- **密码存储**：BCrypt 哈希，使用 Spring Security `PasswordEncoder`
- **JWT**：HMAC-SHA256 签名，载荷包含 `userId`、`subjectType`、`username`，过期时间 7200 秒
- **验证码**：SHA-256 哈希后存入 Redis（内存不存明文），TTL 5 分钟自动过期，60 秒重发间隔，每日上限 10 次
- **密码重置**：验证码校验通过后颁发一次性重置 Token（SHA-256 哈希后存入 Redis），TTL 10 分钟，消费时原子删除防重放
- **用户编号**：基于 `promotion_user` 自增主键格式化生成（如 KS000001），插入后回写

## 7. 测试现状

### 测试基础设施

- 测试基类：[BaseAuthTest.java](src/test/java/com/kasi/backend/BaseAuthTest.java)，每个测试方法前自动清理数据并插入基础测试数据
- 测试数据库：H2 内存数据库（MySQL 兼容模式），通过 `@ActiveProfiles("test")` 激活
- 测试表结构：[test-schema.sql](src/test/resources/test-schema.sql)，与生产表结构一致（H2 兼容语法）

### 现有测试类

| 测试类 | 说明 |
|--------|------|
| `AdminAuthControllerTest` | 管理员登录、获取信息、退出、修改密码（9 个用例） |
| `UserAuthControllerTest` | 用户注册、登录、获取信息、退出、修改密码、忘记密码流程（13 个用例） |
| `SecurityPermissionTest` | 角色隔离：ADMIN/USER Token 不可互访、无 Token 返回 401（5 个用例） |
| `KasiBackendApplicationTests` | Spring 上下文加载测试 |

### 运行测试

```powershell
# 编译检查（跳过测试）
.\mvnw.cmd -DskipTests compile

# 运行全部测试
.\mvnw.cmd test
```

Java 21 下编译会因 `release 25` 失败，必须使用 Java 25。

## 8. 开发优先级

### ✅ P0：让基础设施可重复运行

1. ✅ 确认 Java 25 和 Maven Wrapper 的统一使用方式。
2. ✅ 已配置 datasource（环境变量注入）、Flyway 迁移、MyBatis 映射。
3. ✅ 数据库脚本已改为 Flyway 可识别的 `V1__kasi_promotion.sql`。
4. ✅ 测试环境使用 H2 内存数据库，不依赖本地 MySQL。

### ✅ P0：实现最小后端闭环

1. ✅ 定义了用户实体、Mapper、Service 和 Controller。
2. ✅ 定义了统一响应、参数校验、异常处理和事务边界。
3. ✅ 为管理员和推广用户建立了独立的认证入口与权限边界。

### P1：补齐安全和数据约束

1. ✅ 使用 BCrypt 密码哈希和可测试的认证流程。
2. ✅ 定义了角色权限（ROLE_ADMIN / ROLE_USER）、状态规则和 401/403 契约。
3. ⬜ 明确软删除后的唯一字段复用与恢复策略。
4. ⬜ 补齐 `department_id`、`created_by`、`updated_by` 的外键或明确不加外键的理由。
5. ⬜ 补充审计字段的自动填充（`created_by`、`updated_by`）。

### P2：后续规划

1. ⬜ 实现用户管理 CRUD（管理员对推广用户的增删改查）。
2. ⬜ 接入真实短信/邮件验证码发送（当前为 Console 输出）。
3. ⬜ 实现 Token 刷新机制。
4. ⬜ 添加操作日志和登录审计。

## 9. Git 与协作

- **仓库地址**：`https://github.com/wwxst/kasi-backend.git`
- **当前分支**：`master`，已关联远程 `origin/master`
- 提交规范：使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式（`feat:`、`fix:`、`docs:`、`refactor:`、`test:` 等）。
- 任何代理开始工作前都应先查看 `git status --short --branch`，只修改任务涉及的文件，不使用 `git reset --hard` 或 `git checkout --` 丢弃现有改动。
- 提交前运行 `git diff --check` 检查空白字符问题。
