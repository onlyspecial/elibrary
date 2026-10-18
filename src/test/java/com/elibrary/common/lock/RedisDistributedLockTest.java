package com.elibrary.common.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks RedisDistributedLock lock;

    @Test
    @DisplayName("抢锁-当key不存在时-应返回token")
    void tryLock_whenKeyAbsent_shouldReturnToken() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("borrow:copy:1"), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);

        // Act
        Optional<String> token = lock.tryLock("borrow:copy:1", Duration.ofSeconds(10));

        // Assert
        assertThat(token).isPresent();
        assertThat(token.get()).isNotBlank();
    }

    @Test
    @DisplayName("抢锁-当key已存在时-应返回empty")
    void tryLock_whenKeyPresent_shouldReturnEmpty() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        // Act
        Optional<String> token = lock.tryLock("borrow:copy:1", Duration.ofSeconds(10));

        // Assert
        assertThat(token).isEmpty();
    }

    @Test
    @DisplayName("释放锁-当token匹配时-应删除key")
    void unlock_whenTokenMatches_shouldDeleteKey() {
        // Arrange
        when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("borrow:copy:1")), eq("tok")))
                .thenReturn(1L);

        // Act
        lock.unlock("borrow:copy:1", "tok");

        // Assert
        verify(redisTemplate).execute(any(RedisScript.class), eq(Collections.singletonList("borrow:copy:1")), eq("tok"));
    }
}
