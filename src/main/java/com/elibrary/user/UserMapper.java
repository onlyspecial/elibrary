package com.elibrary.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    int insert(User user);

    int updateRole(@Param("id") Long id, @Param("role") Role role);

    List<User> listAll();

    List<User> listNonSuperAdmins();
}
