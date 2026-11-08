# tools

## sync-via-api.py — 经 GitHub API 同步提交（国内直连 github.com 被墙时的推送通道）

github.com 主站被墙但 api.github.com 可达时，`git push` 不可用；本脚本通过 Git Data API
把本地 commit 逐个重放到远端（blob → tree → commit → 快进 main），**完整保留提交历史**（message/作者/时间戳）。

```bash
python tools/sync-via-api.py     # 依赖 gh 已登录（token 取自 gh auth token）
```

- 幂等可中断重跑：自动匹配远端 HEAD 对应的本地 commit，从下一个继续
- push 之后 GitHub Actions 自动构建并部署到 Pages（约 1-2 分钟）
- 用户开启代理（Clash 7890）后可直接 `git push`，二者等价
