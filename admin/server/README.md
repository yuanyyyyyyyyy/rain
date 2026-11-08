# rain-admin-server

rain 作品集的内容管理后端。Spring Boot 3 单体服务，单管理员 + JWT 鉴权，数据存 MySQL。

## 技术栈

- Spring Boot 3.3.5（Java 17）
- Spring Security + jjwt（HS384，token 有效期 120 分钟）
- Spring Data JPA + MySQL 8（`ddl-auto: update`，开发期自动建表）
- 端口 **8081**

## 模块结构

```
src/main/java/rain/admin/
├── RainAdminApplication.java   # 启动类
├── user/
│   ├── AdminUser.java          # 管理员实体（bcrypt 密码）
│   ├── AdminUserRepo.java
│   └── AdminUserSeeder.java    # 首次启动种子默认管理员 admin/admin123
├── auth/
│   ├── JwtService.java         # 签发 / 校验（HS384，密钥长度决定）
│   ├── JwtAuthFilter.java      # 解析 Authorization: Bearer；无 @Component，
│   │                           # 由 SecurityConfig 手动注册（避免双重注册）
│   └── AuthController.java     # POST /api/auth/login
├── photo/                      # M2 照片管理
│   ├── Photo.java              # 实体（pid 唯一 / 宽高 / 分组 / 排序 / 头图）
│   ├── PhotoRepo.java
│   ├── PhotoService.java       # 上传压缩（长边 1600/q80 只缩不放）、孤儿文件补偿、
│   │                           # 头图全局唯一（实体清零+置一，避开 bulk update 一级缓存坑）
│   └── PhotoController.java    # REST（见下方 API 表）
└── config/
    ├── SecurityConfig.java     # 安全链 + CORS（管理台 dev 端口白名单）+ /photos/** permitAll
    └── WebConfig.java          # /photos/** 静态映射到前台 public/photos/（管理台 img 免代理）
```

## 本地启动

1. 建库（或复用已有实例）：

```sql
CREATE DATABASE rain_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rain'@'localhost' IDENTIFIED BY '<密码>';
GRANT ALL PRIVILEGES ON rain_admin.* TO 'rain'@'localhost';
```

2. 复制 `src/main/resources/application-local.yml.example` 为同目录 `application-local.yml`，填入数据源与 JWT secret（至少 32 字节）。该文件已 gitignore，**不进仓库**。

3. 运行：

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -Dserver.port=8081 -jar target/rain-admin-server-0.1.0.jar
```

> 显式指定 `-Dserver.port`：本机若有 `SERVER_PORT` 环境变量会覆盖 application.yml。

## API

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | 公开 | `{username, password}` → `{token}` |
| GET | `/photos/**` | 公开 | 照片静态文件（映射前台 `public/photos/`） |
| GET | `/api/photos` | Bearer | 全量列表（分组+组内排序） |
| POST | `/api/photos/upload` | Bearer | multipart：`file` + `groupName`(STREET/LIGHT/DAILY) + `shotDate`(可空) → 压缩落盘 `public/photos/pNN.jpg` + 入库；响应含 `snippet`（photos.js 两行片段，M2 半自动过渡） |
| PUT | `/api/photos/{id}` | Bearer | `{groupName, shotDate, sortNo}` 全字段更新 |
| PUT | `/api/photos/{id}/hero` | Bearer | 设为头图（全局唯一，旧头图自动取消） |
| DELETE | `/api/photos/{id}` | Bearer | 删记录（事务）+ 删文件（失败以 `warning` 提示人工清理） |

无 token / token 无效 → `401 {"error":"unauthorized"}`；`/error` 转发路径已 permitAll（否则 404 会被安全链拦成 401，掩盖真实错误码）。

照片初始数据：`src/main/resources/db/seed-photo.sql`（现有 30 张，INSERT IGNORE 幂等），mysql CLI 执行一次。

## 默认账号

`admin / admin123`（首次启动自动种子）。**上线前必须改密**。

## 已知踩坑（开发记录）

1. **JWT 过滤器双重注册**：`JwtAuthFilter` 若标 `@Component`，Servlet 容器自动注册一次 + `addFilterBefore` 再注册一次，`OncePerRequestFilter` 的 already-filtered 标记导致安全链内那次被跳过 → 所有请求 401。修法：去掉 `@Component`，在 SecurityConfig 内手动实例化。
2. **`/error` 丢认证上下文**：受保护路径 404 后 Spring 转发 `/error`，转发请求不带认证，被安全链拦成 401。修法：`/error` 加入 permitAll。
3. **种子 SQL 漏列写出零日期**：photo 种子未提供 `created_at`（NOT NULL 无默认），mysql CLI 导入时该列被写成 `0000-00-00`（导入会话未按严格模式拦截），Connector/J 读取即抛 `Zero date value prohibited` → 查询 500。修法：种子显式提供 `created_at` 列；教训——**INSERT 种子必须覆盖全部 NOT NULL 列，不能依赖隐式默认**。
4. **运行中的 jar 锁定 target**：Windows 下后端进程运行时 `mvn package` 无法覆盖 jar（spring-boot repackage 失败）。修法：打包前先停后端进程。
5. **空表时 pid 生成与现有文件冲突**：photo 表为空时 `maxPidNumber()+1` 会生成 `p01`，直接覆盖前台现有照片。修法：上传前检查目标文件存在即拒绝（pid 冲突防御），且种子导入先于上传。

## 里程碑

- [x] M1 脚手架 + JWT 登录闭环
- [x] M2 照片管理（上传压缩 / photo 表 / 列表·更新·删除·设头图 / 静态映射 / 管理台照片页）
- [ ] M3 项目内容编辑（sections 类型化表单）
- [ ] M4 发布链路（写回 `src/data/*.json` + 触发前台构建）
- [ ] M5 测试收尾
