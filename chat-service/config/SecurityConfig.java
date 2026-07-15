package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // @Bean
    // public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    //     http
    //             .authorizeHttpRequests(authorize -> authorize
    //                     .anyRequest().permitAll() // Allow all requests to any endpoint without authentication
    //             )
    //             .csrf(csrf -> csrf.disable()); // Explicitly disable CSRF protection

    //     return http.build();
    // }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. 显式禁用 CSRF（必须，否则 POST 请求会报 403）
            .csrf(csrf -> csrf.disable())
            
            // 2. 显式禁用跨域限制（交给 WebMvc 或 Nacos/Gateway 处理）
            .cors(cors -> cors.disable())
            
            // 3. 核心：放行所有路径
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            
            // 4. 关键：显式禁用默认的登录表单和 Basic 认证弹窗
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}