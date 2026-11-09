<template>
  <!-- 工业图纸视觉资产：SVG 内 fill/stroke 色值为 tokens 同值字面量（属性不吃 var()），
       改主题时需与本组件一并核查；CSS 域一律走 var() -->
  <figure class="arch">
    <div class="arch-label">FIG. 001 — UNIFIED RUNTIME / Forge</div>
    <svg viewBox="0 0 920 540" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Forge 统一 Runtime 分层架构图">
      <!-- 坐标辅助线 -->
      <g stroke="#aab8d8" stroke-width=".6" stroke-dasharray="3 5" opacity=".7">
        <path d="M20 330 H900" /><path d="M460 30 V520" />
      </g>

      <!-- 入口层（上排五类入口共享同一执行链路） -->
      <g>
        <rect x="30" y="46" width="160" height="64" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="44" y="72" font-size="11" font-weight="600" fill="#202327">CLI</text>
        <g font-size="9" fill="#626970"><text x="44" y="90">本地终端交互</text></g>

        <rect x="216" y="46" width="160" height="64" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="230" y="72" font-size="11" font-weight="600" fill="#202327">终端 TUI</text>
        <g font-size="9" fill="#626970"><text x="230" y="90">长会话面板</text></g>

        <rect x="402" y="46" width="160" height="64" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="416" y="72" font-size="11" font-weight="600" fill="#202327">后台 Gateway</text>
        <g font-size="9" fill="#626970"><text x="416" y="90">HTTP / API 调用</text></g>

        <rect x="588" y="46" width="150" height="64" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="602" y="72" font-size="11" font-weight="600" fill="#202327">定时任务</text>
        <g font-size="9" fill="#626970"><text x="602" y="90">无人值守执行</text></g>

        <rect x="764" y="46" width="126" height="64" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="778" y="72" font-size="11" font-weight="600" fill="#202327">消息渠道</text>
        <g font-size="9" fill="#626970">
          <text x="778" y="90">飞书 / QQ / 企业微信</text>
        </g>
      </g>

      <!-- 入口 → Runtime 汇流箭头 -->
      <g stroke="#002FA7" stroke-width="1" fill="none">
        <path d="M110 110 V152" /><path d="M103 145 L110 152 L117 145" />
        <path d="M296 110 V152" /><path d="M289 145 L296 152 L303 145" />
        <path d="M482 110 V152" /><path d="M475 145 L482 152 L489 145" />
        <path d="M663 110 V152" /><path d="M656 145 L663 152 L670 145" />
        <path d="M827 110 V152" /><path d="M820 145 L827 152 L834 145" />
      </g>
      <text x="460" y="140" font-size="8.5" fill="#777D82" text-anchor="middle" stroke="none">五类入口 · 消除各入口重复实现</text>

      <!-- Runtime 统一层（核心，蓝底） -->
      <g>
        <rect x="30" y="160" width="860" height="150" fill="#EAF0FF" stroke="#002FA7" stroke-width="1.4" />
        <rect x="30" y="160" width="42" height="3" fill="#002FA7" />
        <text x="46" y="188" font-size="11.5" font-weight="700" fill="#002FA7">② RUNTIME 统一层 — Turn 执行单元（会话级串行 · 跨会话并行）</text>

        <g>
          <rect x="52" y="206" width="190" height="80" fill="#fff" stroke="#002FA7" stroke-width="1" />
          <text x="66" y="228" font-size="10" font-weight="600" fill="#202327">① Agent 循环编排</text>
          <g font-size="9" fill="#626970">
            <text x="66" y="246">CLI/TUI/Gateway/消息渠道</text>
            <text x="66" y="260">共享同一条执行链路</text>
            <text x="66" y="276">4,160 次真实请求 0 丢失 0 重复</text>
          </g>

          <rect x="268" y="206" width="190" height="80" fill="#fff" stroke="#002FA7" stroke-width="1" />
          <text x="282" y="228" font-size="10" font-weight="600" fill="#202327">③ 上下文组装</text>
          <g font-size="9" fill="#626970">
            <text x="282" y="246">系统 / 工具 / 滚动历史三层</text>
            <text x="282" y="260">前缀稳定 → 利用 Provider 缓存</text>
            <text x="282" y="276">成本 -72% · 缓存命中 ≈74%</text>
          </g>

          <rect x="484" y="206" width="190" height="80" fill="#fff" stroke="#002FA7" stroke-width="1" />
          <text x="498" y="228" font-size="10" font-weight="600" fill="#202327">④ 工具分级并发</text>
          <g font-size="9" fill="#626970">
            <text x="498" y="246">按读写属性分级：只并行</text>
            <text x="498" y="260">安全读取，写入保持顺序</text>
            <text x="498" y="276">8 路中位 374ms → 113ms</text>
          </g>

          <rect x="700" y="206" width="168" height="80" fill="#fff" stroke="#002FA7" stroke-width="1" />
          <text x="714" y="228" font-size="10" font-weight="600" fill="#202327">会话持久化</text>
          <g font-size="9" fill="#626970">
            <text x="714" y="246">会话隔离 + 后台任务并发</text>
            <text x="714" y="260">端到端 P95 11.66s</text>
            <text x="714" y="276">验收通过率 99.95%</text>
          </g>
        </g>
      </g>

      <!-- Runtime → Provider 箭头 -->
      <g stroke="#002FA7" stroke-width="1" fill="none">
        <path d="M300 310 V352" /><path d="M293 345 L300 352 L307 345" />
        <path d="M620 310 V352" /><path d="M613 345 L620 352 L627 345" />
      </g>

      <!-- Provider 层（下排） -->
      <g>
        <rect x="140" y="360" width="320" height="110" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="156" y="386" font-size="11" font-weight="600" fill="#202327">LiteLLM 统一 Provider 接入</text>
        <g font-size="9" fill="#626970">
          <text x="156" y="406">多模型供应商同一调用契约</text>
          <text x="156" y="420">Provider 侧前缀缓存（对照实验 72 Trials）</text>
          <text x="156" y="438">MCP 工具协议接入</text>
          <text x="156" y="456">uv 管理依赖 · pytest 验收</text>
        </g>

        <rect x="540" y="360" width="240" height="110" fill="#fff" stroke="#D9DCE0" stroke-width="1" />
        <text x="556" y="386" font-size="11" font-weight="600" fill="#777D82">ForgeBench 评测体系</text>
        <g font-size="9" fill="#777D82">
          <text x="556" y="406">冻结模型 / 参数 / Prompt 变量</text>
          <text x="556" y="420">保留执行凭证与证据摘要</text>
          <text x="556" y="438">收益可归因 · 防模型波动误判</text>
          <text x="556" y="456">验收通过率 99.95% 的出处</text>
        </g>
      </g>

      <!-- ForgeBench → Runtime 虚线（评测反馈，不进入生产链路） -->
      <g stroke="#777D82" stroke-width="1" stroke-dasharray="4 4" fill="none">
        <path d="M660 360 C 660 330 700 322 740 310" />
        <text x="690" y="342" font-size="8.5" fill="#777D82" stroke="none">评测反馈</text>
      </g>

      <!-- 图例与标记 -->
      <g font-size="8.5" fill="#777D82">
        <text x="30" y="494">— 实线：生产执行链路</text>
        <text x="30" y="510">--- 虚线：评测反馈（不进生产）</text>
        <text x="30" y="526">蓝底：统一 Runtime（消除入口重复实现）</text>
      </g>
      <g fill="#002FA7"><rect x="24" y="40" width="12" height="12" /></g>
    </svg>
    <figcaption class="arch-note">
      语义说明：编号①–④对应下方「核心实现」各条目。入口层与 Runtime 之间是唯一的耦合面——新增入口只需实现 Turn 的装配，不再复制执行循环；Provider 缓存收益来自上下文三层的「前缀稳定」设计（详见核心实现③）。
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
