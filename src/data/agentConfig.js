/**
 * AI 分身前台配置（2026-11-15 P0）。
 * 放代码层而非 content/：这是功能开关与文案，不是内容——content/site.json 由管理台
 * 发布管道（SiteDataGenerator）生成，手改会被覆盖；功能下线直接 enabled: false 或
 * Worker 端 AGENT_ON = "0"（双闸，见 cloudflare/wrangler.toml 注释）。
 */
export const agentConfig = {
  enabled: true,
  name: 'RAIN · AI 分身',
  fabLabel: 'AI',
  greeting:
    '我是陈雨的 AI 分身，基于站内项目档案回答问题。想了解某个项目的技术细节、他的技术栈或协作方式，直接问就好。',
  suggestions: ['介绍一下云翼项目', '她有哪些技术栈？', 'Forge 是做什么的', '怎么联系她？'],
  inputPlaceholder: '问点什么…（Enter 发送）',
  disclaimer: 'AI 分身生成，仅供参考 · 重要信息请以站内联系方式为准',
  offlineHint: '分身暂时不在线，请直接邮件联系：',
}
