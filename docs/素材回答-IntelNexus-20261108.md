# IntelNexus 素材回答（对应需求清单 P0/P1）
> 日期：2026-11-08
> 回答者：IntelNexus 项目 agent，基于代码库逐条审计。
> 出处标注格式：`文件:行号`（相对 D:/Improve/Project/Python/IntelNexus/）。
> 写不出的内容已明确标注"不确定"，未做推测。

---

## 1. 一句话定位 + 完整功能管线

**一句话定位**（出处 README.md 开头）：**AI 驱动的多源网络情报分析平台——以可信度评估、证据链追溯与冲突检测为分析内核，上接多源采集、下接增量简报，让每一条情报结论都可评分、可溯源、可核验。**

**完整管线**（模块划分依据 `intelnexus/` 包结构 + README「核心特性」表）：

| 环节 | 输入 → 输出 | 说明 | 出处 |
|---|---|---|---|
| ① 多源采集 | 查询词 → 归一化结果列表 | 15 个内置源（网页 Bing/DDG/Yahoo/Yandex/百度、新闻、威胁情报 NVD/CISA KEV/CNVD/OTX/ExploitDB、HackerNews、arXiv、技术社区、HuggingFace、奇安信、小红书 UGC、暗网）+ 用户自定义源（RSS/.onion），线程池并发、跨源按归一化 link 去重 | `intelnexus/core/search/registry.py:23-38`（源清单）、`registry.py:8-12`（去重收口） |
| ② 可信度评分 M-SCORE | 结果列表 → 每条 0-1 分 | 四因子加权：域名权威度 30% + 时效性 25% + 内容深度 20% + 跨源语义一致性 25%；按分数划分主证源/佐证源/孤证源 | `intelnexus/analysis/credibility.py:23-28`、`credibility.py:212-213` |
| ③ 冲突识别 CIDAR | 多源文本 → 冲突清单 | 数值 / 时间 / 立场三类冲突，每条带 severity 0-1；如实呈现分歧而非强行统一 | `credibility.py:506-557` |
| ④ 证据链追溯 STREAM | LLM 报告 + 原文 → claim-证据映射 | 报告逐句拆 claim，与原文段落语义对齐，输出 coverage（有强证据的 claim 占比）；内联角标 `<sup>[n]</sup>` 注入句末，仅 confidence ≥ 0.5 且有证据支撑的 claim 才注入 | `intelnexus/analysis/evidence_tracer.py:18-40`、`intelnexus/analysis/evidence_annotator.py:5-17` |
| ⑤ 知识图谱 | 抓取文本 → 交互图谱 | spaCy 中英文实体抽取（含噪声实体黑名单过滤）→ NetworkX 共现建图，10 类语义关系（developed_by/released_on/competes_with 等），中心度 + 社区发现，pyvis 可视化 HTML | `intelnexus/analysis/intelligence_graph.py:8-9,33-45`、`intelligence_graph.py:650`（社区发现） |
| ⑥ RAG 检索 | 查询 → 历史收藏注入 | 知识库条目经 sentence-transformers 编码，余弦相似度 ≥ 0.45、Top-5 检索，格式化为 LLM prompt 上下文注入搜索管线与简报分析；知识库无变化时按指纹跳过重编码 | `intelnexus/knowledge/retrieval.py:63-67`（参数）、`retrieval.py:38-40`（指纹缓存） |
| ⑦ 结构化报告 | 全部上下文 → 可导出报告 | LLM 流式生成，事实/分析/推测三级拆分 + 整体置信度；一键导出 Markdown/PDF/Word/Excel | README「结构化摘要」行、`requirements.txt:40-43`（reportlab/python-docx/openpyxl） |

---

## 2. 技术栈事实核对

| 清单说法 | 核对结果 | 出处 |
|---|---|---|
| Python | ✅ Python 3.10+（源码版） | README 安装节 |
| LangChain | ✅ `langchain>=0.1.0` + `langchain-openai`（OpenAI 兼容 API 覆盖 DeepSeek/通义/智谱/Moonshot）+ `langchain-ollama`（本地模型） | `requirements.txt:34-38` |
| Streamlit | ✅ `streamlit>=1.48.0`，入口 `ui.py`，Web 界面 `http://localhost:8501` | `requirements.txt:28-29`、README 使用节 |
| NetworkX | ✅ `networkx>=3.1` + `pyvis>=0.3.1` 可视化 | `requirements.txt:45-47` |
| 向量模型 | ⚠️ **需更正口径**：主模型是 `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`（多语言，ModelScope 源下载）；另有 `all-MiniLM-L6-v2` 仅用于本地模型目录分支。清单若只写 "all-MiniLM-L6-v2" 不准确 | `intelnexus/analysis/__init__.py:35`（主模型）、`__init__.py:143-152`（本地分支） |
| 余弦相似度阈值 0.45 | ✅ 知识库 RAG 检索 `min_similarity=0.45`。⚠️ 注意：`credibility.py:269` 也有一个 0.45，但那是"未收录域名的保守基准分"，与检索阈值是两个不同的 0.45，网站文案不能混写 | `intelnexus/knowledge/retrieval.py:66`、`credibility.py:266-269` |
| Top-5 检索 | ✅ 主检索 `top_k=5`；简报类目关联检索用 `top_k=8`，若写死"Top-5"建议注明"主检索 Top-5" | `retrieval.py:65`、`intelnexus/briefing/analyzer.py:360` |
| 其他可核验数字 | 测试规模：**95 个测试文件 / 1101 个测试函数 / 126 个测试类**（`tests/test_*.py` 全量计数）；语义模型冷加载 30-60s+，启动后台预热（`ui.py:127`） | tests/ 目录、`ui.py:127` |
| QPS 等性能数字 | **不确定**——代码库无实测基准数据，建议不写 | — |

---

## 3. 我的真实角色表述（可直接用于网站的口径）

**如实表述：需求定义、方案决策、集成与验证由我完成；功能代码以 AI 生成为主、我审查-测试-迭代修正。** 不写"全部独立手写"。

- **需求定义**：确立产品边界——"交付可核验的分析报告而非链接列表"（README「与现有工具的区别」节，明确了与元搜索/答案引擎/定时爬虫三类工具的差异点）。
- **方案决策**：关键设计决策留有决策记录注释。例：风险等级与信息质量彻底解耦（`risk_level.py:4-22` 完整记录了"历史缺陷→重新设计"）；出版方与投放渠道拆分评分（`credibility.py:152-161,182-203`）。
- **验证**：1101 个测试函数覆盖核心链路，含审计回放（`tests/test_audit_replay.py`）与架构门禁（`tests/test_arch_gate.py`）；多轮修复以"审计实锤问题"标注留痕（`credibility.py:233`、`credibility.py:99-104`）。
- **迭代**：代码中以修订号管理迭代（如 `ui.py:127` 的 "R13（2026-10-20）：后台预热语义模型"），修复均带真实动机注释。

---

## 4. 向专业安全团队展示（P0，待补充）

- 时间：**【待补充：请提供】**
- 场合：**【待补充：请提供】**
- 展示形式：**【待补充：请提供】**
- 收到的反馈要点（可公开表述的程度）：**【待补充：请提供】**

> 注：此条代码库无任何记录，按统一规范不臆造，全部留待本人提供。

---

## 5. 可讲的技术决策（动机 → 方案 → 效果）

### 决策一：风险等级与信息质量解耦（推荐主讲的那个）

- **动机**：早期风险等级由来源平均可信度反推（`avg_score < 0.4 → 高`、`< 0.6 → 中`）。语义被彻底扭曲——它表达的是"这批资料质量差"，呈现给读者的却是"目标有中等风险"；更糟的是形成反向激励：资料越权威，风险等级反而越低。（出处 `intelnexus/analysis/risk_level.py:4-9`，注释原文记录）
- **方案**：风险等级只接受**客观威胁证据**——高：CISA KEV 命中 / 公开利用命中 / CVE 命中 ≥3 / 威胁情报源命中 ≥3；中：CVE ≥1 / TI ≥1 / 跨源冲突严重度 ≥0.5；低：仅低强度冲突（0 < severity < 0.5）；并把"**证据不足**"设为一等公民——没有证据就是没有结论，绝不用"中"填充空白。可信度字段在计算中被显式忽略（`risk_level.py:12-22`、`risk_level.py:57-59`、`risk_level.py:70-76`）。
- **效果**：消除"资料越权威风险越低"的倒挂；评分体系与风险体系各管各的语义，不会互相污染。

### 决策二：出版方 ≠ 投放渠道 + 未收录域名 0.45 倒挂修复

- **动机**：搜索结果 URL 常是搜索引擎跳转壳（如 `baidu/link?url=…`），按壳域名打分等于把"携程页面"按 `www.baidu.com` 计分；反向踩坑：解析出真实出版方后，未收录域名落到 0.45 保守分，反而低于引擎回退分 0.5——"真实出版方评分反而更低"的倒挂（`credibility.py:153-155`、`credibility.py:99-104` 注释原文）。
- **方案**：① 优先用解析后的真实地址评分，解析不出时显式标注"出版方未解析"，**不得退回用引擎名冒充出版方**（`credibility.py:182-203`）；② 按类别补齐域名权重表（OTA 平台、UGC/社交媒体、聚合器、安全厂商、漏洞库等），未收录的可识别域名统一给 0.45 保守基准分（`credibility.py:92-110`、`credibility.py:266-269`）；③ 聚合器精确名优先匹配，修复 `'Bing'` 被 `'Bing News'` 子串规则误判为新闻源 0.7 的问题（`credibility.py:275-279`）。
- **效果**：可信度分反映真实出版方权威度；每条结果的 `credibility_details` 里同时带 `source_reliability`（来源固有可靠性）与 `publisher`（出版方），供报告分层展示"渠道 ≠ 出版方"（`credibility.py:231-244`）。

### 备选决策三：时间冲突的事件年份判定（如需第三个）

- **动机**：页面里的裸年份大量是页脚噪音（`© 2009-2026`、版权/备案行），直接参与冲突比较会产生海量假"时间冲突"（`credibility.py:511-516`）。
- **方案**：±30 字符上下文窗口启发式——窗口内命中版权/备案/年份区间标记则判非事件；裸年份必须伴随真实日期表述（`2026年`、`2026-08-20`、`August 21, 2026` 等五种格式）或事件上下文词（发布/发生/上线/released 等）才纳入比较（`credibility.py:518-543`）。
- **效果**：时间冲突聚焦真实事件日期分歧，页脚噪音不再触发误报。

---

## 6. 界面截图 / 报告输出示例（文件路径）

以下均为本机真实产物，可直接取用：

| 类型 | 路径 | 说明 |
|---|---|---|
| 完整简报成品（PDF） | `data/AI网安每日情报简报_20260707_V3.0.pdf` | 带版本号的定时简报成品 |
| 简报 PDF/HTML 成对导出 | `data/briefings/briefing_20260725_203152.pdf`（同目录有 .html） | 多期可选 |
| 知识图谱交互页 | `data/briefings/kg_20260911_001805.html` | pyvis 可交互图谱 |
| 一致性雷达图（png） | `data/snapshots/20260916011912286326/radar.png` | 快照内含 radar.png + kg.html |
| 快照（图 + 图谱） | `data/snapshots/20260916093519105680/`、`data/snapshots/20260916103845489905/` 等 | 多期快照可选 |

⚠️ 注意：`data/reports/` 目录当前为空，报告成品以 `data/` 根目录 PDF 和 `data/briefings/` 为准。

---

## 审计遗留提醒（给网站制作侧）

1. **两个 0.45 别混写**：检索阈值 0.45（`retrieval.py:66`）与未收录域名保守分 0.45（`credibility.py:269`）语义完全不同。
2. **向量模型主名**：`paraphrase-multilingual-MiniLM-L12-v2`，不是 all-MiniLM-L6-v2。
3. **Top-5 分场景**：主检索 Top-5，简报关联 Top-8。
4. 第 4 题（安全团队展示）全条目留空，等本人提供后再补。
