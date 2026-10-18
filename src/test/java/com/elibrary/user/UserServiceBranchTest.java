package com.elibrary.user;

import com.elibrary.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceBranchTest {

    @Mock
    UserMapper userMapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("列表用户-应返回mapper结果")
    void listUsers_shouldReturnAll() {
        User u = new User();
        u.setId(1L);
        u.setUsername("a");
        when(userMapper.listAll()).thenReturn(Collections.singletonList(u));

        List<User> users = userService.listUsers();

        assertThat(users).hasSize(1);
        assertThat(users.get(0).getUsername()).isEqualTo("a");
    }

    @Test
    @DisplayName("requireById-当用户不存在时-应抛业务异常")
    void requireById_whenMissing_shouldThrow() {
        when(userMapper.findById(9L)).thenReturn(null);

        assertThatThrownBy(() -> userService.requireById(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("撤销管理员-当目标为超管或非管理员时-应拒绝")
    void revokeAdmin_whenSuperAdminOrNotAdmin_shouldThrow() {
        User superAdmin = new User();
        superAdmin.setId(1L);
        superAdmin.setRole(Role.SUPER_ADMIN);
        when(userMapper.findById(1L)).thenReturn(superAdmin);
        assertThatThrownBy(() -> userService.revokeAdmin(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");

        User reader = new User();
        reader.setId(2L);
        reader.setRole(Role.READER);
        when(userMapper.findById(2L)).thenReturn(reader);
        assertThatThrownBy(() -> userService.revokeAdmin(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是管理员");
        verify(userMapper, never()).updateRole(anyLong(), any());
    }

    @Test
    @DisplayName("按用户名查找-应透传mapper结果")
    void findByUsername_shouldDelegate() {
        User u = new User();
        u.setUsername("bob");
        when(userMapper.findByUsername("bob")).thenReturn(u);

        assertThat(userService.findByUsername("bob").getUsername()).isEqualTo("bob");
        assertThat(userService.findByUsername("missing")).isNull();
    }
}
