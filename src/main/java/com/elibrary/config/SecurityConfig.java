package com.elibrary.config;

import com.elibrary.user.LibraryUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    LibraryUserDetailsService userDetailsService) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/", "/login", "/register", "/css/**", "/error").permitAll()
                .antMatchers(HttpMethod.GET, "/physical/**", "/digital/**").permitAll()
                .antMatchers("/admin/users/**").hasRole("SUPER_ADMIN")
                .antMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .antMatchers("/borrow/**", "/my-borrows").authenticated()
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            .and()
            .headers()
                .frameOptions().sameOrigin()
            .and()
            .userDetailsService(userDetailsService);
        return http.build();
    }
}
