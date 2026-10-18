# 异常分级与统一处理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持 Thymeleaf SSR + flash + redirect 的前提下，为 eLibrary 增加异常级别（WARN/ERROR），并兜底任意未捕获异常，避免向用户暴露 DB/Redis 等底层信息。

**Architecture:** 新增 `ExceptionLevel`；扩展 `BusinessException` 携带级别（默认 WARN）；抽取 `ExceptionMessages` 统一解析用户可见文案；`GlobalExceptionHandler` 处理 `BusinessException` 与 `Exception`；`AuthController` 局部 catch 复用同一文案规则。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring MVC、JUnit 5、AssertJ、Mockito（现有测试栈）

**Spec:** `docs/specs/2026-10-18-exception-handling-design.md`

**Git 约定：** Commit 步骤默认 **跳过**，直至用户明确要求 commit。

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `source/src/main/java/com/elibrary/common/ExceptionLevel.java` | **新建** WARN / ERROR 枚举 |
| `source/src/main/java/com/elibrary/common/ExceptionMessages.java` | **新建** 固定文案常量 + `resolveUserMessage(...)` |
| `source/src/main/java/com/elibrary/common/BusinessException.java` | 增加 `level` 与构造器 |
| `source/src/main/java/com/elibrary/common/GlobalExceptionHandler.java` | 分级处理 + `Exception` 兜底 |
| `source/src/main/java/com/elibrary/user/AuthController.java` | 局部 catch 按级别选文案 |
| `source/src/main/resources/templates/error.html` | 无 flash 时默认句与常量对齐 |
| `source/src/test/java/com/elibrary/common/ExceptionMessagesTest.java` | **新建** 文案解析单测 |
| `source/src/test/java/com/elibrary/common/BusinessExceptionTest.java` | **新建** 默认级别单测 |
| `source/src/test/java/com/elibrary/common/GlobalExceptionHandlerTest.java` | **新建** Handler 分级与兜底单测 |
| `source/src/test/java/com/elibrary/web/PublicControllerTest.java` | 扩展注册 ERROR 场景；保留原 Referer 测试 |

**不改：** Service / `LocalPdfStorage`（PDF 保存失败等友好文案保持默认 WARN）。

---

### Task 1: ExceptionLevel + ExceptionMessages + BusinessException（TDD）

**Files:**
- Create: `source/src/main/java/com/elibrary/common/ExceptionLevel.java`
- Create: `source/src/main/java/com/elibrary/common/ExceptionMessages.java`
- Modify: `source/src/main/java/com/elibrary/common/BusinessException.java`
- Create: `source/src/test/java/com/elibrary/common/ExceptionMessagesTest.java`
- Create: `source/src/test/java/com/elibrary/common/BusinessExceptionTest.java`

- [ ] **Step 1: 写失败单测**

创建 `ExceptionMessagesTest.java`：

```java
package com.elibrary.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionMessagesTest {

    @Test
    @DisplayName("解析用户文案-WARN级业务异常-应返回原始message")
    void resolveUserMessage_whenWarnBusinessException_shouldReturnOriginalMessage() {
        BusinessException ex = new BusinessException(ExceptionLevel.WARN, "书目不存在");
        assertThat(ExceptionMessages.resolveUserMessage(ex)).isEqualTo("书目不存在");
    }

    @Test
    @DisplayName("解析用户文案-ERROR级业务异常-应返回网络异常文案")
    void resolveUserMessage_whenErrorBusinessException_shouldReturnNetworkMessage() {
        BusinessException ex = new BusinessException(ExceptionLevel.ERROR, "内部细节SQL");
        assertThat(ExceptionMessages.resolveUserMessage(ex))
                .isEqualTo(ExceptionMessages.NETWORK_ERROR);
        assertThat(ExceptionMessages.resolveUserMessage(ex)).doesNotContain("SQL");
    }

    @Test
    @DisplayName("解析用户文案-非业务异常-应返回网络异常文案")
    void resolveUserMessage_whenUnhandledException_shouldReturnNetworkMessage() {
        RuntimeException ex = new RuntimeException("Connection refused redis");
        assertThat(ExceptionMessages.resolveUserMessage(ex))
                .isEqualTo(ExceptionMessages.NETWORK_ERROR);
        assertThat(ExceptionMessages.resolveUserMessage(ex)).doesNotContain("redis");
    }
}
```

创建 `BusinessExceptionTest.java`：

```java
package com.elibrary.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    @DisplayName("默认构造-应使用WARN级别")
    void defaultConstructor_shouldBeWarnLevel() {
        BusinessException ex = new BusinessException("用户名已存在");
        assertThat(ex.getLevel()).isEqualTo(ExceptionLevel.WARN);
        assertThat(ex.getMessage()).isEqualTo("用户名已存在");
    }

    @Test
    @DisplayName("带cause构造-应保留级别与cause")
    void levelMessageCauseConstructor_shouldKeepLevelAndCause() {
        Throwable cause = new IllegalStateException("io");
        BusinessException ex = new BusinessException(ExceptionLevel.ERROR, "PDF 保存失败", cause);
        assertThat(ex.getLevel()).isEqualTo(ExceptionLevel.ERROR);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
```

- [ ] **Step 2: 运行单测确认失败**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q -Dtest=ExceptionMessagesTest,BusinessExceptionTest test
```

Expected: 编译失败或测试失败（类/方法尚不存在）。

- [ ] **Step 3: 实现最小代码**

`ExceptionLevel.java`：

```java
package com.elibrary.common;

public enum ExceptionLevel {
    WARN,
    ERROR
}
```

`ExceptionMessages.java`：

```java
package com.elibrary.common;

public final class ExceptionMessages {

    public static final String NETWORK_ERROR = "网络异常，请稍后重试";

    private ExceptionMessages() {
    }

    public static String resolveUserMessage(BusinessException ex) {
        if (ex.getLevel() == ExceptionLevel.ERROR) {
            return NETWORK_ERROR;
        }
        return ex.getMessage();
    }

    public static String resolveUserMessage(Throwable ex) {
        if (ex instanceof BusinessException) {
            return resolveUserMessage((BusinessException) ex);
        }
        return NETWORK_ERROR;
    }
}
```

`BusinessException.java`：

```java
package com.elibrary.common;

public class BusinessException extends RuntimeException {

    private final ExceptionLevel level;

    public BusinessException(String message) {
        this(ExceptionLevel.WARN, message);
    }

    public BusinessException(ExceptionLevel level, String message) {
        super(message);
        this.level = level == null ? ExceptionLevel.WARN : level;
    }

    public BusinessException(ExceptionLevel level, String message, Throwable cause) {
        super(message, cause);
        this.level = level == null ? ExceptionLevel.WARN : level;
    }

    public ExceptionLevel getLevel() {
        return level;
    }
}
```

- [ ] **Step 4: 运行单测确认通过**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q -Dtest=ExceptionMessagesTest,BusinessExceptionTest test
```

Expected: PASS

- [ ] **Step 5: Commit（默认跳过）**

仅在用户要求时执行。

---

### Task 2: GlobalExceptionHandler 分级与任意异常兜底（TDD）

**Files:**
- Modify: `source/src/main/java/com/elibrary/common/GlobalExceptionHandler.java`
- Create: `source/src/test/java/com/elibrary/common/GlobalExceptionHandlerTest.java`
- Modify: `source/src/test/java/com/elibrary/web/PublicControllerTest.java`（保留 Referer 行为断言；flash 断言可迁到新测试类）

- [ ] **Step 1: 写失败单测**

创建 `GlobalExceptionHandlerTest.java`：

```java
package com.elibrary.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.RedirectView;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("处理业务异常-WARN级别-flash应为原始message")
    void handleBusinessException_whenWarn_shouldFlashOriginalMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        BusinessException ex = new BusinessException("书目不存在");

        Object view = handler.handleBusinessException(ex, request, ra);

        assertThat(((RedirectView) view).getUrl()).isEqualTo("/error");
        assertThat(ra.getFlashAttributes().get("error")).isEqualTo("书目不存在");
    }

    @Test
    @DisplayName("处理业务异常-ERROR级别-flash应为网络异常文案")
    void handleBusinessException_whenError_shouldFlashNetworkMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "http://localhost/physical/1");
        RedirectAttributes ra = new RedirectAttributesModelMap();
        BusinessException ex = new BusinessException(ExceptionLevel.ERROR, "Data truncation");

        Object view = handler.handleBusinessException(ex, request, ra);

        assertThat(((RedirectView) view).getUrl()).isEqualTo("http://localhost/physical/1");
        assertThat(ra.getFlashAttributes().get("error")).isEqualTo(ExceptionMessages.NETWORK_ERROR);
        assertThat(String.valueOf(ra.getFlashAttributes().get("error"))).doesNotContain("truncation");
    }

    @Test
    @DisplayName("处理任意异常-flash应为网络异常文案且不泄露细节")
    void handleException_whenRuntime_shouldFlashNetworkMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        RuntimeException ex = new RuntimeException("Duplicate entry for key PRIMARY");

        Object view = handler.handleException(ex, request, ra);

        assertThat(((RedirectView) view).getUrl()).isEqualTo("/error");
        assertThat(ra.getFlashAttributes().get("error")).isEqualTo(ExceptionMessages.NETWORK_ERROR);
        assertThat(String.valueOf(ra.getFlashAttributes().get("error"))).doesNotContain("PRIMARY");
    }
}
```

- [ ] **Step 2: 运行单测确认失败**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q -Dtest=GlobalExceptionHandlerTest test
```

Expected: FAIL（尚无 `handleException` 或 ERROR flash 行为不正确）。

- [ ] **Step 3: 实现 GlobalExceptionHandler**

替换为：

```java
package com.elibrary.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        String userMessage = ExceptionMessages.resolveUserMessage(ex);
        if (ex.getLevel() == ExceptionLevel.ERROR) {
            log.error("Business error: {}", ex.getMessage(), ex);
        } else {
            log.warn("Business warn: {}", ex.getMessage());
        }
        return redirectWithError(request, redirectAttributes, userMessage);
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        log.error("Unhandled error", ex);
        return redirectWithError(request, redirectAttributes, ExceptionMessages.resolveUserMessage(ex));
    }

    private RedirectView redirectWithError(HttpServletRequest request,
                                           RedirectAttributes redirectAttributes,
                                           String userMessage) {
        redirectAttributes.addFlashAttribute("error", userMessage);
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return new RedirectView(referer);
        }
        return new RedirectView("/error");
    }
}
```

- [ ] **Step 4: 运行相关单测**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q -Dtest=GlobalExceptionHandlerTest,PublicControllerTest test
```

Expected: PASS（`PublicControllerTest.globalExceptionHandler_shouldRedirectByReferer` 仍兼容默认 WARN）。

- [ ] **Step 5: Commit（默认跳过）**

---

### Task 3: AuthController 局部 catch 对齐 + error.html

**Files:**
- Modify: `source/src/main/java/com/elibrary/user/AuthController.java`
- Modify: `source/src/main/resources/templates/error.html`
- Modify: `source/src/test/java/com/elibrary/web/PublicControllerTest.java`

- [ ] **Step 1: 扩展失败单测（注册 ERROR）**

在 `PublicControllerTest` 的注册相关测试附近新增：

```java
@Test
@DisplayName("注册-ERROR级业务异常-字段错误应为网络异常文案")
void register_whenErrorBusinessException_shouldRejectWithNetworkMessage() {
    AuthController controller = new AuthController(userService);
    RegisterForm form = new RegisterForm();
    form.setUsername("alice");
    form.setPassword("secret1");
    BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
    RedirectAttributes ra = new RedirectAttributesModelMap();
    doThrow(new BusinessException(ExceptionLevel.ERROR, "内部错误"))
            .when(userService).register(anyString(), anyString());

    assertThat(controller.register(form, bindingResult, ra)).isEqualTo("auth/register");
    assertThat(bindingResult.getFieldError("username").getDefaultMessage())
            .isEqualTo(ExceptionMessages.NETWORK_ERROR);
}
```

（按文件内已有 import 补齐 `ExceptionLevel`、`ExceptionMessages`。）

- [ ] **Step 2: 运行确认失败**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q -Dtest=PublicControllerTest#register_whenErrorBusinessException_shouldRejectWithNetworkMessage test
```

Expected: FAIL（仍使用 `e.getMessage()` →「内部错误」）。

- [ ] **Step 3: 改 AuthController + error.html**

`AuthController.register` catch 块改为：

```java
        } catch (BusinessException e) {
            bindingResult.rejectValue("username", "duplicate",
                    ExceptionMessages.resolveUserMessage(e));
            return "auth/register";
        }
```

`error.html` 无 flash 时的默认句改为与常量一致：

```html
  <p th:unless="${error} or ${message}">网络异常，请稍后重试</p>
```

- [ ] **Step 4: 运行 PublicControllerTest**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q -Dtest=PublicControllerTest test
```

Expected: PASS

- [ ] **Step 5: Commit（默认跳过）**

---

### Task 4: 全量验证

- [ ] **Step 1: 跑全量测试**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q test
```

Expected: BUILD SUCCESS，全部测试通过。

- [ ] **Step 2: 若启用 Jacoco，确认 common 包新增代码增量覆盖达标**

```bash
cd /Users/yqg/Desktop/ai/future/source && mvn -q test jacoco:report
```

查看 `target/site/jacoco/com.elibrary.common/` 中 `BusinessException`、`ExceptionMessages`、`GlobalExceptionHandler`、`ExceptionLevel` 行/分支覆盖是否合理（目标：新增/修改代码 ≥ 60%）。

- [ ] **Step 3: 对照验收清单**

- [ ] WARN `BusinessException` → 页面/flash 原文  
- [ ] ERROR `BusinessException` →「网络异常，请稍后重试」  
- [ ] 任意非业务异常 → 同上固定文案，不泄露 SQL/Redis 细节  
- [ ] 现有业务抛点（含 PDF 保存失败）行为不变（默认 WARN）  
- [ ] 全量测试通过  

---

## Spec 覆盖自检

| Spec 要求 | 对应 Task |
|-----------|-----------|
| `ExceptionLevel` WARN/ERROR | Task 1 |
| `BusinessException` 默认 WARN + 显式级别构造 | Task 1 |
| 固定文案「网络异常，请稍后重试」 | Task 1 `ExceptionMessages` |
| Handler 处理 BusinessException 分级 | Task 2 |
| Handler 兜底任意 Exception | Task 2 |
| 不暴露底层细节 | Task 1/2 断言 |
| AuthController 局部 catch 对齐 | Task 3 |
| PDF 等友好文案保持 WARN / 不改 Service | 明确不改文件 |
| error.html 对齐 | Task 3 |
| 单测 + 覆盖率 | Task 1–4 |
