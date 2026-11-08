<template>
  <!-- 工业图纸视觉资产：SVG 内 fill/stroke 色值为 tokens 同值字面量（属性不吃 var()），
       改主题时需与本组件一并核查；CSS 域一律走 var() -->
  <figure class="arch">
    <div class="arch-label">FIG. 002 — ANALYSIS PIPELINE / IntelNexus</div>
    <svg viewBox="0 0 920 540" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="IntelNexus 分析管线图">
      <!-- 坐标辅助线 -->
      <g stroke="#aab8d8" stroke-width=".6" stroke-dasharray="3 5" opacity=".7">
        <path d="M20 210 H900" /><path d="M460 30 V520" />
      </g>

      <!-- 管线主链（上排） -->
      <g>
        <rect x="30" y="60" width="190" height="110" fill="#EAF0FF" stroke="#002FA7" stroke-width="1.4" />
        <rect x="30" y="60" width="42" height="3" fill="#002FA7" />
        <text x="44" y="84" font-size="11.5" font-weight="700" fill="#002FA7">① 多源采集</text>
        <g font-size="9" fill="#626970">
          <text x="44" y="104">15 类内置源：网页 / 新闻 /</text>
          <text x="44" y="118">威胁情报 NVD·CISA KEV·</text>
          <text x="44" y="132">CNVD·OTX / arXiv / HN 等</text>
          <text x="44" y="150">+ 自定义 RSS / .onion</text>
          <text x="44" y="164">线程池并发 · 归一化去重</text>
        </g>

        <rect x="266" y="60" width="190" height="110" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="280" y="84" font-size="11.5" font-weight="600" fill="#202327">② M-SCORE 可信度</text>
        <g font-size="9" fill="#626970">
          <text x="280" y="104">域名权威 30% · 时效 25%</text>
          <text x="280" y="118">内容深度 20% · 跨源一致 25%</text>
          <text x="280" y="136">出版方 ≠ 投放渠道</text>
          <text x="280" y="154">主证源 / 佐证源 / 孤证源</text>
        </g>

        <rect x="502" y="60" width="190" height="110" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="516" y="84" font-size="11.5" font-weight="600" fill="#202327">③ CIDAR 冲突识别</text>
        <g font-size="9" fill="#626970">
          <text x="516" y="104">数值 / 时间 / 立场三类</text>
          <text x="516" y="118">severity 0–1 如实呈现分歧</text>
          <text x="516" y="136">时间冲突：±30 字符窗口</text>
          <text x="516" y="154">过滤页脚版权噪音年份</text>
        </g>

        <rect x="738" y="60" width="152" height="110" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="752" y="84" font-size="11.5" font-weight="600" fill="#202327">④ STREAM 证据链</text>
        <g font-size="9" fill="#626970">
          <text x="752" y="104">报告逐句拆 claim</text>
          <text x="752" y="118">与原文段落语义对齐</text>
          <text x="752" y="136">coverage 有据占比</text>
          <text x="752" y="154">confidence ≥ 0.5 注角标</text>
        </g>
      </g>

      <!-- 主链箭头 -->
      <g stroke="#002FA7" stroke-width="1" fill="none">
        <path d="M220 115 H266" /><path d="M259 110 L266 115 L259 120" />
        <path d="M456 115 H502" /><path d="M495 110 L502 115 L495 120" />
        <path d="M692 115 H738" /><path d="M731 110 L738 115 L731 120" />
      </g>

      <!-- 支撑层（中排） -->
      <g>
        <rect x="266" y="250" width="190" height="104" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="280" y="274" font-size="11.5" font-weight="600" fill="#202327">⑤ 知识图谱</text>
        <g font-size="9" fill="#626970">
          <text x="280" y="294">spaCy 中英文实体抽取</text>
          <text x="280" y="308">（噪声黑名单过滤）</text>
          <text x="280" y="326">NetworkX 共现建图 · 10 类</text>
          <text x="280" y="340">语义关系 · 中心度 / 社区发现</text>
        </g>

        <rect x="502" y="250" width="190" height="104" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="516" y="274" font-size="11.5" font-weight="600" fill="#202327">⑥ RAG 检索</text>
        <g font-size="9" fill="#626970">
          <text x="516" y="294">sentence-transformers 多语言</text>
          <text x="516" y="308">余弦 ≥ 0.45 · 主检索 Top-5</text>
          <text x="516" y="326">（简报关联 Top-8）</text>
          <text x="516" y="344">指纹缓存跳过重编码</text>
        </g>

        <rect x="738" y="250" width="152" height="104" fill="#fff" stroke="#D9DCE0" stroke-width="1" />
        <text x="752" y="274" font-size="11" font-weight="600" fill="#777D82">快照留存</text>
        <g font-size="9" fill="#777D82">
          <text x="752" y="294">radar 一致性雷达图</text>
          <text x="752" y="308">kg.html 交互图谱</text>
          <text x="752" y="326">简报 PDF / HTML</text>
          <text x="752" y="344">按期版本化（V3.0…）</text>
        </g>
      </g>

      <!-- 支撑层连线 -->
      <g stroke="#002FA7" stroke-width="1" stroke-dasharray="4 4" fill="none">
        <path d="M125 170 C 125 210 200 220 266 265" />
        <text x="120" y="216" font-size="8.5" fill="#777D82" stroke="none">抓取文本</text>
        <path d="M361 250 V210 C 361 190 340 180 320 170" />
        <path d="M596 250 C 596 220 430 200 380 170" />
        <text x="612" y="230" font-size="8.5" fill="#777D82" stroke="none">历史知识注入采集与分析</text>
      </g>
      <g stroke="#002FA7" stroke-width="1" fill="none">
        <path d="M456 302 H502" /><path d="M495 297 L502 302 L495 307" />
        <path d="M692 302 H738" /><path d="M731 297 L738 302 L731 307" />
      </g>

      <!-- 输出层（下排） -->
      <g>
        <rect x="266" y="420" width="396" height="90" fill="#EAF0FF" stroke="#002FA7" stroke-width="1.4" />
        <rect x="266" y="420" width="42" height="3" fill="#002FA7" />
        <text x="282" y="446" font-size="11.5" font-weight="700" fill="#002FA7">⑦ 结构化报告</text>
        <g font-size="9" fill="#626970">
          <text x="282" y="466">LLM 流式生成 · 事实 / 分析 / 推测三级拆分 + 整体置信度</text>
          <text x="282" y="484">风险等级只认客观威胁证据 · 证据不足 = 无结论（不以"中"填充）</text>
          <text x="282" y="502">一键导出 Markdown / PDF / Word / Excel</text>
        </g>
        <path d="M814 354 V420 H662" stroke="#002FA7" stroke-width="1" fill="none" />
        <path d="M669 415 L662 420 L669 425" stroke="#002FA7" stroke-width="1" fill="none" />
      </g>

      <!-- 图例与标记 -->
      <g font-size="8.5" fill="#777D82">
        <text x="30" y="446">— 实线：主分析链</text>
        <text x="30" y="462">--- 虚线：知识 / 文本注入</text>
        <text x="30" y="478">蓝底：核心输出</text>
        <circle cx="60" cy="506" r="4" stroke="#002FA7" stroke-width="1.1" fill="#fff" />
        <path d="M60 497 V515 M51 506 H69" stroke="#002FA7" stroke-width="1.1" />
      </g>
      <g fill="#002FA7"><rect x="24" y="54" width="12" height="12" /></g>
    </svg>
    <figcaption class="arch-note">
      语义说明：检索阈值 0.45（RAG 知识库）与未收录域名保守基准分 0.45（M-SCORE）是两个独立参数，语义不同。风险等级与信息质量彻底解耦——风险只接受客观威胁证据，可信度字段在风险计算中被显式忽略。
    </figcaption>
  </figure>
</template>

<style lang="scss" scoped>
.arch {
  margin: 0;
  border: 1px solid var(--c-line-blue);
  background: var(--c-bg);
  position: relative;
  padding: 44px 18px 14px;

  .arch-label {
    position: absolute;
    top: 14px;
    left: 18px;
    color: var(--c-blue);
    font: 9px var(--font-mono);
    letter-spacing: 0.14em;
  }
  svg {
    width: 100%;
    height: auto;
    display: block;
  }
}
.arch-note {
  margin-top: 10px;
  border-top: 1px solid var(--c-line);
  padding-top: 10px;
  font-size: 11px;
  line-height: 1.8;
  color: var(--c-muted);
}
</style>
