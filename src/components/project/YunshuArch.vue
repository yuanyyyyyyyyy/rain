<template>
  <!-- 工业图纸视觉资产：SVG 内 fill/stroke 色值为 tokens 同值字面量（属性不吃 var()），
       改主题时需与本组件一并核查；CSS 域一律走 var() -->
  <figure class="arch">
    <div class="arch-label">FIG. 001 — SYSTEM ARCHITECTURE / his-j8-setroster</div>
    <svg viewBox="0 0 920 600" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="云枢平台微服务架构图">
      <!-- 坐标辅助线 -->
      <g stroke="#aab8d8" stroke-width=".6" stroke-dasharray="3 5" opacity=".7">
        <path d="M20 300 H900" /><path d="M460 40 V560" />
      </g>

      <!-- 网关层 -->
      <g>
        <rect x="60" y="46" width="180" height="52" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="72" y="68" font-size="11" font-weight="600" fill="#202327">his-gateway</text>
        <text x="72" y="84" font-size="9" fill="#777D82">网关鉴权 · Redis URL→角色映射</text>
        <rect x="640" y="46" width="220" height="52" fill="#fff" stroke="#D9DCE0" stroke-width="1" />
        <text x="652" y="68" font-size="11" font-weight="600" fill="#202327">his-login / his-oauth2</text>
        <text x="652" y="84" font-size="9" fill="#777D82">登录会话（Redis Hash + TTL）</text>
      </g>

      <!-- 服务层 -->
      <!-- 本人负责：实心强调 -->
      <g>
        <rect x="60" y="170" width="380" height="180" fill="#EAF0FF" stroke="#002FA7" stroke-width="1.4" />
        <rect x="60" y="170" width="42" height="3" fill="#002FA7" />
        <text x="76" y="196" font-size="12" font-weight="700" fill="#002FA7">his-j8-setroster — 本人负责</text>
        <text x="76" y="212" font-size="9" fill="#777D82">排班主数据服务 · 单人提交 · 18 REST + 1 AI</text>
        <g font-size="10" fill="#202327">
          <rect x="76" y="228" width="104" height="30" fill="#fff" stroke="#4167C5" stroke-width=".9" />
          <text x="86" y="246">日/周排班模板</text>
          <rect x="192" y="228" width="104" height="30" fill="#fff" stroke="#4167C5" stroke-width=".9" />
          <text x="202" y="246">号源时段/放号策略</text>
          <rect x="76" y="270" width="104" height="30" fill="#fff" stroke="#4167C5" stroke-width=".9" />
          <text x="86" y="288">日历例外(停诊)</text>
          <rect x="192" y="270" width="104" height="30" fill="#fff" stroke="#4167C5" stroke-width=".9" />
          <text x="202" y="288">停诊通知任务</text>
          <rect x="308" y="228" width="116" height="72" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
          <text x="318" y="248" fill="#002FA7" font-weight="600">AI 排班助手</text>
          <text x="318" y="262" font-size="8.5" fill="#777D82">意图分类 QUERY/PLAN/</text>
          <text x="318" y="272" font-size="8.5" fill="#777D82">KNOWLEDGE · Function</text>
          <text x="318" y="282" font-size="8.5" fill="#777D82">Calling · RAG Top-3</text>
          <text x="318" y="294" font-size="8.5" fill="#002FA7">规则引擎兜底</text>
        </g>
      </g>
      <!-- 兄弟服务 -->
      <g>
        <rect x="520" y="170" width="180" height="180" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="536" y="196" font-size="12" font-weight="600" fill="#202327">his-j8-clinics</text>
        <text x="536" y="212" font-size="9" fill="#777D82">预约/停诊执行服务</text>
        <g font-size="10" fill="#202327">
          <rect x="536" y="228" width="148" height="30" fill="#FBFCFF" stroke="#D9DCE0" stroke-width=".9" />
          <text x="546" y="246">号源状态流转</text>
          <rect x="536" y="270" width="148" height="30" fill="#FBFCFF" stroke="#D9DCE0" stroke-width=".9" />
          <text x="546" y="288">患者短信（阿里云 Sms）</text>
        </g>
        <rect x="712" y="170" width="148" height="180" fill="#fff" stroke="#D9DCE0" stroke-width="1" />
        <text x="728" y="196" font-size="11" font-weight="600" fill="#777D82">兄弟模块</text>
        <g font-size="9.5" fill="#777D82">
          <text x="728" y="220">his-j8-calendar</text>
          <text x="728" y="240">his-j8-outpatient</text>
          <text x="728" y="260">his-j8-statdata</text>
          <text x="728" y="280">his-mcp-gateway</text>
        </g>
      </g>

      <!-- 基础设施层 -->
      <g>
        <rect x="60" y="430" width="180" height="56" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="76" y="454" font-size="11" font-weight="600" fill="#202327">Nacos</text>
        <text x="76" y="470" font-size="9" fill="#777D82">注册中心 / 配置中心</text>
        <rect x="264" y="430" width="180" height="56" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="280" y="454" font-size="11" font-weight="600" fill="#202327">MySQL</text>
        <text x="280" y="470" font-size="9" fill="#777D82">排班主数据 · 唯一索引幂等</text>
        <rect x="468" y="430" width="180" height="56" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="484" y="454" font-size="11" font-weight="600" fill="#202327">Redis / Redisson</text>
        <text x="484" y="470" font-size="9" fill="#777D82">Cache-Aside · 互斥重建</text>
        <rect x="672" y="430" width="188" height="56" fill="#fff" stroke="#002FA7" stroke-width="1.1" />
        <text x="688" y="454" font-size="11" font-weight="600" fill="#202327">RocketMQ</text>
        <text x="688" y="470" font-size="9" fill="#777D82">topic his-setroster-stop-clinic</text>
      </g>

      <!-- 调用关系连线 -->
      <g stroke="#002FA7" stroke-width="1" fill="none">
        <!-- 客户端/网关 → setroster -->
        <path d="M150 98 V170" />
        <path d="M145 163 L150 170 L155 163" />
        <!-- setroster ↔ clinics：主数据被消费 -->
        <path d="M440 260 H520" />
        <path d="M513 255 L520 260 L513 265" />
        <text x="448" y="252" font-size="8.5" fill="#777D82" stroke="none">排班主数据</text>
        <!-- setroster → MySQL -->
        <path d="M250 350 V430" />
        <path d="M245 423 L250 430 L255 423" />
        <text x="258" y="400" font-size="8.5" fill="#777D82" stroke="none">持久化</text>
        <!-- setroster → Redis -->
        <path d="M420 350 C 500 390 520 395 545 430" />
        <text x="470" y="404" font-size="8.5" fill="#777D82" stroke="none">缓存读写</text>
      </g>
      <!-- 异步链路：虚线 -->
      <g stroke="#002FA7" stroke-width="1" stroke-dasharray="4 4" fill="none">
        <!-- setroster → MQ（生产者） -->
        <path d="M250 350 C 250 400 600 380 740 430" />
        <path d="M733 422 L740 430 L730 432" />
        <!-- MQ → 通知任务落库（消费者） -->
        <path d="M766 486 V520 H350 V486" />
        <text x="500" y="538" font-size="8.5" fill="#777D82" stroke="none">CLOSED 事件 → 消费展开（≤90 天）→ bkg_stop_clinic_notify_task 唯一键幂等</text>
        <!-- clinics → 短信链路（独立） -->
        <path d="M610 350 V430" stroke="#777D82" stroke-dasharray="2 4" />
        <!-- Nacos 注册（虚点） -->
        <path d="M150 430 V360" stroke="#777D82" stroke-dasharray="2 4" />
        <path d="M610 430 V350" stroke="#777D82" stroke-dasharray="2 4" />
        <text x="30" y="404" font-size="8.5" fill="#777D82" stroke="none">服务注册</text>
      </g>

      <!-- 图例与定位标记 -->
      <g font-size="8.5" fill="#777D82">
        <text x="30" y="576">— 实线：同步调用　--- 虚线：异步事件 / 注册　□ 蓝底：本人负责模块</text>
        <circle cx="878" cy="170" r="4" stroke="#002FA7" stroke-width="1.1" fill="#fff" />
        <path d="M878 161 V179 M869 170 H887" stroke="#002FA7" stroke-width="1.1" />
      </g>
      <g fill="#002FA7"><rect x="54" y="164" width="12" height="12" /></g>
    </svg>
    <figcaption class="arch-note">
      两链路互不调用：停诊通知 ① clinics 侧同步短信（阿里云 SmsComponent）；② setroster 侧日历例外 CLOSED 事件经 RocketMQ 异步展开为通知任务。AI 排班助手的合规判定由确定性规则引擎 RosterRuleChecker（纯 Java，R1–R4）负责，模型不直接写库。
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
