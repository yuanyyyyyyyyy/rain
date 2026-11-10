// 稳定的数据适配层，不随发布改动。
// 内容由管理台「04 发布」写入 content/ 下的 JSON；本文件只负责把 JSON 映射成组件使用的具名导出。
// 站点级静态配置（姓名/邮箱/能力介绍）在 content/site.json，项目卡片在 content/projects.json。
import site from '../../content/site.json'
import projectCards from '../../content/projects.json'

export const { apiBase, profile, capabilities, turnstileSiteKey } = site
export const projects = projectCards
