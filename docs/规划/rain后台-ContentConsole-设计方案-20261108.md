# rain 后台管理系统 · Content Console 设计方案

> 状态：**已决策，待开工**（2026-11-08 用户拍板）
> 配套文档：`rain后台-FullConsole-待推进-20261108.md`（二期功能，不实施）

## 1. 背景与目标

rain 作品集站点的内容目前全部硬编码在 `src/data/*.js` 里（照片清单、项目详情、站点配置），改一张照片、换一句文案都要动代码、重新构建。Content Console 的目标是把内容维护变成界面操作，同时——

**这个后台本身是作品集的第 4 个项目**：一个真实的 Java 后端 + Vue3 管理台全栈工程，可直接沉淀为简历项目经历（对标 Java 后端岗位）。

## 2. 已决策事项（2026-11-08）

| 决策点 | 结论 |
|---|---|
| 功能范围 | 方案一 Content Console（登录 / 照片管理 / 项目内容 / 发布）；方案二 Full Console 仅沉淀为待推进文档 |
| 后端技术栈 | Spring Boot 3 + Spring Security（单管理员 JWT）+ MySQL 8 |
| 前端管理台 | Vue 3 + Vite，复用前台 `tokens.scss` Klein Blue 设计语言 |
| 存储与发布 | 文件 + 库混合：照片文件存文件系统、元数据/文案存 MySQL；发布时写回 `src/data/*.json` 并触发 `npm run build`，前台保持纯静态 |

## 3. 系统架构

```
G:\Improve\MI\rain\
├── src/...                  # 前台（现有，纯静态，不引入运行时依赖）
├── admin/
│   ├── server/              # Spring Boot 3（端口 8081）
│   │   ├── auth/            # 登录 + JWT 签发/校验（单管理员，账号存库 bcrypt）
│   │   ├── photo/           # 照片上传/压缩/元数据 CRUD
│   │   ├── content/         # 项目详情 sections 读写（JSON 字段存 MySQL）
│   │   └── publish/         # 数据写回 + 构建触发
│   └── ui/                  # Vue3 管理台（复用 tokens.scss，路由 /login /photos /content /publish）
└── docs/规划/               # 本方案 + 二期待推进
```

**发布链路**（核心卖点，面试可讲）：

```
管理台保存 → Spring Boot 校验 → 生成 src/data/photos.js / projects/<slug>.js（模板渲染写回）
         → ProcessBuilder 调 npm run build → dist 产物 → git push → Cloudflare Pages 自动部署
```

## 4. 模块设计

### 4.1 登录鉴权
- 单管理员账号（username + bcrypt hash 存 `admin_user` 表），登录签发 JWT（2h 过期）
- 其余全部接口走 JWT 过滤器；失败统一 401 JSON

### 4.2 照片管理
- **上传**：MultipartFile → Thumbnailator 压缩（长边 1600px / q80，对齐现有前台参数）→ 存 `public/photos/<pid>.jpg` → 读取实际宽高写 `photo` 表（id、原始文件名、拍摄日期、宽、高、分组、排序号、是否头图）
- **管理**：网格展示（按分组）、拖拽排序（写回 sort_no）、修改分组/日期、设为头图（全局唯一）、删除（文件 + 记录，二次确认）
- **一致性**：文件写成功但入库失败 → 删除孤儿文件（try-catch 补偿）；照片编号全局连续规则与前台 `plateNo()` 保持一致

### 4.3 项目内容编辑
- `project_detail` 表：slug、headline、summary、meta JSON、sections JSON
- 管理台按 section 类型渲染对应表单：impl（问题/方案/效果三段式）、metrics（数据卡）、pits、text、shots（截图选择器）、arch（只读提示——架构图是 SVG 组件，不开放编辑）
- 表单校验与前台渲染器字段一一对应，防止存进渲染不出的数据

### 4.4 发布
- 一键"发布"：写回数据文件 → `npm run build` → 返回构建日志与结果
- 写回策略：先写临时文件再原子替换，失败保留旧文件
- 构建日志落库（`publish_log` 表：时间、触发人、结果、耗时），管理台可见历史

## 5. 开发里程碑

| 阶段 | 交付物 | 验证方式 |
|---|---|---|
| M1 脚手架 + 登录 | Spring Boot 工程 + admin-ui 骨架 + JWT 登录闭环 | 登录后进管理台首页 |
| M2 照片管理 | 上传/压缩/网格/排序/头图 全链路 | 上传一张新照片 → 前台摄影页可见 |
| M3 内容编辑 | 项目 sections 表单化编辑 | 改一条文案 → 前台详情页生效 |
| M4 发布链路 | 写回 JSON + 构建 + 日志 | 管理台点发布 → dist 更新 |
| M5 收尾 | 单测（Service 层）+ 简历素材沉淀 | 新增第 4 个项目经历草稿 |

## 6. 开放问题（开工前需定）

1. **MySQL 实例**：复用 VMware 虚机已有 MySQL 还是本机新装？（开发期均可，上线前再定）
2. **Git 托管**：rain 仓库是否推 GitHub？（推荐推——Cloudflare Pages 连 Git 自动部署，发布链路才能真正闭环）
3. **管理台部署形态**：M1–M4 建议仅本地运行（管理台不暴露公网，静态站照常上线）；云端部署 admin 列入二期再议。

## 7. 简历价值点（预沉淀）

- 文件上传与图片压缩管线（Thumbnailator、宽高校验、孤儿文件补偿）
- Spring Security 单管理员 JWT 鉴权体系
- 数据库设计（photo / project_detail / publish_log）与 JSON 字段存取
- 静态站点自动化发布链路（数据写回 + 构建编排）
- 前后端分离 + 设计系统复用（前台/后台同一套 Design Tokens）
