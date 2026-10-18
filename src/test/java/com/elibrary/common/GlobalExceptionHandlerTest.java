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
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
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

    @Test
    @DisplayName("处理业务异常-跨站Referer-应回退到error页")
    void handleBusinessException_whenCrossOriginReferer_shouldRedirectToErrorPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.addHeader("Referer", "https://evil.example/phish");
        RedirectAttributes ra = new RedirectAttributesModelMap();
        BusinessException ex = new BusinessException(ExceptionLevel.ERROR, "Data truncation");

        Object view = handler.handleBusinessException(ex, request, ra);

        assertThat(((RedirectView) view).getUrl()).isEqualTo("/error");
        assertThat(ra.getFlashAttributes().get("error")).isEqualTo(ExceptionMessages.NETWORK_ERROR);
    }
}
