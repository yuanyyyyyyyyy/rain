# rain-admin-ui

rain 作品集内容管理台。Vue 3 + Vite，复用前台 Klein Blue v2 设计 Token（`src/styles/tokens.scss` 与前台同源），蓝底白字侧栏布局。

## 启动

```bash
npm install
npm run dev      # http://localhost:5175（端口被占时 Vite 自动 +1，注意后端 CORS 白名单）
npm run build
```

开发模式经 Vite proxy 转发 `/api` 到 `http://localhost:8081`（见 `vite.config.js`），无需处理跨域；直连后端调试时后端 CORS 需包含本页端口。

## 页面

| 路由（hash 模式） | 页面 | 状态 |
|---|---|---|
| `#/login` | 登录（admin / admin123） | ✅ |
| `#/` | 01 概览 Dashboard | 占位 |
| `#/photos` | 02 照片管理：上传（自动压缩）+ photos.js 片段复制 / 三组网格 / 组内拖拽排序 / 行内编辑 / 设头图 / 删除二次确认 | ✅ |
| `#/content` | 03 内容编辑 | 占位，M3 实现 |
| `#/publish` | 04 发布 | 占位，M4 实现 |

## 鉴权

- 登录成功后 token 存 `localStorage`（key: `rain_admin_token`），`src/api.js` 统一注入 `Authorization: Bearer` 头
- 任何请求返回 401 → 清除 token 并跳转 `#/login`（fetch 封装内处理）
- 路由守卫（`router/index.js`）：未登录访问受保护页 → 重定向 `#/login`

## 结构

```
src/
├── api.js              # 统一 fetch 封装：token 注入 + 401 处理 + multipart postForm（无 axios 依赖）
├── router/index.js     # hash 模式路由 + 登录守卫
├── styles/tokens.scss  # Klein Blue v2 设计 Token（与前台同源）
└── pages/
    ├── LoginPage.vue
    ├── LayoutPage.vue  # 侧栏布局（01-04 菜单 + 退出）
    └── Dashboard/Photos/Content/PublishPage.vue
```

照片缩略图经 Vite proxy `/photos` → 后端静态映射（后端 `/photos/**` 直指前台 `public/photos/`），dev 与生产同路径。
