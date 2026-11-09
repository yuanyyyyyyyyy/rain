// ============================================
// 站点内容数据 — 文案改动只动这里，不动组件
// ============================================

// 埋点/联系表单的后端地址：'' = 同源（本地 dev 经 vite proxy 到 Spring，可用）。
// Cloudflare Worker 已部署（https://rain-collector.yuanyyyyyyyyy.workers.dev）：
// 埋点与表单入库；国内直连 workers.dev 受限时前端静默降级——提示访客直接邮箱联系（刻意不弹 mailto），无害。
export const apiBase = 'https://rain-collector.yuanyyyyyyyyy.workers.dev'

export const profile = {
  name: '陈雨',
  school: '九江学院 · 软件工程 · 2027 届',
  focus: '关注 Java 后端开发、AI 应用与系统工程。',
  location: 'JIUJIANG, CHINA',
  email: 'm13237097902@163.com',
  // GitHub 暂不展示（用户决定），此处留空即隐藏 GITHUB 按钮
  github: '',
}

// === PROJECTS:AUTO-START ===
export const projects = [
  {
    slug: 'yunshu',
    no: '001',
    flagship: true,
    period: '2026.06 — 至今',
    title: '云枢智慧医院管理平台',
    summary:
      '基于 Spring Cloud Alibaba 微服务架构的智慧医院管理平台，团队 8 人。我独立负责排班预约服务 his-j8-setroster 的后端全部代码（该模块单人提交）：日/周排班模板、号源时段与放号策略、日历例外（停诊源头）、停诊通知任务与 AI 排班助手，共 24 个 REST 接口 + 1 个 AI 接口。',
    tags: ['JAVA', 'SPRING BOOT', 'SPRING CLOUD ALIBABA', 'MYBATIS-PLUS', 'REDIS/REDISSON', 'ROCKETMQ', 'SPRING AI'],
    metrics: [
      { label: 'P99', value: '1.468s → 0.148s' },
      { label: '吞吐', value: '496 → 2480 req/s' },
      { label: '平均响应', value: '99.3 → 19.7ms' },
    ],
  },
  {
    slug: 'intelnexus',
    no: '002',
    period: '2026',
    title: 'IntelNexus',
    summary:
      'AI 驱动的多源网络情报分析平台：以可信度评估、证据链追溯与冲突检测为分析内核，上接 15 类信息源的多源采集，下接定时简报与可导出的结构化报告。我独立完成需求定义、方案决策、集成与验证；功能代码以 AI 生成为主，由我负责设计约束、逐轮审查、测试与迭代修正。',
    tags: ['PYTHON', 'LANGCHAIN', 'STREAMLIT', 'NETWORKX/PYVIS', 'SENTENCE-TRANSFORMERS'],
    metrics: [
      { label: '测试文件', value: '95 个' },
      { label: '测试函数', value: '1101 个' },
      { label: '测试类', value: '126 个' },
    ],
  },
  {
    slug: 'shimmer',
    no: '003',
    period: 'COURSE PROJECT',
    title: '照片光影展示平台',
    summary:
      '围绕照片展示与视觉表达构建的全栈项目，结合图片处理、对象存储与 AI 能力。',
    tags: ['VUE 3', 'EXPRESS', 'MYSQL', 'AI API'],
  }
]
// === PROJECTS:AUTO-END ===

export const capabilities = [
  {
    num: '01 — ENGINEERING',
    title: 'Backend Development',
    desc: 'Java · Spring Boot · Spring Cloud\nMySQL · Redis · RocketMQ',
  },
  {
    num: '02 — INTELLIGENCE',
    title: 'AI Application',
    desc: 'LLM · RAG · Function Calling\nLangChain · Spring AI',
  },
  {
    num: '03 — INTEGRATION',
    title: 'Product Thinking',
    desc: '需求拆解 · 系统集成\n方案验证 · 工程协作',
  },
]
