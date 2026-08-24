# 测试规范

三个子项目分别运行自己的测试，不共享测试数据库或前端构建缓存。后端测试使用 H2 MySQL 兼容模式和 `application-test.properties`；前端测试使用 Vitest、Testing Library 和 MSW。

提交前至少执行受影响项目的完整 test、lint、typecheck、format:check 和 build。涉及后端 API、数据库迁移、安全规则或前端 API 映射时，必须增加针对性正常/异常路径测试。

验证记录应包含命令、退出码、测试数量和已知环境限制。没有最新零错误输出时，不得宣称验证通过。
