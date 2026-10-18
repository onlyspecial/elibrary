package com.elibrary.config;

import com.elibrary.user.Role;
import com.elibrary.user.User;
import com.elibrary.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class SuperAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final LibraryProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminInitializer(LibraryProperties properties,
                                 UserMapper userMapper,
                                 PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        run();
    }

    /**
     * Package-visible for unit tests (no Spring ApplicationArguments needed).
     */
    void run() throws Exception {
        Path dir = Paths.get(properties.getPdf().getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        log.info("PDF storage directory ready: {}", dir);

        String username = properties.getSuperAdmin().getUsername();
        if (userMapper.findByUsername(username) == null) {
            User user = new User();
            user.setUsername(username);
            // Encode only — never log plaintext password
            user.setPasswordHash(passwordEncoder.encode(properties.getSuperAdmin().getPassword()));
            user.setRole(Role.SUPER_ADMIN);
            userMapper.insert(user);
            log.info("Super admin user created: {}", username);
        }
    }
}
