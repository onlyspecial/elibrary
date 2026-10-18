package com.elibrary.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String FLASH_ERROR_KEY = "error";
    private static final String ERROR_PATH = "/error";

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
        redirectAttributes.addFlashAttribute(FLASH_ERROR_KEY, userMessage);
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty() && isSameOriginReferer(request, referer)) {
            return new RedirectView(referer);
        }
        return new RedirectView(ERROR_PATH);
    }

    private boolean isSameOriginReferer(HttpServletRequest request, String referer) {
        try {
            URI uri = URI.create(referer);
            if (!uri.isAbsolute()) {
                return false;
            }
            String refererScheme = uri.getScheme();
            String refererHost = uri.getHost();
            if (refererScheme == null || refererHost == null) {
                return false;
            }
            return refererScheme.equalsIgnoreCase(request.getScheme())
                    && refererHost.equalsIgnoreCase(request.getServerName())
                    && effectivePort(uri) == request.getServerPort();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return 80;
    }
}
