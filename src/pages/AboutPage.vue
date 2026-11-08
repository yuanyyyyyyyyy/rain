<template>
  <div class="wrap page">
    <div class="kicker">ABOUT</div>
    <h1>把复杂的问题拆开，再把想法变成真的东西。</h1>
    <div class="cols">
      <section class="block">
        <div class="kicker2">01 / 学习经历</div>
        <p>九江学院 · 软件工程专业 · 2027 届本科。</p>
        <p>
          学习主线从 Java 语法与数据结构，走到 Spring 全家桶的真实工程：缓存、并发、消息队列、
          鉴权与测试——大部分知识最终都落回了具体项目的具体问题上。
        </p>
        <!-- TODO(内容更新)：课程/竞赛/获奖等真实细节，素材到位后补充 -->
      </section>
      <section class="block">
        <div class="kicker2">02 / 技术兴趣</div>
        <p>Java 后端工程、AI 应用开发（LLM / RAG / Function Calling）、系统集成与自动化。</p>
        <p>
          这些兴趣都落在具体项目里：云枢的缓存与并发压测、IntelNexus 的多源情报分析与 RAG 链路、
          以及这个网站自己的内容管理后台——文件上传、JWT 鉴权与自动化发布。
        </p>
      </section>
      <section class="block">
        <div class="kicker2">03 / 个人特点与方向</div>
        <p>喜欢把复杂问题拆开理解；重视独立探索与创造；求职方向为 Java 后端与 AI 应用开发岗位。</p>
        <p>
          你正在看的这个网站连同它的管理后台，就是我独立完成的一个完整工程——
          从视觉系统、摄影排版到发布链路与单元测试，每个环节都可以追问。
        </p>
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
}
@media (max-width: 760px) {
  .cols {
    grid-template-columns: 1fr;
  }
}
</style>
