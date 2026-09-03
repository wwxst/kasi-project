# 阿里云手机验证码与系统配置设计

日期：2026-09-02

状态：设计已批准，尚未实施

## 1. 背景

当前后端已经具备验证码生成、Redis 限流、哈希存储、原子消费、注册校验和忘记密码校验能力，但生产环境没有真实验证码发送实现。`kasi-user-web` 当前只有密码登录接入真实 API，注册、验证码登录和忘记密码仍是占位交互。

本次接入阿里云短信 API，并允许超级管理员通过 `kasi-admin-web` 配置 AccessKey、短信签名和三个业务模板。阿里云账号、签名和模板尚未准备，因此实现阶段只能完成自动化验证和无凭据运行验证；真实短信必须等配置完成后人工验收。

## 2. 目标

- 手机号注册发送并校验阿里云短信验证码。
- 手机号验证码登录发送并校验验证码，成功后签发现有格式的 JWT 会话。
- 手机号忘记密码发送并校验验证码，继续使用现有密码重置 Token 流程。
- 在管理后台提供阿里云短信配置入口。
- 使用 AES-GCM 加密保存 AccessKey ID 和 AccessKey Secret。
- 保留现有 Redis 验证码有效期、重发间隔、每日上限和失败次数限制。
- 保持邮箱密码登录可用。

## 3. 非目标

- 本阶段不发送邮箱验证码，不开放邮箱注册、邮箱验证码登录或邮箱忘记密码。
- 不实现短信发送记录、费用统计、模板审批、签名审批或多供应商切换。
- 不实现短信配置历史、版本回滚或删除接口。
- 不增加测试短信端点；配置后通过真实注册、验证码登录或忘记密码入口验收。
- 不重做登录页品牌、扫码登录、协议页面或其他无关界面。
- 不提交真实 AccessKey、手机号、签名或模板 Code。

## 4. 方案选择

采用“完整配置存数据库”的方案。超级管理员通过管理前端维护配置，后端在发送时读取当前启用配置。密钥使用现有 AES-GCM 主密钥能力加密，API 永不返回密钥内容。

未采用以下方案：

- AccessKey 使用环境变量、前端只配置签名和模板：不能满足全部配置由系统前端完成的要求。
- 全部使用环境变量：实现简单，但不提供系统配置页面。
- 明文保存 AccessKey：不满足密钥安全要求。

## 5. 数据库设计

在唯一初始化文件 `kasi-backend/src/main/resources/db/kasi_promotion.sql` 中新增单例表。应用不增加 Flyway 或历史迁移 SQL；开发数据库按现有规则删除重建。

```text
system_sms_config                    阿里云短信当前配置
id                                  固定为 1 的单例主键
access_key_id_ciphertext            AccessKey ID 的 AES-GCM 密文
access_key_secret_ciphertext        AccessKey Secret 的 AES-GCM 密文
sign_name                           阿里云审核通过的短信签名
register_template_code              注册验证码模板 Code
login_template_code                 验证码登录模板 Code
reset_password_template_code        忘记密码模板 Code
enabled                             0 停用，1 启用
created_by                          创建管理员逻辑关联 ID
updated_by                          最后更新管理员逻辑关联 ID
created_at                          创建时间
updated_at                          更新时间
```

表不建立物理外键。初始化 SQL 不插入默认记录，不包含凭据。服务层始终以 `id = 1` 查询和保存，确保只有一条当前配置。

现有平台密钥 AES-GCM 实现提取为应用级通用凭据加密能力，平台连接行为和已有密文格式保持不变。短信配置复用同一主密钥，不增加第二套密钥配置。

## 6. 管理 API

```text
GET  /api/admin/system/sms-config    读取短信配置状态
PUT  /api/admin/system/sms-config    首次创建或覆盖当前配置
```

两个端点均要求 `ROLE_SUPER_ADMIN`。普通管理员不具备读取或写入权限。

读取响应包含以下字段：

```text
configured                          是否存在完整配置
accessKeyIdConfigured               AccessKey ID 是否已配置
accessKeySecretConfigured           AccessKey Secret 是否已配置
signName                            短信签名
registerTemplateCode                注册模板 Code
loginTemplateCode                   登录模板 Code
resetPasswordTemplateCode           忘记密码模板 Code
enabled                             是否启用
updatedAt                           最后更新时间
```

读取响应不返回 AccessKey 明文、密文或掩码片段。首次保存必须同时提交 AccessKey ID、AccessKey Secret、签名和三个模板 Code。后续更新时两个 AccessKey 字段留空表示保留现有值；签名、模板和启用状态使用请求中的当前完整值覆盖。

服务层只有在配置完整时才允许 `enabled = true`。不提供密钥读取、单独清空或配置删除 API。

## 7. 验证码发送架构

现有 `VerificationCodeSender` 增加 `VerificationScene` 参数，使发送器能够区分以下模板：

```text
REGISTER                            注册验证码
LOGIN                               验证码登录
RESET_PASSWORD                      忘记密码验证码
```

运行时发送器保持 profile 隔离：

```text
local                               ConsoleVerificationCodeSender
test                                TestVerificationCodeSender
其他 profile                        AliyunSmsVerificationCodeSender
```

生产发送器通过独立阿里云短信网关调用官方 Java SDK。网关只负责把手机号、签名、模板 Code 和 `code` 模板参数发送给阿里云；配置查询、场景选择和业务错误转换由短信发送服务负责。该边界允许自动化测试使用假网关，不调用真实短信。

发送成功必须同时满足阿里云调用没有网络或 SDK 异常，并且响应业务码为 `OK`。其他结果统一转换为验证码发送失败，不把阿里云原始错误暴露给客户端。

## 8. 用户认证 API

现有注册接口继续使用，但请求目标限制为中国大陆 11 位手机号：

```text
POST /api/user/auth/register/code           发送注册验证码
POST /api/user/auth/register                校验验证码并注册
```

新增验证码登录接口：

```text
POST /api/user/auth/login/code              发送登录验证码
POST /api/user/auth/login/code/verify       校验验证码并登录
```

验证码登录成功后复用现有密码登录的账号状态检查、最后登录信息更新、SessionService 会话创建和登录 VO，不引入第二种 Token 格式。

忘记密码继续使用现有接口，但请求目标限制为中国大陆 11 位手机号：

```text
POST /api/user/auth/password/forgot/code    发送忘记密码验证码
POST /api/user/auth/password/forgot/verify  校验并签发重置 Token
POST /api/user/auth/password/reset          使用重置 Token 设置新密码
```

未注册或不可登录的手机号请求登录验证码时，对外返回与正常请求相同的成功响应，但只占用相同 Redis 限流状态，不实际投递。忘记密码沿用同一防账号枚举行为。

密码登录的 `account` 继续支持手机号或邮箱，不改变已有契约。管理员创建推广用户的邮箱能力也不受本次变更影响。

## 9. Redis 与失败处理

保留现有参数：

```text
验证码有效期                        300 秒
同场景重发间隔                    60 秒
同目标同场景每日上限              10 次
错误尝试上限                      5 次
```

Redis key 继续包含目标和场景，因此注册、登录和忘记密码互不消费验证码。验证码仍只以 SHA-256 哈希保存，校验成功后原子消费。

阿里云发送失败时，本次验证码不可用于校验，并回收本次验证码、冷却和每日计数占用，使用户可以重新发起请求。Redis 回收失败时按认证状态不可确认处理，安全失败。

新增可达错误码 `VERIFICATION_CODE_SEND_FAILED`，位于验证码 `4xxx` 分段。配置不存在、配置停用、配置不完整、阿里云网络或 SDK 异常、阿里云非 `OK` 响应均使用该错误，不泄露供应商细节。

生产日志不得记录验证码、AccessKey 或完整手机号。允许记录脱敏手机号、业务场景、阿里云响应 Code 和 RequestId，以便定位发送失败。

## 10. 管理前端

`kasi-admin-web` 在“系统配置”下新增“短信配置”，且菜单和路由只对超级管理员开放。

页面包含：

```text
AccessKey ID                        密码型输入，已配置时保持空白
AccessKey Secret                    密码型输入，已配置时保持空白
短信签名                            普通文本输入
注册模板 Code                       普通文本输入
验证码登录模板 Code                 普通文本输入
忘记密码模板 Code                   普通文本输入
启用短信                            Switch 开关
保存                                明确的保存命令按钮
```

页面加载后只展示密钥是否已配置，不把密钥写入 DOM、状态持久化或日志。保存成功后重新读取服务器状态。首次配置缺少密钥时前端提示必填；已有配置时密钥留空表示不修改。

## 11. 用户前端

`kasi-user-web` 保持当前登录页整体布局，只接通本次认证行为。

- 密码登录继续接受手机号或邮箱。
- 验证码登录只接受手机号；发送成功后才启动 60 秒倒计时。
- 注册只接受手机号，提交手机号、验证码、密码和确认密码；成功后返回密码登录，不伪造登录状态。
- 忘记密码按“手机号和验证码校验”与“新密码和确认密码”两个阶段完成；成功后返回密码登录。
- 公共认证 API 错误继续经过共享 Axios 层显示中文消息，不重复展示原始响应。
- 本次不新增邮箱验证码切换入口。

## 12. 安全边界

- 短信配置读取和写入只允许超级管理员。
- AccessKey ID 和 AccessKey Secret 均加密保存且永不通过 API 返回。
- 浏览器不持久化任何阿里云凭据。
- 发送接口不返回验证码、模板内容、签名或阿里云 RequestId。
- 未注册账号的发送响应保持不可区分，防止账号枚举。
- Redis 或短信服务不可用时不得绕过验证码校验或降级放行。
- 只有 HTTP 401 可以清理已有前端会话；短信服务失败不得清理会话。

## 13. 测试与验收

后端自动化验证覆盖：

- 初始化 SQL 能创建单例短信配置表且不植入凭据。
- 配置首次保存、覆盖、保留已有密钥、启停和完整性校验。
- AccessKey 密文落库，管理响应不包含明文、密文或掩码片段。
- 普通管理员读取和写入均被拒绝，超级管理员可以操作。
- 三个场景选择各自模板 Code。
- 阿里云 `OK`、非 `OK` 和异常路径。
- 发送失败时 Redis 预留状态回收。
- 注册、验证码登录、忘记密码的成功和异常路径。
- 未注册手机号不实际发送登录和忘记密码短信。

前端自动化验证覆盖：

- 超级管理员短信配置菜单、配置读取和保存。
- 普通管理员不显示短信配置入口。
- 密钥不回显，留空更新保留配置。
- 注册、验证码登录、忘记密码三条手机号流程。
- 发送失败不启动倒计时，成功后启动倒计时。
- 密码登录仍支持邮箱账号文本。

实现完成后运行：

```powershell
cd kasi-backend
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify

cd ..\kasi-admin-web
pnpm check

cd ..\kasi-user-web
pnpm check

cd ..
git diff --check
```

涉及登录页和短信配置页布局时，还要在桌面和 320px 以上移动视口完成浏览器截图检查。

由于当前没有真实 AccessKey、签名和模板，自动化测试不证明真实阿里云短信已经送达。实现交付必须明确记录“真实短信人工验收待配置”，不能将其描述为 PASS。配置完成后的人工验收依次验证注册、验证码登录和忘记密码三个模板。

## 14. 文档同步

实现完成并通过验证后，再把以下内容更新为当前事实：

- 根级项目文档中的三应用跨项目契约。
- `kasi-backend/README.md` 和 `kasi-backend/AGENTS.md` 的短信发送、API、配置、schema 和验证说明。
- `kasi-admin-web/README.md` 的短信配置页面、权限和验证说明。
- `kasi-user-web/README.md` 与 `kasi-user-web/AGENTS.md` 的手机号认证行为和验证说明。

在实现和自动化验证完成前，这些行为只属于已批准设计，不得写入当前架构文档作为已实现事实。实现完成后可以记录已验证的接口、配置和前端行为，但在真实短信人工验收通过前，必须继续明确标注“真实阿里云短信送达待验收”。
