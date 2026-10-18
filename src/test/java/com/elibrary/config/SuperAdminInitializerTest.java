package com.elibrary.config;

import com.elibrary.user.Role;
import com.elibrary.user.User;
import com.elibrary.user.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminInitializerTest {

    @Mock
    UserMapper userMapper;
    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("启动种子-当超管不存在时-应插入SUPER_ADMIN")
    void run_whenSuperAdminMissing_shouldInsertSuperAdmin() throws Exception {
        // Arrange
        LibraryProperties props = new LibraryProperties();
        props.getSuperAdmin().setUsername("superadmin");
        props.getSuperAdmin().setPassword("ChangeMe123!");
        Path dir = Files.createTempDirectory("pdf");
        props.getPdf().setStorageDir(dir.toString());

        when(userMapper.findByUsername("superadmin")).thenReturn(null);
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("HASH");

        SuperAdminInitializer initializer =
                new SuperAdminInitializer(props, userMapper, passwordEncoder);

        // Act
        initializer.run();

        // Assert
        verify(userMapper).insert(argThat(u ->
                u.getUsername().equals("superadmin")
                        && u.getRole() == Role.SUPER_ADMIN
                        && u.getPasswordHash().equals("HASH")));
    }

    @Test
    @DisplayName("启动种子-当超管已存在时-不应再次插入")
    void run_whenSuperAdminExists_shouldNotInsert() throws Exception {
        // Arrange
        LibraryProperties props = new LibraryProperties();
        props.getSuperAdmin().setUsername("superadmin");
        props.getSuperAdmin().setPassword("ChangeMe123!");
        Path dir = Files.createTempDirectory("pdf");
        props.getPdf().setStorageDir(dir.toString());

        when(userMapper.findByUsername("superadmin")).thenReturn(new User());

        SuperAdminInitializer initializer =
                new SuperAdminInitializer(props, userMapper, passwordEncoder);

        // Act
        initializer.run();

        // Assert
        verify(userMapper, never()).insert(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
