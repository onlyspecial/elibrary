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
    @DisplayName("null级别构造-应回退为WARN")
    void nullLevelConstructor_shouldFallbackToWarn() {
        BusinessException ex = new BusinessException(null, "msg");

        assertThat(ex.getLevel()).isEqualTo(ExceptionLevel.WARN);
        assertThat(ex.getMessage()).isEqualTo("msg");
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
