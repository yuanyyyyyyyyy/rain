# 任务 A：前台渲染闭环 + forge/rain 两项目接入（2026-11-10）

> 对应总纲：`rain-并行开发总纲-审计与任务分解-20261110.md`。用户问题 1：「把简历里的 forge 项目也加进网站的 work，我感觉我这个项目（rain 本站）也可以加进去」。
> 建议分支：`feat/task-a-projects-loop`。

## 一、审计结论（为什么现在加不进去）

1. **Works 列表的数据源是 DB 不是 site.js 手写区**：site.js:20-61 的 projects 数组被 `PROJECTS:AUTO-START/END` 锚点包裹，由 `SiteDataGenerator.java:28-42` 在发布时从 `project_detail` 表重新生成。**手工往 site.js 加 forge/rain 会在下次发布被冲掉**；且 `PublishService.java:72-86` 会删除 DB 之外多余的 `src/data/projects/*.js`。
2. **详情页数据是静态 import**：`ProjectDetailPage.vue:156-163` 硬编码 `yunshu/intelnexus/shimmer` 三个 import，新 slug 即使发布会生成 `forge.js`/`rain.js` 也不会被加载，详情页只显示"素材整理中"骨架（:131-148）。**必须改代码**。
3. **路由本身通配**（src/main.js:37-40），无需加路由；导航/首页其他区（SiteHeader/Hero/Beyond）均不消费 projects，**无需改**。
4. **P0 bug：shots 字段不一致**——管理台/后端写 `caption`（ContentPage.vue:138、ContentService.java:181），前台读 `s.fig`/`s.cap`（ProjectDetailPage.vue:44-46、123-124）。现在三个项目 shots 全是 `pending:true` 所以没爆；一旦接图，图注渲染 `undefined — undefined`。**接截图前必须先修**。
5. 卡片顺序 = MySQL 插入顺序（`ProjectDetailRepo.findAll()` 无排序），forge/rain 会排在最后（no=004/005）；`flagship` 恒为第 0 个（yunshu）。

## 二、任务清单

### P0-1 详情页改为动态收集（解锁新项目闭环）
- `ProjectDetailPage.vue:156-163` 改为：
  ```js
  const modules = import.meta.glob('../data/projects/*.js', { eager: true })
  const details = Object.fromEntries(Object.entries(modules).map(([p, m]) => [p.match(/\/(\w+)\.js$/)[1], m[Object.keys(m)[0]]]))
  ```
  注意各文件导出名是 `<slug>Detail`，用 `Object.keys(m)[0]` 取即可（也可统一改成命名导出 `default`）。
- 验收：管理台新建一个临时项目 → 发布 → `/projects/<slug>` 显示完整正文（非占位骨架）→ 删除临时项目 → 发布 → 页面恢复 404 兜底。

### P0-2 统一 shots 字段名
- 二选一（推荐前者，改动最小）：前台 `ProjectDetailPage.vue:44-46、123-124` 的 `s.fig`/`s.cap` 改读 `s.fig ?? s.src`、`s.cap ?? s.caption`（或直接统一为 `caption`）；同时改 `.js` 数据文件、`ContentService.export()` 无需动（它原样导出 DB 字段）。
- 顺手核对 `ProjectDetailPage.vue` 渲染与 `ContentService.validateAndRenumber()`（ContentService.java:162-226）六种节类型字段一一对应（设计方案 4.3 的纪律）。

### P1-1 新建 forge 项目（内容已备好，见总纲第四节）
- 路径：`admin/server/src/main/resources/content-seed/` 新增 `forge.json`、`rain.json`（结构照 `yunshu.json`：`{slug, headline, summary, meta, sections}`），并在 `ProjectDetailSeeder.java:18-34` 注册（先核实其幂等语义：按 slug 补种则加文件即可）。
- 或者走管理台/API 手工建（`POST /api/content/projects` + PUT）。**推荐 seed 方式**，环境重建也不丢。
- 发布（点「04 发布」）后卡片出现；`SiteDataGenerator` 字段映射：卡片 title←meta`项目名`、period←`卡片周期`、tags←`技术栈`（`·` 分隔）、metrics←第一个 metrics 节前 3 条。

**forge 数据草案**（由总纲第四节简历内容转换）：
- `headline`: `Forge — 面向多轮复杂任务的 Agent Harness`
- `summary`: 面向多入口 AI Agent 场景的统一 Runtime：CLI、终端 TUI、后台 Gateway、定时任务与飞书/QQ/企业微信等消息渠道共享同一执行链路，统一负责调度、上下文组装、工具执行与会话持久化，消除各入口重复实现。
- `meta`: `[["项目名","Forge — Agent Harness"],["卡片周期","2026.01 — 2026.05"],["技术栈","PYTHON · TYPESCRIPT · ASYNCIO · LITELLM · MCP · PYTEST"],["角色","独立设计开发"]]`
- `sections`: 无 arch（无 Web UI，不写 SVG 架构图也可，可选画一张 Runtime 分层图组件）、无 shots（纯 CLI/库形态，截图无对象——audit 结论）、
  - `impl` 5 条（title/problem/solution/result）：统一执行模型 / 任务调度 / 上下文与成本优化 / 工具并发 / 可观测与可复现，素材照总纲第四节逐条拆写（效果列放量化数字）。
  - `metrics`：`[{label:"真实请求",value:"4,160 次",delta:"0 丢失 · 0 重复"},{label:"端到端 P95",value:"11.66s",delta:"13.57s → 11.66s"},{label:"验收通过率",value:"99.95%"},{label:"成本",value:"-72%",delta:"72 Trials 对照实验"},{label:"Provider 缓存命中",value:"≈74%"},{label:"工具并发中位耗时",value:"113ms",delta:"374ms → 113ms"}]`（前 3 条自动进首页卡片）。

**rain 数据草案**（素材来自 README.md）：
- `headline`: `Rain — Personal Archive`
- `summary`: 个人作品集网站 + 自托管内容管理后台。Klein Blue v2 视觉系统；Vue 3 纯静态前台 + Spring Boot 管理台 + Cloudflare Worker/D1 公网收集端 + GitHub Pages 自动部署，管理台编辑内容一键发布。
- `meta`: `[["项目名","Rain — Personal Archive"],["卡片周期","2026.10 — 至今"],["技术栈","VUE 3 · VITE · SPRING BOOT · MYSQL · CLOUDFLARE WORKER · D1 · GITHUB ACTIONS"],["角色","独立设计开发"]]`
- `sections`：`impl`（设计 token 单文件驱动全站视觉 / 摄影页 justified rows + 自研 Lightbox / 发布管道：原子写回→构建→git push→API 通道回退 / Worker 与 Spring 同契约 `/api` 兼容层）、`metrics`（3 个 case study ×07 节 / 30 张真实照片 justified 排版 / 发布到公网生效 1.5–3 分钟）、`shots`（`pending:true` + wanted 清单，图由任务 G 的自动截图填充）。

### P1-2 Works 卡片顺序可控（可选）
- 给 `ProjectDetail` 加 `sortNo` 列（或按 `created_at`），`SiteDataGenerator` 排序后生成；forge 期望排在 yunshu/intelnexus 之后、shimmer 之前或之后由用户定。
- `ProjectsSection.vue:72-74` 的 Visual Notes 卡硬编码 `PERSONAL / 004`，加项目后编号漂移，顺手改为动态或不带数字。

## 三、涉及文件
- `src/pages/ProjectDetailPage.vue`（P0 两项）
- `admin/server/src/main/resources/content-seed/forge.json`、`rain.json`（新增）
- `admin/server/src/main/java/rain/admin/content/ProjectDetailSeeder.java`、（可选）`ProjectDetail.java`、`SiteDataGenerator.java`
- `src/components/home/ProjectsSection.vue`（Visual Notes 编号）
- 发布动作产生：`src/data/site.js` 锚点区、`src/data/projects/forge.js`、`rain.js`（生成物，勿手改）

## 四、验收标准
1. 公网 Works 列表出现 forge、rain 两张卡片，字段（标题/周期/技术栈/指标）与简历一致。
2. `/projects/forge`、`/projects/rain` 详情页完整渲染（forge 无 shots 节也正常）。
3. shots 录入一条带 caption 的截图（本地放一张测试图到 `public/screenshots/rain/`）发布后图注正常显示（非 undefined）。
4. 现有三个项目详情页回归无差异。

## 五、冲突与依赖
- 动 `ProjectDetailPage.vue`：与任务 F（可选预览功能）有重叠，**A 先行**。
- 发布会 commit+push：并行期间与任务 C 的发布动作错开。
- 不改 `PhotosPage.vue`/`PublishService.java`（B/C 领地）。
