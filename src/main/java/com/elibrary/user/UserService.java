package com.elibrary.user;

import com.elibrary.common.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String username, String rawPassword) {
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.READER);
        userMapper.insert(user);
        return user;
    }

    public List<User> listUsers() {
        return userMapper.listAll();
    }

    public User requireById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Transactional
    public void grantAdmin(Long userId) {
        User user = requireById(userId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("不能变更超级管理员角色");
        }
        userMapper.updateRole(userId, Role.ADMIN);
        user.setRole(Role.ADMIN);
    }

    @Transactional
    public void revokeAdmin(Long userId) {
        User user = requireById(userId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BusinessException("不能撤销超级管理员");
        }
        if (user.getRole() != Role.ADMIN) {
            throw new BusinessException("目标用户不是管理员");
        }
        userMapper.updateRole(userId, Role.READER);
        user.setRole(Role.READER);
    }

    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
}
