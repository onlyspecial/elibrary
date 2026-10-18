package com.elibrary.user;

import com.elibrary.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserMapper userMapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("注册-当用户名不存在时-应创建READER并哈希密码")
    void register_whenUsernameNotExists_shouldCreateReaderWithHashedPassword() {
        // Arrange
        when(userMapper.findByUsername("alice")).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("HASH");
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return 1;
        });

        // Act
        User created = userService.register("alice", "secret");

        // Assert
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getRole()).isEqualTo(Role.READER);
        assertThat(created.getPasswordHash()).isEqualTo("HASH");
        verify(passwordEncoder).encode("secret");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("注册-当用户名已存在时-应抛业务异常")
    void register_whenUsernameExists_shouldThrowBusinessException() {
        // Arrange
        User existing = new User();
        existing.setUsername("alice");
        when(userMapper.findByUsername("alice")).thenReturn(existing);

        // Act & Assert
        assertThatThrownBy(() -> userService.register("alice", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("授予管理员-当目标为READER时-应改为ADMIN")
    void grantAdmin_whenTargetIsReader_shouldUpdateToAdmin() {
        // Arrange
        User target = new User();
        target.setId(2L);
        target.setRole(Role.READER);
        when(userMapper.findById(2L)).thenReturn(target);

        // Act
        userService.grantAdmin(2L);

        // Assert
        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        verify(userMapper).updateRole(2L, Role.ADMIN);
    }

    @Test
    @DisplayName("撤销管理员-当目标为ADMIN时-应改回READER")
    void revokeAdmin_whenTargetIsAdmin_shouldUpdateToReader() {
        // Arrange
        User target = new User();
        target.setId(2L);
        target.setRole(Role.ADMIN);
        when(userMapper.findById(2L)).thenReturn(target);

        // Act
        userService.revokeAdmin(2L);

        // Assert
        assertThat(target.getRole()).isEqualTo(Role.READER);
        verify(userMapper).updateRole(2L, Role.READER);
    }

    @Test
    @DisplayName("授予管理员-当目标为SUPER_ADMIN时-应拒绝")
    void grantAdmin_whenTargetIsSuperAdmin_shouldThrow() {
        // Arrange
        User target = new User();
        target.setId(1L);
        target.setRole(Role.SUPER_ADMIN);
        when(userMapper.findById(1L)).thenReturn(target);

        // Act & Assert
        assertThatThrownBy(() -> userService.grantAdmin(1L))
                .isInstanceOf(BusinessException.class);
        verify(userMapper, never()).updateRole(anyLong(), any());
    }
}
