# 截图交付 · IntelNexus

> 来自 IntelNexus 项目的 agent，回应 `截图需求-IntelNexus-20261109.md`。
> 6/6 张已交付并直接接线进 `src/data/projects/intelnexus.js`（`pending` 已替换为 items 数组，模块加载验证通过）。
> 网站侧可直接 `npm run dev` 打开 `/projects/intelnexus` 验收；图注为 IntelNexus 侧代写的事实性版本，网站侧可随意改写。

## 1. 交付清单

文件在 `public/screenshots/intelnexus/`，命名按需求建议 `intelnexus-01.png` ~ `intelnexus-06.png`。

| 文件 | 对应需求 | 实际画面 | 尺寸/大小 |
|---|---|---|---|
| intelnexus-01.png | I1 多源采集/源管理 | 今日概览 + 侧边栏「数据源状态」健康面板展开：12 类源卡片、10 正常 · 2 待复核、OTX/TechCommunity 失败原因明细 | 1600×1000 / 239KB |
| intelnexus-02.png | I2 M-SCORE 评分详情 | 「来源可信度评估」面板：平均可信度 0.47 等 4 张指标卡 + 情报可信度雷达（五维）+ 逐源评分表（来源/可信度/等级/原因） | 1600×1000 / 363KB |
| intelnexus-03.png | I3 CIDAR 冲突检测 | 「跨源信息冲突」面板：temporal 冲突逐条展开，严重度 0.90，双方原始表述并排 | 1600×1000 / 333KB |
| intelnexus-04.png | I4 证据链/快照追溯 | 「证据链追踪」面板：证据覆盖率 80% + 证据明细（claim + 置信度 0.40–0.81 + 查看原文链接） | 1600×1000 / 349KB |
| intelnexus-05.png | I5 知识图谱 | pyvis 交互画布：81 节点 / 311 边，实体带标签（OpenAI/GLM/Z.ai/AI编程IDE 等），已缩放到主聚类取景 | 1600×1000 / 506KB |
| intelnexus-06.png | I6 定时简报/导出报告 | 简报中心 → 历史记录 → 简报预览：《AI 与网络安全每日情报简报》头部（概览/来源可信度概览/今日核心摘要分级） | 1600×1000 / 340KB |

全部满足：宽 ≥1440px ✓、侧边栏展开 ✓、单张 ≤2MB ✓。

## 2. 网站侧接线（已完成）

`src/data/projects/intelnexus.js` 的 shots 节改动：

- `"pending": true` → items 数组（6 项，`src` 路径按 rain.js 同款约定 `screenshots/intelnexus/*.png`）
- 每项带事实性 caption（如「跨源信息冲突检测 — temporal 冲突标注严重度 0.90，逐条列出双方原始表述」）
- 新增 `note` 一行：「截图为真实界面实录（历史检索快照回放），未做样式美化；数据为公开网络信息演示。」——组件支持 `sec.note` 渲染在网格下方；不想显示可整行删掉
- `ProjectDetailPage.vue` 的 shots 分支确认兼容（items 渲染 + Lightbox 正常路径），ES module 加载已验证

caption 与 wanted 里原来的措辞有两处刻意不同（见 §4 审计），网站侧定稿图注时建议先读那一节。

## 3. 制作方式与数据真实性

- **没有现场跑搜索**：采集时 Ollama 未启动。全部画面走的是应用官方的「历史回放 = 搜索当时页面」机制——`ui.py` 的结果渲染对实时结果与 `data/snapshots/<entry_id>/snapshot.json` 回放共用同一套代码（`results_view.render_full_results`），所以截图就是真实搜索当时的页面。
- **两个底层数据源（都是真实历史检索留存）**：
  - 华盛顿酒店快照 `20261021090829103069`（2026-10-21 09:08，28 条结果，15 条 temporal 冲突，证据覆盖率 80%）→ I2/I3/I4
  - zcode 快照 `20261021111414750270`（2026-10-21 11:14，59 条结果，快照记录 30 关键实体/328 关系，图谱画布实际渲染 81 节点/311 边——图注按渲染口径写）→ I5
- **唯一的非数据操作**：I5 的 kg.html 依赖 CDN 加载 vis-network，首帧画布空白（库已加载但未 fit），在 iframe 内补了一次 `network.fit()` 并缩放到主聚类。纯视图操作，未改任何数据。
- I1 里「2 项需关注」（OTX 无 key、TechCommunity 超时）是当天真实健康状态，按需求「真实感比美化重要」保留。

## 4. 审计发现 —— 图注定稿前建议先看

三处「需求文案 ↔ 界面实际」的口径差，不影响交付，但影响诚实口径：

1. **「15 类源」vs UI 实际 12 类**。首页源卡片和健康面板都是 12 类（Web/News/DarkWeb/SecRSS/TechCommunity/Qianxin/NVD/CISA_KEV/AlienVault_OTX/HackerNews/ExploitDB/Xiaohongshu）。15+ 的口径只有按底层引擎细数才成立（Web 拆 Baidu/Bing/DDG/Yahoo/Yandex、暗网 Ahmia、另有 arXiv/HuggingFace 等检索渠道）。**建议**：详情页 summary 与 I1 图注统一改为「12 类信息源 · 15+ 检索渠道」，或只说 12 类；summary 行（"上接 15 类信息源的多源采集"）也含同样表述，需要一并核对。
2. **M-SCORE「四因子分项可见」打了折扣**。四因子加权（域名权威度 30% + 时效性 25% + 内容深度 20% + 跨源一致性 25%）在引擎 `analysis/credibility.py` 与快照 JSON 里，但 UI 不逐条渲染四因子数值——界面呈现的是综合分 + 等级 + 原因文案（如「高权威域名, 内容丰富」「内容单薄」）+ 五维雷达（来源可靠性/信息可信度/跨源一致性/高质量来源占比/证据覆盖）。02 图注按此写成了「四指标卡 + 五维雷达」。impl 节的算法描述不受影响。若想强调四因子，需要 IntelNexus 侧给结果条目加一个分项明细 UI 再补拍。
3. **CIDAR 三类冲突只有时间线有实例**。引擎支持数值/立场/时间线三类，但现有演示快照的 19 条冲突全部是 temporal（数值/立场无历史实例可截）。03 图注只如实提 temporal。若想三类齐，需 LLM 可用时用 CVE/CVSS 类查询跑一次新检索再补拍。

## 5. 隐私与脱敏核查（按需求 §隐私逐条）

- **API Key / token**：全 6 张无真实密钥。唯一 key 字样是 I1 健康面板的提示文案「注册后写入 .env OTX_API_KEY=<key>」——`<key>` 是占位符，非真实值。源管理设置里的密钥输入框均为密码框且不回填明文（未入镜）。
- **账号信息**：订阅者邮箱/SMTP 配置面板全部未入镜；简报预览只有「AI情报团队」机构署名。
- **日志/路径**：未截取终端；页面内无本机绝对路径。I5 图谱中的实体「SHOME/zcode-dev-home」来自公开网页抓取内容（HackerNews 语境），不是本机路径。
- **内容性质**：演示数据为公开网络信息（酒店页面、AI 资讯、漏洞库条目），与需求评估一致。

## 6. 复现/补拍指引（给任何后来的 agent）

```bat
cd /d D:\Improve\Project\Python\IntelNexus
.venv\Scripts\python.exe main.py ui --no-browser
:: 健康检查 http://localhost:8501/_stcore/health 通过后：
:: 浏览器开 http://localhost:8501，视口 ≥1440px（交付版用 1600×1000）
```

- I2/I3/I4：情报搜索 Tab → 打开「显示历史记录」→ 查看 2026-10-21 09:08「华盛顿酒店」条目 → 滚动到「来源可信度评估 / 跨源信息冲突 / 证据链追踪」三个分区
- I5：关闭预览 → 查看 2026-10-21 11:14「zcode」→ 展开「展开知识图谱（600px 画布）」；**坑**：kg.html 从 cdnjs 拉 vis-network，离线/CDN 不通时画布空白——库加载完但没画，需在 iframe 内触发 `network.fit()`（pyvis 的 network 是 iframe 全局变量）
- I6：简报中心 Tab → 「显示历史记录」→ 查看任一简报
- I1：今日概览 + 侧边栏「数据源状态」expander；想要全绿的源面板，先配 OTX key 并跑一次「联网检测」再拍
- 拍完停服务（Ctrl+C），避免常驻

## 7. 备份

原始底片（含未剪裁帧）在 `D:\Improve\Project\Python\IntelNexus\temp\screenshots_intelnexus\*raw.png`，与交付文件一一对应。采集会话结束后 Streamlit 服务已停止，8501 端口已释放。
