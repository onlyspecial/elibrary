# 异常分级与统一处理 — 设计说明书

**日期：** 2026-10-18  
**状态：** 已实现（2026-10-18）  
**范围：** 在现有 Thymeleaf SSR 形态下，优化异常处理；不做前后端解耦 / JSON API 改造  
**相关代码：** `src/main/java/com/elibrary/common/`（相对 `source/` 模块根）

---

## 1. 背景与目标

### 1.1 现状问题

- `GlobalExceptionHandler` 仅捕获 `BusinessException`，DB / Redis / 未预期异常走 Spring 默认错误页，可能暴露非用户友好信息。
- `BusinessException` 只有 message，无级别，无法区分「可展示业务提示」与「系统故障」。
- 交互出口仍为 flash + redirect（本期保持不变）。

### 1.2 目标

1. **兜底任意异常**：Controller 层未捕获的异常均由全局 Handler 处理。
2. **异常分级**：WARN / ERROR。
   - **WARN**：向用户展示异常 `message`。
   - **ERROR**：向用户展示固定文案「网络异常，请稍后重试」，不暴露底层细节。
3. `BusinessException` 支持设置级别；**默认 WARN**（兼容现有抛点）。
4. **非 `BusinessException`**：一律按 ERROR 处理。
5. 凡可给用户友好提示的场景，**优先使用 WARN**（含 PDF 保存失败等已有文案）。

### 1.3 非目标（本期不做）

- JSON 统一信封 / `ResponseBodyAdvice` / MessageConverter
- 前端路由解耦、后端不再返回页面地址
- 引入完整 ErrorCode 枚举体系（Braavos 级）
- 改变 SSR + flash + redirect 的交互形态

---

## 2. 设计决策（相对 Braavos 的收敛）

参考 Braavos 的核心原则：**级别驱动日志 + 前端文案脱敏**；但本项目保持 SSR，故：

| Braavos | 本项目本期 |
|---------|------------|
| `YqgException` + `ErrorCode`（含级别） | `BusinessException` + `ExceptionLevel` |
| HTTP 200 + JSON `{status, body}` | flash `error` + `RedirectView` |
| MessageConverter 包信封 | 不引入 |
| ERROR → 默认「网络繁忙…」 | ERROR →「网络异常，请稍后重试」 |

---

## 3. 异常模型

### 3.1 `ExceptionLevel`

```java
package com.elibrary.common;

public enum ExceptionLevel {
    WARN,
    ERROR
}
```

### 3.2 `BusinessException`

| 构造 | 级别 | 说明 |
|------|------|------|
| `BusinessException(String message)` | **WARN（默认）** | 兼容现有全部 `throw new BusinessException("…")` |
| `BusinessException(ExceptionLevel level, String message)` | 显式指定 | 业务侧需要 ERROR 时使用 |
| `BusinessException(ExceptionLevel level, String message, Throwable cause)` | 显式指定 | 需要保留 cause 打日志时使用 |

字段：`private final ExceptionLevel level`，提供 `getLevel()`。

### 3.3 抛异常约定

| 场景 | 做法 |
|------|------|
| 业务校验、资源不存在、锁竞争繁忙、参数不合法、PDF 校验/保存失败等**已有友好文案** | `BusinessException`（默认 WARN）或显式 `WARN` |
| 无法给出友好文案、或确属系统故障且不想展示细节 | `BusinessException(ERROR, "内部说明", cause)` 或直接让底层异常上抛 |
| MyBatis / JDBC / Redis 连接失败、NPE 等 | **不必包装**；由 Handler 按非 `BusinessException` → ERROR 兜底 |

**明确约定：**「PDF 保存失败」「PDF 读取失败」等现有友好提示 **保持 WARN**，不改为 ERROR。

---

## 4. 全局异常处理

### 4.1 用户可见文案规则

常量（建议放在 `GlobalExceptionHandler` 或 `common` 常量类）：

```text
网络异常，请稍后重试
```

| 异常 | 用户可见文案 | 日志 |
|------|--------------|------|
| `BusinessException` + WARN | `ex.getMessage()` | `log.warn("Business warn: {}", msg)`（可不打完整堆栈，或按现有习惯） |
| `BusinessException` + ERROR | 固定文案 | `log.error("Business error: {}", msg, ex)` |
| 其它 `Exception` | 固定文案 | `log.error("Unhandled error", ex)` |

**禁止**：将 `SQLException`、Redis、堆栈原文写入 flash / 页面。

### 4.2 Handler 行为

`GlobalExceptionHandler`：

1. `@ExceptionHandler(BusinessException.class)`  
   - 按 `level` 解析用户文案与日志级别。  
   - 展示出口：`RedirectAttributes.addFlashAttribute("error", userMessage)` + `RedirectView(Referer 或 /error)`（与现状一致）。

2. `@ExceptionHandler(Exception.class)`  
   - 一律 ERROR 文案 + `log.error`。  
   - 同一 redirect / flash 出口。

3. 解析用户文案建议抽私有方法，例如：

```text
resolveUserMessage(BusinessException) /
resolveUserMessageForUnhandled(Exception)  // 恒为固定文案
```

便于单测与 `AuthController` 局部 catch 复用（见 4.3）。

### 4.3 `AuthController.register` 局部 catch

注册接口仍局部捕获 `BusinessException` 并 `rejectValue`（不走 redirect）。需与全局规则一致：

- WARN → 字段错误使用 `e.getMessage()`
- ERROR → 字段错误使用固定文案「网络异常，请稍后重试」

实现上优先调用与 Handler 相同的文案解析逻辑（抽到 `common` 小工具方法，避免两处复制规则）。

### 4.4 错误页

`templates/error.html` 继续展示 flash `error`；无需改文案结构。无 flash 时的默认句可保留，或与固定文案对齐为同一句（实现时二选一，优先与常量一致）。

---

## 5. 数据流

```
Service / 基础设施抛异常
        │
        ├─ BusinessException(WARN) ──► Handler: warn 日志 + flash(message) + redirect
        ├─ BusinessException(ERROR) ─► Handler: error 日志 + flash(固定文案) + redirect
        └─ 其它 Exception ──────────► Handler: error 日志 + flash(固定文案) + redirect
                                              │
                                              ▼
                                    Thymeleaf 页 th:if="${error}"
```

---

## 6. 改动文件清单

| 文件 | 变更 |
|------|------|
| **新建** `ExceptionLevel.java` | WARN / ERROR |
| `BusinessException.java` | 增加 level 与构造器 |
| `GlobalExceptionHandler.java` | 分级处理 + `Exception` 兜底；文案常量 |
| **新建或抽** 文案解析工具（可选，推荐） | Handler 与 AuthController 共用 |
| `AuthController.java` | 局部 catch 按级别选文案 |
| 单测（新建/扩展） | 见第 7 节 |
| Service / LocalPdfStorage | **默认不改**；保持现有 WARN 友好文案 |

---

## 7. 测试计划

命名遵循 `methodName_condition_expectedResult`，AAA 模式。

建议覆盖：

1. `handleBusinessException_whenWarn_shouldFlashOriginalMessage`
2. `handleBusinessException_whenError_shouldFlashNetworkMessage`
3. `handleException_whenDataAccessOrRuntime_shouldFlashNetworkMessage`（可用任意 `RuntimeException` / mock）
4. 断言 flash 中**不包含**模拟的 SQL/内部细节字符串
5. `BusinessException_defaultConstructor_shouldBeWarnLevel`
6. 注册局部 catch：WARN 展示业务 message；ERROR 展示固定文案（若补测 AuthController）

覆盖率：新增/修改代码行与分支 ≥ 60%。

---

## 8. 验收标准

- [ ] 任意未捕获异常不会把 JDBC/Redis 原始信息展示给用户
- [ ] WARN 级 `BusinessException` 页面可见原文
- [ ] ERROR 级 `BusinessException` 与非业务异常页面均为「网络异常，请稍后重试」
- [ ] 现有业务抛点（含 PDF 保存失败）行为不变（默认 WARN + 原 message）
- [ ] 相关单测通过，增量覆盖率达标

---

## 9. 后续可选（不在本期）

- 引入 ErrorCode 枚举（类似 Braavos）
- 写操作改为 JSON + 前端控路由（原方案 A/B）
- Redis 连接失败在锁组件内包装为带 cause 的 ERROR `BusinessException`（当前依赖全局 `Exception` 兜底即可）
