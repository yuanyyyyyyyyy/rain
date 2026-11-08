<template>
  <section class="hero wrap">
    <div class="copy">
      <div class="eyebrow">
        <i class="signal"></i> AVAILABLE FOR OPPORTUNITIES
        <span class="dim">/ 2027</span>
      </div>
      <h1>Building things.<span>Exploring ideas.</span></h1>
      <div class="cn">你好，我是{{ profile.name }}。</div>
      <div class="desc">
        软件工程专业 · 2027 届<br />
        {{ profile.focus }}
      </div>
      <div class="actions">
        <a class="btn primary" href="#work">VIEW SELECTED WORK <b>↗</b></a>
        <a class="btn" href="#contact">GET IN TOUCH <b>＋</b></a>
        <!-- TODO(上线前): 接入真实简历 PDF 下载 -->
        <a class="btn ghost" href="#contact">下载简历 <b>↓</b></a>
      </div>
    </div>

    <div class="art">
      <div class="art-label">FIG. 001 — PERSONAL SYSTEM</div>
      <!-- 几何主视觉：自 Klein Blue v2 原型 1:1 迁移，站点核心视觉资产，勿替换 -->
      <svg viewBox="0 0 520 390" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <g stroke="#002FA7" stroke-width=".85" opacity=".78">
          <path d="M65 300 L190 62 L315 300 Z" /><path d="M103 300 L190 132 L277 300 Z" /><path d="M143 300 L190 202 L237 300 Z" />
          <path d="M190 62 V330 M65 300 H365 M100 230 H327 M130 175 H298" />
          <path d="M190 62 L365 300 M190 62 L15 300" />
          <path d="M330 92 L450 212 L330 332 L210 212 Z" opacity=".55" />
          <path d="M330 125 L417 212 L330 299 L243 212 Z" opacity=".55" />
          <path d="M330 92 V332 M210 212 H450" opacity=".55" />
        </g>
        <g stroke="#002FA7" stroke-width="1.3">
          <path d="M190 62 L205 47 L220 62" /><path d="M365 300 L380 315 L395 300" /><circle cx="330" cy="212" r="5" />
          <path d="M330 199 V225 M317 212 H343" />
        </g>
        <g stroke="#aab8d8" stroke-width=".65" stroke-dasharray="3 5">
          <path d="M18 195 H500" /><path d="M260 18 V370" />
        </g>
        <g fill="#002FA7" font-family="monospace" font-size="8" letter-spacing="1.2">
          <text x="30" y="326">STRUCTURE / 01</text><text x="350" y="350">FORM &amp; FUNCTION</text>
        </g>
        <g fill="#002FA7">
          <rect x="184" y="56" width="12" height="12" /><rect x="324" y="206" width="12" height="12" fill="#fff" stroke="#002FA7" />
        </g>
      </svg>
      <div class="art-code">YUAN — 001 / 026</div>
    </div>

    <div class="hero-foot">
      <span>{{ profile.location }}</span>
      <span>ENGINEERING · AI · CREATIVE</span>
      <span>SCROLL TO EXPLORE ↓</span>
    </div>
  </section>
</template>

<script setup>
import { profile } from '../../data/site.js'
</script>

<style lang="scss" scoped>
.hero {
  min-height: 600px;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  gap: 44px;
  align-items: center;
  padding: 78px 0 74px;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 35px;
    left: 0;
    right: 0;
    border-top: 1px solid var(--c-line);
  }
}
.eyebrow {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--c-blue);
  font: 10px var(--font-mono);
  letter-spacing: 0.14em;
  margin-bottom: 28px;

  .signal {
    width: 7px;
    height: 7px;
    background: var(--c-blue);
    transform: rotate(45deg);
  }
  .dim {
    color: #9aa0a8;
  }
}
h1 {
  font-size: clamp(48px, 6.3vw, 79px);
  line-height: 1.04;
  letter-spacing: -0.065em;
  font-weight: 600;

  span {
    display: block;
    color: #a1a6ad;
    font-weight: 400;
  }
}
.cn {
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 0.08em;
  margin: 27px 0 9px;
}
.desc {
  font-size: 13px;
  line-height: 2;
  color: var(--c-muted);
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 30px;
  flex-wrap: wrap;

  .ghost {
    border-color: var(--c-line);
    color: var(--c-muted);
  }
}
.art {
  height: 390px;
  border: 1px solid var(--c-line-blue);
  position: relative;
  overflow: hidden;
  background: var(--c-bg);

  &::before {
    content: '';
    position: absolute;
    inset: 17px;
    border: 1px solid #dce2ef;
  }
  &::after {
    content: '';
    position: absolute;
    inset: 28px;
    border: 1px solid #edf0f6;
  }
}
.art-label,
.art-code {
  position: absolute;
  z-index: 2;
  color: var(--c-blue);
  font: 9px var(--font-mono);
  letter-spacing: 0.14em;
}
.art-label {
  top: 34px;
  left: 36px;
}
.art-code {
  right: 35px;
  bottom: 31px;
  color: var(--c-muted);
}
.art svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.hero-foot {
  position: absolute;
  bottom: 20px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--c-line);
  padding-top: 12px;
  color: var(--c-muted);
  font: 9px var(--font-mono);
  letter-spacing: 0.1em;
}

@media (max-width: 760px) {
  .hero {
    grid-template-columns: 1fr;
    padding: 60px 0 70px;
    gap: 32px;
  }
  .art {
    height: 300px;
  }
  .hero-foot {
    position: static;
    margin-top: 24px;
  }
  h1 {
    font-size: 53px;
  }
}
</style>
