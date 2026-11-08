<template>
  <div class="wrap page">
    <div class="kicker">ABOUT</div>
    <h1>把复杂的问题拆开，再把想法变成真的东西。</h1>
    <div class="cols">
      <section class="block">
        <div class="kicker2">01 / 学习经历</div>
        <p>九江学院 · 软件工程专业 · 2027 届本科。</p>
        <p class="todo">待补充：学习主线、课程与实践经历。</p>
      </section>
      <section class="block">
        <div class="kicker2">02 / 技术兴趣</div>
        <p>Java 后端工程、AI 应用开发（LLM / RAG / Function Calling）、系统集成与自动化。</p>
        <p class="todo">待补充：兴趣如何落到具体项目（云枢 / IntelNexus / 照片平台）。</p>
      </section>
      <section class="block">
        <div class="kicker2">03 / 个人特点与方向</div>
        <p>喜欢把复杂问题拆开理解；重视独立探索与创造；求职方向为 Java 后端与 AI 应用开发岗位。</p>
        <p class="todo">待补充：真实细节，不用空洞自我评价。</p>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.page {
  padding: 50px 0 75px;
}
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 14px;
}
h1 {
  font-size: clamp(28px, 4vw, 42px);
  font-weight: 600;
  letter-spacing: -0.04em;
  max-width: 720px;
}
.cols {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--c-line);
  border: 1px solid var(--c-line);
  margin-top: 40px;
}
.block {
  background: var(--c-bg);
  padding: 24px;

  .kicker2 {
    font: 10px var(--font-mono);
    letter-spacing: 0.14em;
    color: var(--c-blue);
    margin-bottom: 12px;
  }
  p {
    font-size: 13px;
    line-height: 1.9;
    color: var(--c-ink-soft);
  }
  .todo {
    color: var(--c-muted);
    font-size: 12px;
  }
}
@media (max-width: 760px) {
  .cols {
    grid-template-columns: 1fr;
  }
}
</style>
