package com.elibrary.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionMessagesTest {

    @Test
    @DisplayName("解析用户文案-WARN级业务异常-应返回原始message")
    void resolveUserMessage_whenWarnBusinessException_shouldReturnOriginalMessage() {
        BusinessException ex = new BusinessException(ExceptionLevel.WARN, "书目不存在");

        String userMessage = ExceptionMessages.resolveUserMessage(ex);

        assertThat(userMessage).isEqualTo("书目不存在");
    }

    @Test
    @DisplayName("解析用户文案-ERROR级业务异常-应返回网络异常文案")
    void resolveUserMessage_whenErrorBusinessException_shouldReturnNetworkMessage() {
        BusinessException ex = new BusinessException(ExceptionLevel.ERROR, "内部细节SQL");

        String userMessage = ExceptionMessages.resolveUserMessage(ex);

        assertThat(userMessage).isEqualTo(ExceptionMessages.NETWORK_ERROR);
        assertThat(userMessage).doesNotContain("SQL");
    }

    @Test
    @DisplayName("解析用户文案-非业务异常-应返回网络异常文案")
    void resolveUserMessage_whenUnhandledException_shouldReturnNetworkMessage() {
        RuntimeException ex = new RuntimeException("Connection refused redis");

        String userMessage = ExceptionMessages.resolveUserMessage(ex);

        assertThat(userMessage).isEqualTo(ExceptionMessages.NETWORK_ERROR);
        assertThat(userMessage).doesNotContain("redis");
    }
}
