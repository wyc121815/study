# 数据库连接管理平台

一个前后端分离 + 微服务架构的数据库连接管理平台：登录后可以登记、搜索、测试、维护
各个环境的数据库连接，在已保存的连接上直接执行只读 SQL，连接密码加密入库，
所有操作可追溯到具体用户。指标查询平台的演进方案见
[docs/指标查询平台设计.md](docs/指标查询平台设计.md)。

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
    nacos（注册中心）  redis（令牌吊销 / 指标缓存 / 并发额度）
    kafka（批量导出队列）  mysql（platform_auth / platform_conn / platform_demo）

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
| Redis | 7 | 网关：令牌吊销名单 + 登录限流；conn-service：指标结果缓存、热度排行、数据源并发额度 |
| Kafka | 3.9（KRaft） | 批量导出任务队列，单节点即可，不需要 ZooKeeper |
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
docker compose up -d          # MySQL + Nacos + Redis + Kafka
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

#### 本地起多实例

`conn-service` 的副本数以同一个服务名注册到 Nacos，网关按 `lb://conn-service`
做轮询，用来验证"额度全局共享、锁跨实例、队列重平衡"这些多实例行为：

```powershell
.\scripts\start-services.ps1 -ConnReplicas 2
# 已启动 auth-service (:8081) 日志=auth-service.log
# 已启动 conn-service (:8082) 日志=conn-service.log
# 已启动 conn-service (:8083) 日志=conn-service-2.log
# 已启动 gateway-service (:8080) 日志=gateway-service.log
```

起始端口可用 `-ConnBasePort` 调整。查看注册情况：

```powershell
(Invoke-RestMethod 'http://127.0.0.1:18848/nacos/v1/ns/instance/list?serviceName=conn-service').hosts |
  Select-Object ip, port, healthy
```

注意两点：

- **必须用默认的 nacos 模式**（不要加 `-Local`）。`-Local` 下网关直连固定地址，
  只会打到第一个副本。
- 每个副本的日志是独立的（`conn-service.log` / `conn-service-2.log`），
  排查"这个请求到底落在哪个实例"就对比两边日志里的 traceId。

本地实测结论（2 副本、每库并发上限 4）：

| 场景 | 结果 |
| --- | --- |
| 串行 10 个请求 | 8082 / 8083 各 5 个，轮询生效 |
| 并发 8 个慢查询 | 只放行 4 个、拒绝 4 个（若额度是每实例一份，应该是 8 个全过） |
| 清缓存后并发 10 个相同指标 | 实际只查库 1 次（Redis 抢占锁跨实例生效） |
| 提交 4 个导出任务 | Kafka 消费组 2 个成员分摊分区，4 个全部完成 |

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
| GET | `/api/users` | 用户列表，支持 `keyword` / `page` / `size`（仅 ADMIN） |
| POST | `/api/users` | 新建用户（仅 ADMIN） |
| PUT | `/api/users/{id}` | 修改昵称 / 角色 / 状态（仅 ADMIN） |
| POST | `/api/users/{id}/password` | 重置指定用户密码（仅 ADMIN） |
| GET | `/api/db-types` | 支持的数据库类型列表 |
| GET | `/api/datasources` | 开放查询的数据源（所有登录用户） |
| GET | `/api/connections` | 连接列表，支持 `keyword` / `dbType` / `page` / `size` |
| GET | `/api/connections/{id}` | 连接详情 |
| POST | `/api/connections` | 新建连接（仅 ADMIN） |
| PUT | `/api/connections/{id}` | 更新连接（`password` 留空表示不改，仅 ADMIN） |
| DELETE | `/api/connections/{id}` | 删除连接（仅 ADMIN） |
| POST | `/api/connections/test` | 用表单参数直接试连，不落库 |
| POST | `/api/connections/{id}/test` | 对已保存连接试连并记录结果 |
| POST | `/api/queries/execute` | 在指定连接上执行单条只读 SQL |
| POST | `/api/queries/export` | 导出查询结果为 CSV |
| GET | `/api/queries/history` | 查询历史，`scope=mine\|all`（all 仅 ADMIN） |
| DELETE | `/api/queries/history/{id}` | 删除一条查询历史 |
| DELETE | `/api/queries/history` | 清空查询历史，支持 `scope=mine\|all` |
| GET | `/api/metrics` | 指标列表（所有登录用户） |
| POST | `/api/metrics` | 把 SQL 存为指标（所有登录用户） |
| PUT | `/api/metrics/{id}` | 修改指标（创建人或 ADMIN） |
| DELETE | `/api/metrics/{id}` | 删除指标（创建人或 ADMIN） |
| POST | `/api/metrics/{id}/run` | 执行指标 |
| GET | `/api/metrics/ranking` | 指标热度排行（次数存在 Redis） |
| POST | `/api/metrics/{id}/export` | 导出指标结果为 CSV |
| POST | `/api/exports` | 提交批量导出任务（连接+SQL 或指标 ID），立即返回任务号 |
| GET | `/api/exports` | 导出任务列表，`scope=mine\|all`（all 仅 ADMIN） |
| GET | `/api/exports/{taskId}` | 导出任务状态 |
| GET | `/api/exports/{taskId}/download` | 下载导出结果 |
| DELETE | `/api/exports/{taskId}` | 删除导出任务与结果 |
| GET | `/actuator/health` | 健康检查 |

所有响应统一为：

```json
{ "code": 0, "message": "成功", "data": {}, "traceId": "..." }
```

`code = 0` 表示成功；`401xx` 登录相关，`4xxxx` 请求错误，`5xxxx` 服务端错误。

## SQL 查询

在「SQL 查询」页选择一条已保存的连接，输入单条 SQL 执行，结果以表格返回。
服务端默认只读，并有三重护栏：

| 护栏 | 默认值 | 配置项 |
| --- | --- | --- |
| 语句类型 | 只放行 `SELECT` / `WITH` / `SHOW` / `DESCRIBE` / `EXPLAIN` 等只读语句 | `QUERY_ALLOW_WRITE=false` |
| 返回行数 | 默认 1000 行，硬上限 5000 行，超出按上限截断并标记 | `QUERY_MAX_ROWS` / `QUERY_MAX_ROWS_LIMIT` |
| 执行超时 | 单条语句 30 秒，驱动侧 + 线程池双层超时兜底 | `QUERY_TIMEOUT` |

写操作（`INSERT` / `UPDATE` / `DELETE` / DDL）默认被拒绝，也拦截多语句拼接与
`INTO OUTFILE` 之类的文件操作。确需放开时由管理员设 `QUERY_ALLOW_WRITE=true`，
但真正的权限边界仍应落在数据库账号自身——平台用的连接账号建议只给只读权限。

每次执行都会写入 `query_history`（操作人、连接、SQL、行数、耗时、成败），
查询台右侧的「查询历史」可以一键回填重跑；ADMIN 还能切到「全部」视角。
结果可通过 `POST /api/queries/export` 导出为带 BOM 的 CSV。

## 数据源隔离

连接分两种用途，用 `db_connection.query_enabled` 区分：

| 用途 | 出现在 | 说明 |
| --- | --- | --- |
| 仅管理（`query_enabled=0`） | 只在「数据库连接」页 | 只能维护配置、做连通性测试 |
| 允许查询（`query_enabled=1`） | 也会出现在查询台与指标数据源里 | 可以执行只读 SQL |

`/api/datasources` 只返回开放查询的连接，且不携带密码脱敏值等管理字段。
服务端在 `SqlQueryService` 里还会再校验一次——即使绕过前端直接调
`/api/queries/execute`，未开放查询的连接同样会被 403 拒绝。

管理平台自己的连接库默认置为"仅管理"，避免通过查询台读到平台自身的凭据表。

## 用户管理

ADMIN 可以在「用户管理」页新建用户、改昵称/角色、启用/禁用、重置密码。
几条保护规则：

- 不能修改自己的角色，也不能禁用自己的账号；
- 系统至少保留一名启用状态的管理员；
- 改角色/禁用/重置密码后，会立即吊销该用户的全部访问令牌与刷新令牌，旧
  登录态当场失效，不用等 JWT 自然过期；
- 用户只能禁用、不提供删除，保留历史记录里的操作人线索。

角色判断统一走 `Roles`（忽略大小写与首尾空白），签发令牌时也会把角色归一化成
大写。不要在业务代码或前端里直接写 `role == "ADMIN"`。

## 指标可视化与缓存

指标页的「查看」会按结果结构自动画图：时间列当 X 轴、数值列当度量、其余非时间列
当分组维度——带维度的时间序列会自动拆成多条折线。顶部可以在折线 / 柱状 / 饼图 /
表格之间切换；结果里没有数值列时自动落回表格。

图表库（ECharts）走动态 `import()`，只有真的要看图时才加载这个 chunk，
首屏包体不受影响。

指标结果缓存在 Redis，不需要每次都压数据库：

| 项 | 说明 |
| --- | --- |
| 键 | `metric:result:{指标ID}:{SQL摘要}:rows={行数上限}` |
| TTL | `QUERY_CACHE_SECONDS`，默认 300 秒；设为 0 关闭缓存 |
| 命中 | 响应里 `cached=true`；页面不暴露缓存状态，需要最新数据时点指标卡片的「刷新」 |
| 失效 | 键里带 SQL 摘要，改了指标定义自然换键，不用手工清理 |
| 跳过 | 调 `?noCache=true`（页面的「刷新」走的就是这条） |

Redis 只当加速层：读写失败一律降级为直接查库，不影响查询本身。

指标热度排行用的是 Redis 的 ZSET（`metric:usage`），每次执行指标
`ZINCRBY` 累加一次，指标名在返回时从库里补齐；指标被删掉也不会影响其余排行。

## 并发控制与多实例

数据库的连接数和并发查询能力都是有限的，所以请求侧做了三道闸门，而且都是
**按多实例来设计的**——每一条在起多个 conn-service 时依然成立：

| 闸门 | 位置 | 多实例下的行为 |
| --- | --- | --- |
| 有界执行池 | 每个实例 | `ThreadPoolExecutor(8 线程, 队列 100)`，排满直接拒绝（429），不会无限堆线程 |
| 数据源并发额度 | Redis | `INCR/DECR` + 租约的分布式计数器，默认每库 4 个并发，**多实例共享同一份额度** |
| 缓存击穿锁 | Redis | `SET NX` 抢占式短锁，热点 key 过期时只有一个实例去查库 |

实测：把单库额度设成 1，3 个真并发慢查询里 2 个被拒（HTTP 429 / code 42900），
1 个正常返回；清空缓存后 10 个并发请求打同一个指标，实际只查库 1 次。

缓存侧另外做了两件事：写入 TTL 带 ±10% 随机抖动，避免一批 key 同时过期（雪崩）；
不存在的指标 ID 会写一条 30 秒负缓存，挡住拿随机 ID 反复刷库的穿透。

## 批量导出与队列

导出不走同步请求——一次导出可能扫很多行、跑十几秒，同步会占着 Tomcat 线程和
数据库连接。现在的流程是：

```
POST /api/exports  → 落一条 PENDING 记录 + 投队列  → 立刻返回 taskId
                    → 消费者执行 SQL → CSV 写回任务表 → 状态 DONE
前端轮询状态 → 完成后 GET /api/exports/{taskId}/download 下载
```

队列默认用 **Kafka**（`EXPORT_QUEUE_TYPE=kafka`），消息键是连接 ID，
同一个库的导出落进同一分区；把消费者并发数设成每库允许的并发数，队列本身就是
一层限流。也可以切成 Redis Streams（`EXPORT_QUEUE_TYPE=redis`），语义一样
（消费组 + ACK + pending），省掉一个 Kafka 实例。

几个细节：

- **至少一次投递 + CAS 认领**：消费者把 `PENDING` 原子改成 `RUNNING` 才算认领成功，
  重复消息不会把同一条 SQL 跑两遍。
- **兜底重投**：任务卡在 PENDING/RUNNING 超过 `EXPORT_STUCK_MINUTES`（默认 5 分钟，
  消息丢了、实例被 kill 都会这样）会被定时任务重新投递。
- **降级**：队列不可用时自动同步执行，功能不丢；`EXPORT_ASYNC_ENABLED=false`
  可以整体关掉异步。
- **清理**：结果默认保留 `EXPORT_TTL_MINUTES`（120 分钟）后由定时任务删除。
- 想同步小结果集直接下载，仍然可以用 `POST /api/queries/export` 与
  `POST /api/metrics/{id}/export`。

### 演示数据

`deploy/mysql/init/02-demo-data.sql` 会建一个独立的 `platform_demo` 库、
只读账号 `demo_reader`（只有 SELECT 权限）和最近 120 天的销售明细
`sales_daily`（6 个渠道 × 大区组合，共 720 行）。

conn-service 启动时会幂等地登记好演示数据源和 4 个演示指标（按名字判重，不会覆盖
你的修改），所以指标页一打开就有趋势图、渠道对比和地区分布可看。生产环境不想要
这些演示数据时，设 `APP_DEMO_ENABLED=false` 即可。

脚本本身也是幂等的：唯一键 + `INSERT IGNORE`，存量库直接执行
`docker exec -i conn-platform-mysql mysql -uroot -p"$密码" < deploy/mysql/init/02-demo-data.sql`
就能补上，不会产生重复行。

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
>   docker exec -i conn-platform-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4
> ```

> **灌 SQL 脚本一定要带 `--default-character-set=utf8mb4`**（或者确保脚本开头有
> `SET NAMES utf8mb4`）。`mysql` 客户端的连接字符集默认是 latin1，官方镜像执行
> `docker-entrypoint-initdb.d` 时也不会替你指定，脚本里的中文会被当成 latin1 再转一次，
> 存进库就是"双重编码"的乱码——表注释、中文数据都会中招，
> 而且**数据看起来是存进去了、只是内容不对**，很难第一时间发现。
> `SqlScriptCharsetTest` 会强制检查这一点。

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

整栈（含 Kafka）常驻内存约 3.5-4.5G，**2C2G 的机器跑不动**。当前服务器是 2C2G，
上生产前需要先升配到 4C8G，或者按下面的取舍省内存：

- 把 Nacos 换成外部实例，或者用 `-Local` 模式省掉注册中心；
- 把 MySQL 换成外部实例；
- 把 `EXPORT_QUEUE_TYPE` 改成 `redis`，省掉 Kafka（导出改用 Redis Streams，
  语义一样）。

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
├─ docs/                       设计与方案文档
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
