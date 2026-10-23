# 图书管理系统 · Library Manager System

一个前后端分离的图书管理系统（课程项目）：后端提供 REST 接口，前端提供管理界面。
支持**图书 / 分类 / 借阅归还 / 通知公告**四大模块，以及基于 **JWT** 的登录鉴权与 **ADMIN / USER** 两种角色的权限控制。

---

## 功能

| 模块 | 能力 |
| --- | --- |
| 图书管理 | 分类列表、图书分页查询与筛选、图书详情、新增 / 修改 / 删除、上架 / 下架 |
| 借阅管理 | 借书、我的借阅、借阅概览统计、归还、续借、取消、借阅记录查询与删除 |
| 通知公告 | 公告列表 / 详情、未读数统计、发布、修改、置顶、删除 |
| 用户与鉴权 | 注册、登录（JWT）、获取当前用户、登出、修改密码、用户管理（新增 / 修改 / 重置密码 / 启用禁用 / 删除）、注销账号 |

权限模型：登录拦截器校验 `Authorization` 头里的 JWT，方法上标注 `@RequireAdmin` 的接口只有管理员可以调用（写操作基本都在这类里）。

---

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.0.8、Java 17、MyBatis、MySQL 8、PageHelper、jjwt 0.12.6、Lombok |
| 前端 | Vue 3.5、Vite 6、Element Plus、Vue Router、Axios |
| 鉴权 | JWT（HS256/HS384，有效期 2 小时）+ 自定义拦截器 |

---

## 项目结构

```
library_manager_system/
├── libaray_manager_system/            # 后端（Maven 模块）
│   ├── pom.xml
│   └── src/main/
│       ├── java/org/libaray_manager_system/
│       │   ├── controller/            # 接口层
│       │   ├── service/               # 业务层（含 Impl）
│       │   ├── mapper/                # MyBatis Mapper 接口
│       │   ├── pojo/                  # entity / dto / vo
│       │   ├── interceptor/           # JWT 拦截器
│       │   ├── annotation/            # @RequireAdmin 等
│       │   ├── exception/             # 全局异常处理
│       │   ├── config/                # Web / 静态资源配置
│       │   └── utils/                 # JwtUtil 等工具
│       └── resources/
│           ├── application.yml        # 数据源等配置（支持环境变量覆盖）
│           └── mapper/                # MyBatis XML
├── library-web/                       # 前端（Vue 3 + Vite）
│   ├── index.html
│   ├── vite.config.js                 # /api 代理到后端 8081
│   └── src/
│       ├── api/                       # 接口封装
│       ├── components/ layout/ router/ store/ styles/ utils/
│       └── views/                     # book / borrow / notice / user / login / register / profile / home
└── docs/                              # 设计、接口、建表、部署文档
    ├── api/                           # 各版本接口文档
    ├── sql/                           # 建表 + 初始化数据
    ├── plans/                         # 设计方案
    ├── deploy/                        # Nginx 部署配置
    └── result/                        # 实现记录与后评估
```

---

## 快速开始

### 0. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8

### 1. 初始化数据库

按文件名顺序执行 `docs/sql/` 下的脚本（脚本可重复执行，不会删除已有数据）：

```bash
mysql -u root -p < docs/sql/01-v1.0-图书管理建表.sql
mysql -u root -p < docs/sql/02-v1.0-新增10本图书.sql
mysql -u root -p < docs/sql/02-v2.0-用户与登录建表.sql
mysql -u root -p < docs/sql/03-v3.0-通知公告与借阅建表.sql
```

> 数据库名默认 `library_system`，表：`book`、`book_category`、`sys_user`、`notice`、`notice_read`、`borrow_record`。

### 2. 启动后端

```bash
cd libaray_manager_system
mvn spring-boot:run
```

默认监听 <http://localhost:8081>。

### 3. 启动前端

```bash
cd library-web
npm install
npm run dev
```

默认监听 <http://localhost:5173>，开发服务器会把 `/api` 和 `/uploads` 代理到后端 8081（见 `vite.config.js`），所以不会跨域。

### 4. 默认账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | 123456 | ADMIN（管理员） |
| zhangsan | 123456 | USER（普通用户） |
| lisi | 123456 | USER（普通用户） |

---

## 配置说明

`application.yml` 里的敏感项都写成了 `${环境变量:默认值}` 的形式，**不配环境变量也能直接用默认值跑本地开发**；部署时用环境变量覆盖即可。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | 数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | `library_system` | 数据库名 |
| `DB_USER` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `JWT_SECRET` | 代码内占位密钥 | JWT 签名密钥，**至少 32 字节**，否则启动报错 |

示例（PowerShell）：

```powershell
$env:DB_HOST="10.0.0.8"; $env:DB_PASSWORD="你的密码"; $env:JWT_SECRET="至少32字节的随机串"
mvn spring-boot:run
```

生成随机密钥：

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```

---

## 接口文档

接口的请求/响应格式、错误码、字段说明都在 `docs/api/` 下，按版本拆分：

| 文档 | 内容 |
| --- | --- |
| `01-v1.0-图书管理接口文档.md` | 图书列表 / 详情 / 增删改 / 上下架 |
| `01.1-书号查找接口-1.0增补.md` | 按书号查询 |
| `02-v2.0-用户与登录接口文档.md` | 登录、JWT、用户管理 |
| `03-v2.1-注册接口文档.md` | 注册 |
| `04-v2.2-注销账号接口文档.md` | 注销账号 |
| `05-v3.0-通知公告接口文档.md` | 公告 |
| `06-v3.0-借阅归还接口文档.md` | 借阅、归还、续借 |

后端主要资源路径：`/auth/**`、`/books`、`/categories`、`/borrows`、`/notices`、`/users`。

---

## 说明

- 本项目为课程作业 / 学习项目，数据库密码与 JWT 密钥的默认值**仅用于本地开发演示**，生产部署请务必用环境变量覆盖。
- 前端构建产物、`node_modules`、Maven `target`、IDE 配置均已通过 `.gitignore` 排除。
