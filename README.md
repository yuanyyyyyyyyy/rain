# rain — Personal Portfolio

个人作品集网站 + 自托管内容管理后台。视觉语言：**Klein Blue v2**——纯白底、克莱因蓝 (#002FA7) 作为标点、工业档案线条、硬投影 hover。

> rain：名字里那场雨，也是蓝调时刻（blue hour）前后的天色。

## 站点结构

| 路由 | 页面 | 状态 |
|---|---|---|
| `/` | 首页（Hero 几何主视觉 / Selected Works / Capabilities / Beyond Code / Contact） | ✅ |
| `/projects/yunshu` | 云枢智慧医院管理平台 — 档案式 case study（07 节） | ✅ 正文完成，界面截图待补 |
| `/projects/intelnexus` | IntelNexus 多源情报分析平台 — case study（07 节） | ✅ 正文完成，界面截图待补 |
| `/projects/shimmer` | 照片光影平台 | ⏳ 骨架，素材待接入 |
| `/about` | About | ⏳ 骨架 |
| `/photography` | 摄影页 — 对齐行印样 + Lightbox，30 张真实照片 | ✅ |

## 目录结构

```
rain/
├── src/
│   ├── styles/tokens.scss     # 设计 Token 单文件（色彩/字体/布局变量）
│   ├── data/site.js           # 站点内容数据（文案与组件分离）
│   ├── data/projects/*.js     # 详情页分节数据（数据驱动渲染）
│   ├── data/photos.js         # 摄影页分组/编号/宽高比
│   ├── components/
│   │   ├── layout/            # SiteHeader / SiteFooter
│   │   ├── home/              # Hero / Projects / Capabilities / Beyond / Contact
│   │   └── project/           # 架构图 SVG（云枢 / IntelNexus 管线）
│   └── pages/                 # Home / ProjectDetail（通用分节渲染器）/ About / Photography
├── public/photos/             # 网页版照片（长边 1600px / q80，原图另存）
├── admin/
│   ├── server/                # 后端：Spring Boot 3 + Security JWT + MySQL → admin/server/README.md
│   └── ui/                    # 管理台：Vue 3 + Vite（复用 Klein Blue tokens）→ admin/ui/README.md
└── docs/
    ├── _索引.md               # 文档总索引
    ├── 素材/                   # 素材需求清单、素材回答、照片清单 JSON
    └── 规划/                   # 后台设计方案（Content Console / Full Console 待推进）
```

## 快速开始

```bash
npm install
npm run dev        # 前台 http://localhost:5173
npm run build      # 产物在 dist/（纯静态，可部署 Cloudflare Pages / Vercel / Nginx）
npm run preview    # 本地预览构建产物
```

后台（可选，内容管理用）：

```bash
cd admin/server && mvn spring-boot:run   # 后端 :8081，需先配置 application-local.yml
cd admin/ui && npm install && npm run dev # 管理台 :5175
```

详见 `admin/server/README.md`。

## 技术栈

- **前台**：Vue 3.5 + Vite 6 + Vue Router 4 + Sass，无 UI 组件库（保持线条风格可控）
- **摄影页**：对齐行排版（justified rows，flex-grow = 宽高比）、懒加载、自研 Lightbox（键盘翻页）
- **后台**：Spring Boot 3.3.5 + Spring Security + JPA + MySQL 8 / Vue 3 管理台
- **发布机制**：管理台编辑内容 → 写回 `src/data/*.json` → 触发构建，前台保持纯静态

## 内容维护

改文案 / 数据不需要碰组件：

| 想改什么 | 改哪里 |
|---|---|
| 导航、联系方式、首页卡片 | `src/data/site.js` |
| 项目详情页章节 | `src/data/projects/<slug>.js` |
| 摄影分组、顺序、头图 | `src/data/photos.js`，或管理台 `#/photos` 上传/排序/设头图（新照片按片段提示接进 photos.js） |
| 主题色 / 字体 / 间距 | `src/styles/tokens.scss` |

> 联系方式（邮箱 / GitHub / 简历 PDF）当前为占位，上线前必须替换，代码内标注 `TODO(上线前)`。

## Git 约定

- 分批提交，一个功能一个 commit：`feat: / chore: / docs: / fix:`
- 敏感凭据只放 `application-local.yml`（已 gitignore），仓库内留 `.example` 模板
- 文档进 `docs/` 并登记 `_索引.md`，命名 `主题-内容-YYYYMMDD.md`
