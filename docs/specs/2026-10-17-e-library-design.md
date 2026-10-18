# 电子图书馆服务 — 设计说明书

**日期：** 2026-10-17  
**状态：** 待用户审阅  
**源码目录：** 本仓库 `source/` 模块（Maven / Spring Boot；本文档位于 `source/docs/specs/`）

---

## 1. 目标与范围

构建可在本地运行的电子图书馆 Web 服务，支持：

- 浏览与查询馆藏（实体书目、电子书）
- 实体书借阅 / 归还 / 查看本人已借阅
- 电子书在线预览（PDF）
- 多用户注册登录与角色权限（读者 / 管理员 / 超级管理员）

**不在本期范围：**

- 借阅数量/期限限制、逾期罚款
- EPUB 及其他非 PDF 格式
- 电子书下载
- 电子书借阅占用
- 移动端原生 App、微服务 / 多实例集群
- 外部对象存储（七牛、OSS、S3 等）

---

## 2. 部署假设与简化取舍

本服务按 **单实例** 部署设计（一台进程对外提供全部能力）。在此前提下，部分能力刻意简化，优先本地可跑与实现成本：

| 能力 | 本期做法 | 若多实例 / 规模上升时的替代 |
|------|----------|------------------------------|
| PDF 等电子文件 | **本地磁盘目录**存储（路径可配置）；暂认定磁盘空间够用 | 接入外部对象存储（如七牛、阿里云 OSS、S3），库中只存对象 key/URL |
| 会话 | Spring Security **服务端 Session**（单机内存/容器会话即可） | Session 外置（Redis Session）或 JWT，以支持多实例粘滞/无状态 |
| 借阅并发 | Redis 分布式锁（便于表达互斥语义，并与作业中的工程实践一致） | 多实例下同样适用；单机也可用 JVM 锁，但本期统一用 Redis |

**文件存储约定（本期）：**

- 上传的 PDF 写入配置项 `library.pdf.storage-dir` 指向的本地目录（如 `source/uploads/pdf/`）
- 数据库 `digital_item.pdf_path` 仅保存相对/绝对本地路径，由应用进程直接读文件做 inline 预览
- 不做文件同步、CDN、分片上传；备份与扩容不在本期
- **明确不引入**七牛等外部存储 SDK，避免额外账号、网络与运维依赖

该取舍与作业「控制实现范围、展示设计权衡」一致：单实例 + 本地盘换取更简单的架构；一旦需要水平扩展或存储独立扩容，再将 `file` 模块替换为对象存储适配层即可，领域模型其余部分可保持不变。

---

## 3. 技术选型

| 项 | 选择 |
|----|------|
| 形态 | Spring Boot **单实例**单体（**2.7.x**，兼容 JDK 8） |
| Web | Spring MVC + Thymeleaf |
| 持久化 | MyBatis + **MySQL 5.7** |
| 安全 | Spring Security（Session + BCrypt） |
| 并发借阅 | Redis 分布式锁 |
| 文件 | **本地目录**存储 PDF（路径可配置；非七牛/OSS） |
| 构建运行 | Maven，`mvn spring-boot:run` |
| 运行时 | **JDK 8**、MySQL 5.7、Redis |

**本地依赖：** JDK 8、Maven、MySQL 5.7、Redis。  
**说明：** 不使用 Spring Boot 3 / JDK 17（与本地已有环境对齐）。验证 API 使用 `javax.*`（非 `jakarta.*`）。

---

## 4. 架构

```
浏览器 (Thymeleaf)
    │  HTTP + Session (Spring Security)
    ▼
Spring Boot (source/)
  ├─ Web / Controller
  ├─ Service
  ├─ Security
  ├─ MyBatis Mapper ──► MySQL
  └─ File + Redis Lock ──► 本机 PDF 目录 / Redis
```

分层：`Controller → Service → Mapper`；按业务分包（user、physical、digital、borrow、file、admin、config）。

**部署：** 单进程单实例；PDF 由该实例本机目录读写，不经过外部对象存储。

**API 形态：** 以服务端渲染页面 + 表单/链接对应的 HTTP 端点为主（REST 风格的资源路径与清晰的 POST 写操作），不单独做前后端分离 JSON API；这与「Thymeleaf + 单实例本地易跑」的取舍一致。

---

## 5. 角色与权限

| 角色 | 说明 |
|------|------|
| 匿名 | 见下方「公开」能力 |
| READER | 注册用户默认角色 |
| ADMIN | 管理员：馆藏维护 |
| SUPER_ADMIN | 超级管理员：配置种子账号；可授予/撤销 ADMIN |

### 5.1 公开（无需登录）

- 首页
- 注册、登录页
- **实体书目**列表 / 搜索 / 详情（展示可借册数 / 总册数）
- **电子书**列表 / 搜索 / 详情
- 电子书 **PDF 在线预览**（禁止下载）

### 5.2 需登录

- 实体书 **借阅**、**归还**、**我的借阅**
- 登出

### 5.3 管理员（ADMIN / SUPER_ADMIN）

- 实体书目与复本的增删改、下架（软删除）
- 电子书增删改、PDF 上传、下架
- 超管额外：指定用户为管理员 / 撤销管理员

### 5.4 超级管理员种子

- 用户名 + 密码写在 `application.yml`（或等价配置）
- 应用启动时若不存在则自动创建，角色为 `SUPER_ADMIN`

---

## 6. 领域模型

### 6.1 用户 `user`

- `id`, `username`, `password_hash`
- `role`: `READER` | `ADMIN` | `SUPER_ADMIN`
- `created_at`, `updated_at`

### 6.2 实体书目 `physical_work`

- `id`, `title`, `author`
- `content_type`: `BOOK` | `JOURNAL`
- `description`, `isbn_or_issn`（可选）
- `deleted`（书目级软删除）
- `created_by`, `created_at`, `updated_at`

列表/详情按**书目**展示，并汇总：**可借册数 / 总册数**（未删除复本中 `AVAILABLE` 数 / 未删除复本总数）。

### 6.3 实体复本 `physical_copy`

- `id`, `work_id`, `copy_no`（馆藏号/册号）
- `status`: `AVAILABLE` | `BORROWED`
- `deleted`（单册软删除）
- `created_at`, `updated_at`

同一书目可有多册复本（例如「活着」3 册），最多同时被 3 人各借 1 册；**同一复本**同一时间仅一人可借。

### 6.4 借阅记录 `borrow_record`

- `id`, `user_id`, `physical_copy_id`
- `borrowed_at`, `returned_at`（`NULL` = 未还）
- 约束：同一 `physical_copy_id` 最多一条 `returned_at IS NULL` 的记录

### 6.5 电子馆藏 `digital_item`

- `id`, `title`, `author`
- `content_type`: `BOOK` | `JOURNAL`
- `description`, `isbn_or_issn`（可选）
- `pdf_path`, `pdf_original_name`
- `deleted`（软删除）
- `created_by`, `created_at`, `updated_at`

电子馆藏**无借阅、无复本概念**；与实体分表管理。`pdf_path` 指向单实例本机文件，不存外部存储 URL。

---

## 7. 关键业务流程

### 7.1 实体借阅（含 Redis 锁）

1. 用户已登录，选择某书目下的可用复本（或系统分配一册 `AVAILABLE` 复本——实现时采用「用户对书目点借阅，服务端选取一册可用复本」或「用户指定册号」；**默认：对书目借阅，服务端选取一册 AVAILABLE 复本**）。
2. 对目标复本加 Redis 分布式锁，key 形如 `borrow:copy:{copyId}`。
3. **未获取到锁**：立即返回失败（提示如「借阅繁忙，请稍后重试」）。
4. **获取到锁后**（事务内）：
   - 再次校验复本未删除且 `AVAILABLE`
   - 写入 `borrow_record`，复本状态改为 `BORROWED`
   - 提交事务并释放锁
5. 校验失败同样释放锁并提示（如「暂无可用复本」）。

### 7.2 归还

- 仅当前借阅人可归还对应未还记录。
- 事务内：`returned_at` 置当前时间，复本改回 `AVAILABLE`。
- 归还路径建议同样使用按复本的短锁，避免与借阅并发打架。

### 7.3 下架（软删除）

- 书目或复本 / 电子条目标记 `deleted=true`。
- 公开与读者列表中不可见、不可再借。
- 书目下架后，其下复本均不再对公开列表聚合展示；未还复本的借阅关系仍按例外处理。
- **例外**：若某复本仍被借出，当前借阅人在「我的借阅」中仍可见该记录，并允许归还；归还后不再对读者展示。
- 超管不可撤销自身的 `SUPER_ADMIN`；管理员角色的授予/撤销仅针对普通用户 ↔ `ADMIN`。

### 7.4 电子书预览

- 任何人（含未登录）可在线预览 PDF。
- 文件从**本机存储目录**读取后以 inline 方式输出。
- **禁止下载**：不提供下载入口；响应使用 inline 预览，并尽量限制 content-disposition 为 inline；不做附件下载接口。

---

## 8. 页面结构（Thymeleaf）

| 页面 | 访问 |
|------|------|
| 首页（入口：实体 / 电子） | 公开 |
| 注册 / 登录 | 公开 |
| 实体书目列表（搜索、分页） | 公开 |
| 实体书目详情（可借/总册数） | 公开 |
| 借阅操作 | 登录 |
| 我的借阅 / 归还 | 登录 |
| 电子书列表（搜索、分页） | 公开 |
| 电子书详情 + PDF 预览 | 公开 |
| 管理后台：实体书目/复本 | ADMIN+ |
| 管理后台：电子书 + 上传 | ADMIN+ |
| 超管：用户角色管理 | SUPER_ADMIN |

搜索：按标题、作者关键词；列表分页。

---

## 9. 配置要点

```yaml
# 示意，实现时落到 application.yml
spring:
  datasource: # MySQL
  data:
    redis: # Redis
library:
  super-admin:
    username: ...
    password: ...
  pdf:
    storage-dir: ./uploads/pdf
    max-size-mb: 50
```

---

## 10. 错误与安全

- 用户名重复、登录失败：明确错误提示
- 未登录访问需登录接口：跳转登录或 401/403
- 无可用复本、抢锁失败、非借阅人归还：业务错误提示
- 密码 BCrypt；方法/URL 级角色鉴权
- PDF 上传：仅 `application/pdf`（及扩展名校验）、大小限制、存储路径防穿越
- 敏感信息不写明文日志（密码等）

---

## 11. 测试策略

- 单元/集成测试覆盖：注册登录与角色、实体借还与锁失败路径、电子书公开预览、管理员 CRUD/下架、超管改角色
- 增量行覆盖率、分支覆盖率 ≥ **60%**
- Mock：外部 Redis/部分文件 IO 按需；测试命名 `methodName_condition_expectedResult`；AAA 模式

---

## 12. 目录约定

```
source/                          # Maven 工程根
  docs/
    specs/                       # 设计说明书（本文档所在目录）
    plans/                       # 实现计划
  src/main/java/.../
    config/
    user/
    physical/                    # work + copy
    digital/
    borrow/
    file/                        # 本地磁盘读写，非对象存储
    admin/
  src/main/resources/
    templates/
    mapper/
    application.yml
  uploads/pdf/                   # 单实例本地存储目录（可配置，勿提交大文件）
```

---

## 13. 已确认决策摘要

1. **单实例**部署；PDF **本地磁盘**存储（空间暂认定够用），不引入七牛等外部对象存储  
2. Web：Thymeleaf + Spring MVC；栈：Spring Boot + MyBatis + MySQL  
3. 多用户注册登录（用户名+密码）；超管配置种子自动创建  
4. 实体与电子分表；图书/期刊为各自 `content_type`  
5. 实体：书目 + 复本；借阅针对复本；多册可多人同时借  
6. 实体浏览公开；借还与「我的借阅」需登录  
7. 电子书公开在线预览，全程禁止下载，无借阅  
8. 下架软删除；未还持有人仍可归还  
9. 并发借阅：Redis 分布式锁，未抢到则失败提示  
10. 方案 A：经典分层 + Spring Security Session  

---

## 14. 开放实现细节（不阻塞设计，实现计划中敲定）

- 借阅入口默认「对书目一键借阅（自动选册）」；详情页亦可展示分册状态供管理员维护
- Redis 锁实现（如 Redisson / 自研 SET NX + TTL）在实现计划中选定，须带 TTL 防死锁
- PDF 预览组件（浏览器内嵌 `<embed>`/`<iframe>` 或 pdf.js）在实现时选定，须保持无下载按钮的产品约束
- 本地存储目录权限与启动时自动创建目录的细节在实现计划中落实
