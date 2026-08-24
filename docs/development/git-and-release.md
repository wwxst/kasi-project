# Git 与发布规范

## 提交

根目录是唯一 Git 根。提交信息使用简短、可追溯的动词描述；单个提交尽量只解决一个问题。提交前检查：

```powershell
git status --short --branch
git diff --check
git diff --stat
```

不要提交 `node_modules`、`target`、`dist`、日志、环境变量文件、IDE 状态或本机密钥。

## 推送

远端为 `origin`：`https://github.com/wwxst/kasi-project.git`。推送前必须核对：

```powershell
git rev-parse HEAD
git rev-parse @{upstream}
git ls-remote origin refs/heads/master
```

远端已有历史时，普通 push 被拒绝属于保护行为；不得自动 force push。应先创建迁移分支或通过明确批准的合并方案整合历史。

## 发布

发布前分别完成三个项目的构建和测试，确认数据库迁移顺序、环境变量、备份和回滚路径，再创建发布提交或标签。旧目录和迁移备份在首次生产验证完成前保留。
