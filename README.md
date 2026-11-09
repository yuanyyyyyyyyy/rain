# rain — Personal Portfolio

个人作品集网站 + 自托管内容管理后台。视觉语言：**Klein Blue v2**——纯白底、克莱因蓝 (#002FA7) 作为标点、工业档案线条、硬投影 hover。

> rain：名字里那场雨，也是蓝调时刻（blue hour）前后的天色。

## 站点结构

| 路由 | 页面 | 状态 |
|---|---|---|
| `/` | 首页（Hero 几何主视觉 / Selected Works / Capabilities / Beyond Code / Contact） | ✅ |
| `/projects/yunshu` | 云枢智慧医院管理平台 — 档案式 case study（07 节） | ✅ 正文完成，界面截图待补 |
| `/projects/intelnexus` | IntelNexus 多源情报分析平台 — case study（07 节） | ✅ 正文完成，界面截图待补 |
| `/projects/forge` | Forge — Agent Harness（多入口统一 Runtime） | ✅ 2026-11-10 上线 |
| `/projects/rain` | Rain — 本站（作品集 + 自托管管理台） | ✅ 2026-11-10 上线，含界面实录截图 |
| `/projects/shimmer` | 照片光影平台 | ⏳ 骨架，素材待接入 |
| `/about` | About | ⏳ 骨架 |
| `/photography` | 摄影页 — 对齐行印样 + Lightbox，30 张真实照片 | ✅ |

## 目录结构

```
rain/
├── src/
│   ├── styles/tokens.scss     # 设计 Token 单文件（色彩/字体/布局变量）
│   ├── data/site.js           # 站点内容数据（文案与组件分离；apiBase 指向公网 Worker）
│   ├── data/projects/*.js     # 详情页分节数据（数据驱动渲染）
│   ├── data/photos.js         # 摄影页分组/编号/宽高比
│   ├── components/
│   │   ├── layout/            # SiteHeader / SiteFooter
│   │   ├── home/              # Hero / Projects / Capabilities / Beyond / Contact
│   │   └── project/           # 架构图 SVG（云枢 / IntelNexus 管线）
│   └── pages/                 # Home / ProjectDetail（通用分节渲染器）/ About / Photography
├── public/photos/             # 网页版照片（长边 1600px / q80，原图另存）
├── cloudflare/                # 公网收集端：Cloudflare Worker + D1（埋点/表单/简历计数）→ cloudflare/README.md
├── tools/                     # sync-via-api.py（被墙时经 GitHub API 重放推送）→ tools/README.md
├── .github/workflows/         # deploy.yml（GitHub Pages 自动部署）+ verify-collector.yml（Worker E2E）
├── admin/
│   ├── server/                # 后端：Spring Boot 3 + Security JWT + MySQL → admin/server/README.md
│   └── ui/                    # 管理台：Vue 3 + Vite（复用 Klein Blue tokens）→ admin/ui/README.md
└── docs/
    ├── _索引.md               # 文档总索引
    ├── 素材/                   # 素材需求清单、素材回答、照片清单 JSON
    ├── 规划/                   # 后台设计方案、审计修复记录
    └── 交接/                   # agent 交接与项目认知文档
```

## 快速开始

```bash
npm install
npm run dev        # 前台 http://localhost:5173（埋点/表单直连线上 Worker）
npm run build      # 产物在 dist/（纯静态）
npm run preview    # 本地预览构建产物
```

线上部署：**GitHub Pages**（push main → Actions `deploy.yml` 自动构建，base=/rain/）。

后台（可选，内容管理用）：

```bash
cd admin/server && mvn spring-boot:run   # 后端 :8081，需先配置 application-local.yml
cd admin/ui && npm install && npm run dev # 管理台 :5175
```

详见 `admin/server/README.md`。

## 技术栈

- **前台**：Vue 3.5 + Vite 6 + Vue Router 4 + Sass，无 UI 组件库（保持线条风格可控）；字体自托管（@fontsource）
- **摄影页**：对齐行排版（justified rows，flex-grow = 宽高比）、懒加载、自研 Lightbox（键盘翻页）
- **后台**：Spring Boot 3.3.5 + Spring Security + JPA + MySQL 8 / Vue 3 管理台
- **公网收集端**：Cloudflare Worker + D1（埋点 / 联系表单 / 简历下载计数，`/api/` 前缀兼容 Spring 契约）→ `cloudflare/`
- **发布机制**：管理台编辑内容 → 写回 `src/data/*.js` → 触发构建，前台保持纯静态

## 内容维护

改文案 / 数据不需要碰组件：

| 想改什么 | 改哪里 |
|---|---|
| 导航、联系方式、首页卡片 | `src/data/site.js`（projects 数组是发布产物，勿手改锚点区） |
| 项目详情页章节 | 管理台「03 项目内容」（发布自动写回 `src/data/projects/<slug>.js`，勿手改） |
| 摄影分组、顺序、头图、图注 | 管理台「02 照片管理」上传/排序/设头图（发布自动写回 `src/data/photos.js`） |
| 主题色 / 字体 / 间距 | `src/styles/tokens.scss` |
| 管理台密码 / 公网数据源 | 管理台「06 / 设置」改密 + 配置 Worker 地址与 ADMIN_TOKEN |

> 联系方式已接入真实信息（2026-11-09）：邮箱见 `site.js`；简历 PDF 为 `public/resume.pdf`（Hero"下载简历"按钮直链 + Worker 下载计数）；GitHub 暂不展示（`github: ''` 留空即自动隐藏按钮）。

## Git 约定

- 分批提交，一个功能一个 commit：`feat: / chore: / docs: / fix:`
- 敏感凭据只放 `application-local.yml`（已 gitignore），仓库内留 `.example` 模板
- 文档进 `docs/` 并登记 `_索引.md`，命名 `主题-内容-YYYYMMDD.md`

