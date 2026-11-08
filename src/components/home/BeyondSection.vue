<template>
  <section id="about" class="section wrap">
    <div class="sec-head">
      <div>
        <div class="kicker">03 / BEYOND CODE</div>
        <h2>Curiosity keeps me moving.</h2>
      </div>
      <div class="note">A LITTLE MORE HUMAN</div>
    </div>
    <div class="about">
      <p>
        我喜欢把复杂的问题拆开，理解它们如何运作，再尝试把想法变成真实的东西。<br />
        写代码之外，我也喜欢摄影、游戏，以及观察日常里那些容易被忽略的细节。<br /><br />
        我希望技术工作能创造价值，也希望生活始终留有探索和创造的余地。
      </p>
      <RouterLink class="photo" to="/photography" aria-label="前往摄影页">
        <svg viewBox="0 0 500 230" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
          <rect width="500" height="230" fill="#f4f6fb" />
          <path d="M0 170 L120 70 L220 160 L320 45 L500 175 V230 H0Z" fill="#e0e6f2" />
          <path d="M0 190 L170 110 L280 185 L390 95 L500 180 V230 H0Z" fill="#c4d0e6" />
          <path d="M0 205 L150 160 L265 205 L390 145 L500 200 V230 H0Z" fill="#9eafd1" />
          <g stroke="#002FA7" stroke-width=".8" fill="none" opacity=".65">
            <path d="M320 0 V230 M350 0 V230 M380 0 V230" />
            <path d="M0 35 H500 M0 65 H500" />
          </g>
          <circle cx="390" cy="55" r="23" fill="#002FA7" opacity=".8" />
        </svg>
        <span class="photo-label">FIELD NOTE / LIGHT STUDY — 003 →</span>
      </RouterLink>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.about {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  align-items: center;

  > p {
    font-size: 14px;
    line-height: 2.1;
    color: #626970;
  }
}
.photo {
  height: 230px;
  border: 1px solid #bfc8dc;
  position: relative;
  overflow: hidden;
  display: block;
  transition: 0.22s;

  &:hover {
    border-color: #8fa5dc;
    box-shadow: 5px 5px 0 #e7ecf8;
  }

  svg {
    width: 100%;
    height: 100%;
    display: block;
  }
}
.photo-label {
  position: absolute;
  bottom: 12px;
  left: 15px;
  font: 9px var(--font-mono);
  letter-spacing: 0.1em;
  color: var(--c-blue);
  background: #fff;
  padding: 4px 7px;
}
@media (max-width: 760px) {
  .about {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
