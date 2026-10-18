# e-library（电子图书馆）

## 1. 项目简介

e-library 是一个可在本地运行的**单实例电子图书馆 Web 服务**，面向读者与管理员提供：

| 能力 | 说明 |
|------|------|
| 实体馆藏 | 浏览 / 搜索书目与详情（可借册数 / 总册数），登录后借阅、归还、查看「我的借阅」 |
| 电子馆藏 | 浏览 / 搜索电子书，**PDF 在线预览**（禁止下载） |
| 账号与权限 | 读者注册登录；管理员维护馆藏；超级管理员管理管理员角色 |
| 后台管理 | 实体书目与复本、电子书 PDF 上传、软删除（下架）等 |

技术形态：**Spring Boot 2.7 + Thymeleaf 服务端渲染**，非前后端分离；数据落 **MySQL 5.7**，借阅等并发互斥使用 **Redis 分布式锁**，PDF 存**本机目录**。

更细的设计说明见仓库内：

- `docs/specs/2026-10-17-e-library-design.md` — 总体设计
- `docs/specs/2026-10-18-exception-handling-design.md` — 异常分级处理

---

## 2. 环境、配置与启动

### 2.1 环境依赖

| 组件 | 版本 / 要求 |
|------|-------------|
| JDK | **8** |
| Maven | 3.6+ |
| MySQL | **5.7** |
| Redis | 任意稳定版（默认 `127.0.0.1:6379`） |

### 2.2 创建数据库

```sql
CREATE DATABASE elibrary DEFAULT CHARSET utf8mb4;
```

命令行示例（按本机账号密码调整）：

```bash
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS elibrary DEFAULT CHARSET utf8mb4;"
# 若 root 有密码：mysql -uroot -p你的密码 -e "..."
```

### 2.3 修改配置

主配置文件：`src/main/resources/application.yml`。

| 配置项 | 含义 | 默认值（示例） |
|--------|------|----------------|
| `server.port` | HTTP 端口 | `8080` |
| `spring.datasource.url` | JDBC 地址（库名 `elibrary`） | `jdbc:mysql://127.0.0.1:3306/elibrary?...` |
| `spring.datasource.username` / `password` | 数据库账号 | `root` / 空密码 |
| `spring.redis.host` / `port` | Redis | `127.0.0.1` / `6379` |
| `library.super-admin.username` / `password` | 启动时种子超管（库中不存在才创建） | `superadmin` / `ChangeMe123!` |
| `library.pdf.storage-dir` | PDF 本地存储目录（相对路径相对进程工作目录） | `./uploads/pdf` |
| `library.pdf.max-size-mb` | 上传大小上限（MB） | `50` |
| `spring.servlet.multipart.max-file-size` | Servlet 上传上限 | `50MB` |

说明：

- `spring.sql.init.mode: always`：启动时执行 `schema.sql` 建表（表已存在时需注意本机 schema 是否幂等）。
- 超管密码**不会**在日志中明文打印；若库中已有同名用户，**不会**因改配置而自动重置密码。

### 2.4 启动依赖服务

```bash
# MySQL 可用
mysql -uroot -e "SELECT 1"

# Redis 可用
redis-cli ping   # 期望 PONG
```

### 2.5 启动应用

在 `source/` 目录下：

```bash
cd source
mvn spring-boot:run
```

若公司 Nexus 镜像返回 **403**，请改用 Maven Central，例如：

```bash
# 临时 settings 仅指向 Central（勿提交到仓库）
cat > /tmp/maven-central-settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>central-direct</id>
      <url>https://repo.maven.apache.org/maven2</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF

mvn -s /tmp/maven-central-settings.xml spring-boot:run
```

或先打包再运行：

```bash
mvn -s /tmp/maven-central-settings.xml -DskipTests package
java -jar target/e-library-0.1.0-SNAPSHOT.jar
```

访问首页：<http://localhost:8080>

### 2.6 测试与覆盖率

```bash
cd source
mvn test
# 或：mvn -s /tmp/maven-central-settings.xml test
mvn verify   # Jacoco：行 / 分支覆盖率门槛 ≥ 60%
```

报告目录：`target/site/jacoco/`。单测以 Mockito 为主；测试配置见 `src/test/resources/application-test.yml`。

---

## 3. 如何使用与注意事项

### 3.1 角色与入口

| 角色 | 典型操作 |
|------|----------|
| **匿名** | 首页；注册 / 登录；浏览实体书目与电子书列表、详情；电子书 PDF 预览 |
| **READER（读者）** | 注册后默认角色；借阅 / 归还实体书；「我的借阅」 |
| **ADMIN（管理员）** | `/admin/**`：维护实体书目与复本、电子书与 PDF 上传、下架 |
| **SUPER_ADMIN（超管）** | 上述管理能力 + `/admin/users`：授予 / 撤销 ADMIN |

默认超管：配置项 `library.super-admin`（默认用户名 `superadmin`，密码 `ChangeMe123!`）。生产或演示环境请尽快修改密码。

### 3.2 推荐使用路径

1. **读者**：打开首页 → 注册 → 登录 → 在「实体书」详情借阅 →「我的借阅」归还；电子书详情页内嵌预览即可。
2. **管理员**：用超管登录 →「用户管理」将某读者设为 ADMIN → 管理员登录后维护馆藏、上传 PDF。
3. **搜索**：列表页支持关键词检索；非法搜索条件会在页面提示，不会把内部错误抛给用户。

### 3.3 注意事项

1. **单实例部署**：PDF 存在本机磁盘，多实例部署不会自动共享文件，也不适合无共享盘时水平扩展。
2. **电子书仅预览**：接口使用 `Content-Disposition: inline`，页面无下载按钮；请勿自行增加 attachment 下载能力（除非产品明确要求）。
3. **借阅并发**：同一复本借阅用 Redis 锁；抢锁失败会提示「借阅繁忙，请稍后重试」等友好文案，需保证 Redis 可用。
4. **软删除**：书目 / 复本 / 电子书「下架」为软删除，列表默认不展示已下架内容。
5. **异常展示**：业务可预期错误（如「暂无可用复本」）会直接提示；数据库 / Redis 连接失败等系统问题统一提示「网络异常，请稍后重试」，避免把 SQL 等细节暴露给用户。
6. **Referer 回跳**：业务异常处理后可能回跳到来源页，但仅允许**同源** Referer，跨站 Referer 会落到 `/error`。
7. **上传限制**：PDF 大小受 `library.pdf.max-size-mb` 与 multipart 配置双重限制；仅接受 PDF。
8. **安全**：默认超管密码仅适合本地演示；勿将含真实密码的 `application.yml` 提交到公共仓库。

### 3.4 常见问题

| 现象 | 处理 |
|------|------|
| 无法连 MySQL | 确认 5.7 已启动与建库；修改 `spring.datasource.*` |
| Redis 连接拒绝 | 启动 Redis 后再启动应用；否则借阅等依赖锁的接口会失败 |
| Maven 依赖 403 | 使用 Maven Central settings，勿用失效内网镜像 |
| 超管登录失败 | 核对配置；库中已有用户时改 yml **不会**自动改密 |
| 预览 PDF 空白 | 确认管理员已上传文件且 `storage-dir` 路径对进程可写可读 |

---

## 4. 核心设计思路

### 4.1 整体架构

```
浏览器 (Thymeleaf 页面)
    │  HTTP + Session (Spring Security)
    ▼
Spring Boot (本模块 source/)
  ├─ Controller（页面路由 / 表单 POST）
  ├─ Service（领域规则与事务）
  ├─ MyBatis Mapper ──► MySQL 5.7
  ├─ LocalPdfStorage ──► 本机 PDF 目录
  └─ RedisDistributedLock ──► Redis
```

- **分层**：`Controller → Service → Mapper`，按业务分包：`user`、`physical`、`digital`、`borrow`、`file`、`admin`、`common`、`config`。
- **交互形态**：服务端渲染 + 表单 / 链接；不做独立 JSON API / SPA（刻意降低本地运行与演示成本）。
- **部署假设**：单进程单实例；会话用服务端 Session；文件不接 OSS。

### 4.2 领域模型设计与思考

核心对象与关系：

| 概念 | 说明 | 设计取舍 |
|------|------|----------|
| **User** | `READER` / `ADMIN` / `SUPER_ADMIN` | 角色简单枚举，满足作业级权限演示即可 |
| **PhysicalWork（书目）** | 标题、作者、图书/期刊等元数据 | 列表按**书目**聚合展示可借/总册数 |
| **PhysicalCopy（复本）** | 隶属于书目，`AVAILABLE` / `BORROWED` | 同一书目可多册；**同一复本**同时仅一人可借 |
| **BorrowRecord** | 用户 ↔ 复本；`returned_at IS NULL` 表示在借 | 归还写回时间并恢复复本状态 |
| **DigitalItem** | 电子书元数据 + 本地 `pdf_path` | **无复本、无借阅**；与实体分表，避免混用库存语义 |

思考要点：

1. **书目与复本分离**：读者关心「这本书能不能借」；库存真相在复本状态，避免在书目上硬塞「库存数字」却难以支撑多册并发。
2. **实体与电子分治**：电子资源无物理占用，强行复用借阅模型会引入假库存；故电子仅「可预览」生命周期。
3. **软删除**：下架可恢复空间留给运营，查询侧过滤 `deleted`。
4. **借阅路径**：用户对**书目**发起借阅，服务端挑选一册 `AVAILABLE` 复本并加锁，简化前端选册交互。
5. **锁粒度**：`borrow:copy:{copyId}`，保证同一复本不会被并发借出两次；锁失败快速返回友好提示。

### 4.3 异常处理设计

目标：对用户友好、对系统可观测、不泄露基础设施细节。

```
Service 抛 BusinessException(级别, message)
         或 基础设施抛出任意 Exception
                │
                ▼
        GlobalExceptionHandler
                │
    ┌───────────┴───────────┐
    │ WARN 业务异常          │ ERROR 业务异常 / 其它异常
    │ flash 展示 message     │ flash 固定「网络异常，请稍后重试」
    │ log.warn               │ log.error（含堆栈）
    └───────────┬───────────┘
                ▼
     同源 Referer 回跳，否则 /error
```

| 类型 | 用户可见文案 | 日志 |
|------|--------------|------|
| `BusinessException` + **WARN**（默认） | `ex.getMessage()`（如「暂无可用复本」「PDF 保存失败」） | `warn` |
| `BusinessException` + **ERROR** | 固定文案「网络异常，请稍后重试」 | `error` |
| 非 `BusinessException`（DB/Redis/NPE 等） | 同上固定文案 | `error` |

约定：

- 凡能给出用户可理解提示的，优先 **WARN**（含 PDF 校验 / 保存失败等现有文案）。
- 文案解析集中在 `ExceptionMessages`，供 Handler 与注册页局部 catch 复用。
- 成功仍走 flash `message` + `redirect`；失败走 flash `error`，保持 Thymeleaf SSR 一致体验。

相关类：`ExceptionLevel`、`BusinessException`、`ExceptionMessages`、`GlobalExceptionHandler`。

### 4.4 其它工程取舍（摘要）

| 主题 | 做法 |
|------|------|
| 安全 | Spring Security 表单登录 + BCrypt；URL 级角色控制 |
| 文件 | 本地目录，启动时可自动创建；库中只存路径 |
| 并发 | Redis `SET NX` + TTL + 校验 value 释放，不引入 Redisson |
| 测试 | JUnit 5 + Mockito；Jacoco 增量门槛 60% |

---

## 5. 目录速览

```
source/
├── pom.xml
├── README.md                 ← 本文档
├── docs/
│   ├── specs/                # 设计说明书
│   └── plans/                # 实现计划
├── src/main/java/com/elibrary/
│   ├── user / physical / digital / borrow / admin / file
│   ├── common/               # 异常、分页、Redis 锁
│   └── config/               # Security、属性、种子超管等
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   ├── mapper/
│   └── templates/            # Thymeleaf 页面
└── src/test/                 # 单元测试
```
