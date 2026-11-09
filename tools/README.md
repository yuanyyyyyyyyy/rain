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

## sync-via-api.py 断点匹配纪律（2026-11-10 修复）

断点续传必须「commit message + **tree sha**」双匹配：publish commit 消息固定重复，且重放会让本地/远端
hash 分叉（同内容不同 hash），只按消息匹配会把未推送的同名 publish commit 误判为已推送——曾导致
一次发布之后的所有改动（含照片/截图二进制）静默丢失。tree sha 由文件内容决定、重放后不变，是可靠锚点。

## screenshots.mjs — 项目界面自动截图（Playwright）

```bash
npm run build && npm run shots            # 起 vite preview 截全部目标
npm run shots -- --target rain            # 只截某个 slug
npm run shots -- --url http://...         # 复用已起的服务（如 IntelNexus/Shimmer 本地服务）
```

- 产出 `public/screenshots/<slug>/<slug>-NN.png|jpg`（元素级截图，1440px 宽；大区块 jpg q85 控制单张 ≤2MB）
- 接入走管理台：shots 节取消 pending → datalist 选文件 + 写图注 → 04 发布（发布 commit 自带截图目录）
- 目标配置在脚本内 TARGETS 数组；选择器须为页面稳定根区块（.hero / #work / .gallery / .detail）
- forge 无 Web UI 不设 shots；云枢截图必须人工过脱敏后再接入
