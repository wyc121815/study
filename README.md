# 数据库连接管理平台

一个前后端分离 + 微服务架构的数据库连接管理平台：登录后可以登记、搜索、测试、维护
各个环境的数据库连接，连接密码加密入库，所有操作可追溯到具体用户。

## 架构

```
                       浏览器
                          │
              ┌───────────┴───────────┐
              │  web（Nginx 容器）      │  静态资源 + /api 反代
              └───────────┬───────────┘
                          │
                 gateway-service :8080      ← 唯一入口
                 · JWT 校验    · 路由        · 跨域
                 · traceId     · 注入用户身份
                          │
        ┌─────────────────┴─────────────────┐
        ▼                                   ▼
  auth-service :8081                  conn-service :8082
  （公共服务）                          （业务服务）
  登录 / 签发 JWT / 用户信息             连接配置 CRUD / 连通性测试
        │                                   │  Feign 调用取创建人昵称
        │                                   │
        └───────────────┬───────────────────┘
                        ▼
    nacos（注册中心）  redis（令牌吊销）  mysql（platform_auth / platform_conn）

公共依赖库（JAR，不是独立进程）：common-core · common-security · common-web
```

## 模块

| 模块 | 类型 | 说明 |
| --- | --- | --- |
| `gateway-service` | 独立进程 | 网关：路由、JWT 校验、跨域、traceId、用户身份注入 |
| `auth-service` | 独立进程 | 公共服务：登录、签发 JWT、用户信息查询 |
| `conn-service` | 独立进程 | 业务服务：数据库连接管理、连通性测试 |
| `common-core` | JAR | 统一返回体、错误码、业务异常、AES 加解密 |
| `common-security` | JAR | JWT 签发与校验（纯逻辑，WebFlux/Servlet 都能用） |
| `common-web` | JAR | 全局异常处理、traceId 过滤器、登录用户上下文、Feign 透传 |
| `frontend` | 静态资源 | React 18 + Vite + TypeScript |

## 技术选型

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 21 | |
| Spring Boot | 3.5.16 | |
| Spring Cloud | 2025.0.3 | 必须与 Boot 版本对齐 |
| Spring Cloud Alibaba | 2025.0.0.0 | 提供 Nacos 注册中心支持 |
| Nacos | 2.5.1 | 注册中心（standalone，内嵌存储） |
| MySQL | 8.4 | 两个库：`platform_auth`、`platform_conn` |
| Redis | 7 | 访问令牌吊销名单（jti 黑名单 + 用户级水位）与登录限流计数 |
| JJWT | 0.12.6 | JWT 签发/校验 |
| React / Vite / TS | 18 / 5 / 5.6 | react-router-dom 7 做路由 |

> 这三个 Spring 版本号是一条链，改动前先查官方兼容矩阵，不要单独升级其中一个。

## 快速开始

### 0. 环境要求

- JDK 21（`JAVA_HOME` 指向即可，Maven 用仓库自带的 `mvnw`）
- Node.js 18+
- Docker Desktop（用来跑 MySQL、Nacos 和 Redis）

### 1. 启动基础设施

```powershell
docker compose up -d          # MySQL + Nacos + Redis
docker compose ps
```

镜像默认走公共镜像源（国内直连 Docker Hub 会超时）。能直连的话：

```powershell
$env:MYSQL_IMAGE='mysql:8.4'; $env:NACOS_IMAGE='nacos/nacos-server:v2.5.1'; docker compose up -d
```

> **本机有两处端口改动**，写在 `.env` 里（该文件不入库）：
>
> | 服务 | 默认 | 本机 | 原因 |
> | --- | --- | --- | --- |
> | MySQL | 3306 | **3307** | 3306 已被本机 MySQL80 服务占用 |
> | Nacos | 8848 | **18848** | 8848 落在 Windows/Hyper-V 保留端口段 8830-8929 |
> | Redis | 6379 | 6379 | 本机空闲，未改动；如冲突可用 `.env` 里的 `REDIS_PORT` 覆盖 |
>
> 启动应用时要和 `.env` 保持一致，下面的脚本已经处理好了。

> Redis 是鉴权关键路径（每个带令牌的请求都要查一次吊销名单）。它挂了网关
> 默认按"未吊销"放行并记错误日志（`AUTH_FAIL_OPEN=false` 可改成直接拒绝），
> 此时登出/改密要等访问令牌自然过期才彻底生效。

### 2. 构建并启动后端

```powershell
.\mvnw.cmd -B package                    # 编译 + 单元测试
.\scripts\start-services.ps1             # 三个服务后台启动
```

如果 Nacos 没起、想直接跑，加 `-Local`：

```powershell
.\scripts\start-services.ps1 -Local       # 网关直连各服务，不依赖注册中心
```

查看/停止：

```powershell
Get-Content logs\auth-service.log -Wait   # 日志（带 traceId）
.\scripts\stop-services.ps1
```

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>，用初始账号登录：

| 用户名 | 密码 |
| --- | --- |
| `admin` | `admin123` |

> 账号由 auth-service 首次启动时自动创建，可用 `APP_ADMIN_USERNAME` /
> `APP_ADMIN_PASSWORD` 覆盖。生产环境请务必改掉默认密码。

## 接口

所有接口经网关访问（`http://localhost:8080`），除 `login` / `refresh` 外都需要
`Authorization: Bearer <token>` 头。访问令牌过期时前端会自动调用 `refresh` 续期，
失败才退回登录页。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回访问令牌 + 刷新令牌（免登录） |
| POST | `/api/auth/refresh` | 用刷新令牌换新令牌，旧令牌立即作废（免登录） |
| POST | `/api/auth/logout` | 登出，服务端作废刷新令牌 |
| POST | `/api/auth/password` | 修改当前用户密码，成功后吊销其它会话 |
| GET | `/api/auth/me` | 当前登录用户 |
| GET | `/api/users/{id}` | 用户信息（供服务间调用） |
| GET | `/api/users` | 用户列表（仅 ADMIN） |
| GET | `/api/db-types` | 支持的数据库类型列表 |
| GET | `/api/connections` | 连接列表，支持 `keyword` / `dbType` / `page` / `size` |
| GET | `/api/connections/{id}` | 连接详情 |
| POST | `/api/connections` | 新建连接（仅 ADMIN） |
| PUT | `/api/connections/{id}` | 更新连接（`password` 留空表示不改，仅 ADMIN） |
| DELETE | `/api/connections/{id}` | 删除连接（仅 ADMIN） |
| POST | `/api/connections/test` | 用表单参数直接试连，不落库 |
| POST | `/api/connections/{id}/test` | 对已保存连接试连并记录结果 |
| GET | `/actuator/health` | 健康检查 |

所有响应统一为：

```json
{ "code": 0, "message": "成功", "data": {}, "traceId": "..." }
```

`code = 0` 表示成功；`401xx` 登录相关，`4xxxx` 请求错误，`5xxxx` 服务端错误。

## 安全设计

| 关注点 | 做法 |
| --- | --- |
| 认证 | 登录签发 HS256 访问令牌（默认 30 分钟），网关统一校验；另有 7 天刷新令牌负责续期 |
| 会话生命周期 | 刷新令牌存在 `auth_refresh_token` 表（只存哈希），每次用完即轮换；登出、改密、检测到重放都会吊销 |
| 即时吊销 | 访问令牌的 `jti` 登出时写入 Redis 黑名单；改密写用户级吊销水位，此前签发的令牌立即失效（不依赖令牌自然过期） |
| 登录限流 | 网关按来源 IP 做固定窗口限流（默认 60 秒 20 次），Redis 计数；与账号级锁定互补 |
| 身份传递 | 网关注入 `X-User-Id` / `X-Username` / `X-User-Role`，下游读头即可 |
| 信任边界 | 业务服务的所有 `/api/**` 都必须带 `X-Internal-Token`（只有网关和服务间 Feign 持有），否则直接 401——绕过网关直连 `:8081`/`:8082` 拿不到任何数据；身份头也只有带上它才被采信。仅 `login`/`refresh`/`logout` 例外，名单见 `app.internal.permit-paths` |
| 授权 | `@RequireRole` 做角色校验：增删改连接与用户列表要求 ADMIN，其余只读 |
| 登录防爆破 | 连续失败 5 次锁定 15 分钟；用户不存在时也做一次 bcrypt 比较，抹平时序差异 |
| 密码存储 | BCrypt 哈希，接口永不返回哈希 |
| 密码修改 | `POST /api/auth/password` 校验原密码 + 强度（≥8 位且含字母数字），改完吊销其它会话 |
| 连接密码 | AES-256-GCM 加密入库，接口只返回脱敏值（`p******3`） |
| 账号枚举 | 用户不存在与密码错误返回同一个提示，且耗时一致 |
| 链路追踪 | 网关生成 `X-Trace-Id`，日志格式 `%X{traceId}` 全链路可串 |

> **`APP_CRYPTO_KEY` 一旦上线就不能更换**，换了以后已保存的连接密码无法解密。
> 同理，`APP_JWT_SECRET` 变更会让所有已签发的 token 失效。
> `APP_INTERNAL_SECRET` 变更会让网关与业务服务之间对不上，需三处同时更新。

## 数据库

建库建表脚本在 `deploy/mysql/init/01-schema.sql`，MySQL 容器首次启动时自动执行。
两个库分属两个服务，服务之间不跨库直接连表，只通过接口获取对方数据。

改了表结构需要重建数据卷（会清空数据）：

```powershell
docker compose down -v
docker compose up -d
```

> 不想清库的存量环境，用 `deploy/mysql/migrations/` 下对应的幂等迁移脚本补字段：
>
> ```powershell
> Get-Content deploy\mysql\migrations\2026-10-07-auth-hardening.sql |
>   docker exec -i conn-platform-mysql mysql -uroot -proot123456
> ```

## 常见命令

```powershell
.\mvnw.cmd -B test                       # 只跑单元测试
.\mvnw.cmd -B -DskipTests package        # 跳过测试打包
cd frontend; npm run lint                # ESLint
cd frontend; npm run build               # 类型检查 + 生产构建
```

## 部署

生产部署走「发布包 + Docker Compose」，流程见 `.github/workflows/deploy.yml`：

1. CI 构建三个 jar 与前端 `dist`，组装成发布包（`backend/`、`web/`、
   `docker-compose.yml`、`Dockerfile`、`nginx/`、`init/`）；
2. 生成 `.env`（密钥来自 GitHub Secrets），连同 `release.sh` 上传到服务器；
3. 服务器解包到 `/srv/study/releases/<时间戳>/`，切换 `current` 软链；
4. `docker compose up -d --build` 起整栈，健康检查失败自动回滚上一版本。

密钥集中存放在 `/srv/study/shared/.env`（权限 600），各版本软链引用。

### 首次初始化服务器

```bash
scp -r deploy ubuntu@<host>:/tmp/study-deploy
ssh ubuntu@<host> 'bash /tmp/study-deploy/server-setup.sh'
```

### 本地演练生产编排

不用真上服务器，本地就能把生产那套跑一遍：

```powershell
.\mvnw.cmd -B package
cd frontend; npm run build; cd ..
.\scripts\build-release-bundle.ps1
cd build\release
docker compose up -d --build
```

### 需要的 GitHub Secrets

| 名称 | 说明 |
| --- | --- |
| `DEPLOY_SSH_KEY` | 部署私钥 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_PASSWORD` | 应用库账号密码 |
| `APP_JWT_SECRET` | JWT 密钥，base64，解码 ≥32 字节 |
| `APP_CRYPTO_KEY` | 连接密码加密密钥，base64，解码恰好 32 字节 |
| `APP_INTERNAL_SECRET` | 网关↔业务服务内部共享密钥，base64，解码 ≥32 字节 |
| `APP_ADMIN_PASSWORD` | 初始管理员密码 |

生成两个密钥：

```powershell
.\scripts\generate-secrets.ps1
```

### 容量说明

整栈常驻内存约 2.5-3.5G，**2C2G 的机器跑不动**。当前服务器是 2C2G，
上生产前需要先升配到 4C8G，或者：把 Nacos 换成外部实例、用
`-Local` 模式省掉注册中心、把 MySQL 换成外部实例。

## 目录结构

```
.
├─ pom.xml                     聚合 POM
├─ common/                     公共依赖库
│  ├─ common-core/
│  ├─ common-security/
│  └─ common-web/
├─ gateway-service/            网关
├─ auth-service/               公共服务
├─ conn-service/               业务服务
├─ frontend/                   React 前端
├─ docker-compose.yml          本地开发基础设施（MySQL + Nacos + Redis）
├─ deploy/
│  ├─ docker-compose.yml       生产编排
│  ├─ Dockerfile               三个服务共用
│  ├─ nginx/nginx.conf
│  ├─ mysql/init/01-schema.sql
│  ├─ release.sh               服务器端发布/回滚
│  └─ server-setup.sh          服务器一次性初始化
└─ scripts/                    本地开发脚本
```
