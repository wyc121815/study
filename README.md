# Spring Boot + React 全栈脚手架

前后端分离的单仓库项目:

| 目录 | 技术栈 | 默认端口 |
| --- | --- | --- |
| `backend/` | Spring Boot 3.5.16 · Java 21 · Maven | `8080` |
| `frontend/` | React 18 · Vite 5 · TypeScript | `5173` |

## 目录结构

```
.
├─ backend/                     Spring Boot 后端
│  ├─ mvnw / mvnw.cmd           Maven Wrapper(固定使用 Maven 3.9.9)
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/example/backend/
│     │  ├─ BackendApplication.java      启动类
│     │  ├─ controller/HelloController.java  示例接口
│     │  └─ config/CorsConfig.java           跨域配置
│     ├─ main/resources/application.yml
│     └─ test/java/...                   单元测试
└─ frontend/                    React 前端
   ├─ vite.config.ts            dev server + /api 代理
   ├─ src/api/                  接口封装
   └─ src/App.tsx               示例页面
```

## 环境要求

- JDK 21(本机路径 `D:\Program Files\Java\jdk-21`)
- Node.js 18+(本机 v24)
- 不需要单独安装 Maven:项目自带 Wrapper,首次运行会自动下载 Maven 3.9.9

## 启动方式

### 1. 启动后端

```powershell
cd backend
$env:JAVA_HOME = "D:\Program Files\Java\jdk-21"   # 若已全局配置可省略
.\mvnw.cmd spring-boot:run
```

打开 <http://localhost:8080/api/hello?name=Codex> 应返回:

```json
{ "message": "Hello, Codex!", "service": "backend", "time": "..." }
```

### 2. 启动前端

另开一个终端:

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开 <http://localhost:5173>,页面会自动调用后端 `/api/hello` 展示连通状态。

> Vite 已配置代理:前端所有 `/api/**` 请求会转发到 `http://localhost:8080`,
> 因此开发时不会遇到跨域问题(后端的 `CorsConfig` 作为直连时的兜底)。

## 常用命令

后端(`backend/` 目录下):

```powershell
.\mvnw.cmd test                 # 跑单元测试
.\mvnw.cmd clean package         # 打包为 target/backend-0.0.1-SNAPSHOT.jar
.\mvnw.cmd clean package -DskipTests
java -jar target\backend-0.0.1-SNAPSHOT.jar
```

前端(`frontend/` 目录下):

```powershell
npm run dev        # 开发服务器(热更新)
npm run build      # 类型检查 + 生产构建,输出 dist/
npm run preview    # 本地预览生产构建
npm run lint       # ESLint 检查
```

## 接口约定

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/hello?name=xxx` | 示例接口,返回问候语与服务时间 |
| GET | `/actuator/health` | 健康检查 |

后端接口统一以 `/api` 开头,新增 Controller 时请沿用该前缀。

## 前后端联调 / 生产部署

- 开发环境:前端 `npm run dev` + 后端 `spring-boot:run`,由 Vite 代理转发。
- 若前端需要直连后端(例如分离部署),在 `frontend/.env.local` 中写入:

  ```
  VITE_API_BASE_URL=http://localhost:8080
  ```

  同时把前端域名加进后端 `backend/src/main/resources/application.yml`
  的 `app.cors.allowed-origins`。

- 生产环境:`npm run build` 产出的 `frontend/dist` 可交给 Nginx 托管,
  并把 `/api` 反向代理到后端 8080 端口;或将静态资源拷贝到后端
  `src/main/resources/static` 由 Spring Boot 一并提供。

## 说明

- Java 版本在 `backend/pom.xml` 的 `<java.version>` 中配置,默认 21。
- Maven Wrapper 版本在 `backend/.mvn/wrapper/maven-wrapper.properties` 中配置。
- 前端接口封装位于 `frontend/src/api/client.ts`,统一在这里处理 baseURL 与错误。

