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
│   ├── JwtService.java         # 签发 / 校验
│   ├── JwtAuthFilter.java      # 解析 Authorization: Bearer；无 @Component，
│   │                           # 由 SecurityConfig 手动注册（避免双重注册）
│   └── AuthController.java     # POST /api/auth/login
└── config/
    └── SecurityConfig.java     # 安全链 + CORS（管理台 dev 端口白名单）
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
| /api/** 其余 | — | Bearer JWT | M2 起逐步实现（photos / content / publish） |

无 token / token 无效 → `401 {"error":"unauthorized"}`；`/error` 转发路径已 permitAll（否则 404 会被安全链拦成 401，掩盖真实错误码）。

## 默认账号

`admin / admin123`（首次启动自动种子）。**上线前必须改密**。

## 已知踩坑（开发记录）

1. **JWT 过滤器双重注册**：`JwtAuthFilter` 若标 `@Component`，Servlet 容器自动注册一次 + `addFilterBefore` 再注册一次，`OncePerRequestFilter` 的 already-filtered 标记导致安全链内那次被跳过 → 所有请求 401。修法：去掉 `@Component`，在 SecurityConfig 内手动实例化。
2. **`/error` 丢认证上下文**：受保护路径 404 后 Spring 转发 `/error`，转发请求不带认证，被安全链拦成 401。修法：`/error` 加入 permitAll。

## 里程碑

- [x] M1 脚手架 + JWT 登录闭环
- [ ] M2 照片管理（Thumbnailator 压缩长边 1600 / photo 表 / 网格管理接口）
- [ ] M3 项目内容编辑（sections 类型化表单）
- [ ] M4 发布链路（写回 `src/data/*.json` + 触发前台构建）
- [ ] M5 测试收尾
