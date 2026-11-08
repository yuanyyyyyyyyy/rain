// ============================================
// 站点内容数据 — 文案改动只动这里，不动组件
// 联系方式为占位，上线前必须替换为真实信息
// ============================================

export const profile = {
  name: '陈雨',
  school: '九江学院 · 软件工程 · 2027 届',
  focus: '关注 Java 后端开发、AI 应用与系统工程。',
  location: 'JIUJIANG, CHINA',
  // TODO(上线前): 替换为真实邮箱
  email: 'hello@example.com',
  // TODO(上线前): 替换为真实 GitHub 地址
  github: '',
}

export const projects = [
  {
    slug: 'yunshu',
    no: '001',
    flagship: true,
    period: '2026.06 — 至今',
    title: '云枢智慧医院管理平台',
    summary:
      '基于 Spring Cloud Alibaba 微服务架构的智慧医院管理平台。我独立负责排班预约服务（his-j8-setroster）后端全部代码，围绕缓存、并发控制、异步通知与 AI 排班助手开展工程实践。',
    tags: ['JAVA', 'SPRING CLOUD', 'REDIS', 'ROCKETMQ', 'SPRING AI'],
    // 性能数据：hey -n 2000 -c 50，本机服务 + dev 虚机中间件，测试环境数据（出处：压测报告.md）
    metrics: [
      { label: 'P99', value: '1.468s → 0.148s' },
      { label: '吞吐', value: '496 → 2480 req/s' },
      { label: '均响', value: '99.3 → 19.7ms' },
    ],
  },
  {
    slug: 'intelnexus',
    no: '002',
    period: '2026',
    title: 'IntelNexus',
    summary:
      'AI 驱动的多源情报分析平台：可信度评估、证据链追溯与冲突检测构成分析内核。独立完成需求定义、方案决策、集成与验证。',
    tags: ['PYTHON', 'LANGCHAIN', 'RAG', 'KNOWLEDGE GRAPH'],
  },
  {
    slug: 'shimmer',
    no: '003',
    period: 'COURSE PROJECT',
    title: '照片光影展示平台',
    summary:
      '围绕照片展示与视觉表达构建的全栈项目，结合图片处理、对象存储与 AI 能力。',
    tags: ['VUE 3', 'EXPRESS', 'MYSQL', 'AI API'],
  },
]

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
