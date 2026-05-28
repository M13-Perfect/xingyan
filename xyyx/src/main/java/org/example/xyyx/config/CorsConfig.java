package org.example.xyyx.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 针对所有接口路径生效
        registry.addMapping("/**")
                // 允许的前端源（在本地开发时，Vue 3 通常运行在 5173 或 8080 端口）
                // 使用 allowedOriginPatterns 替代 allowedOrigins 兼容性更好，特别是配合 allowCredentials 时
                .allowedOriginPatterns("*")
                // 允许的 HTTP 请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许携带的请求头
                .allowedHeaders("*")
                // 是否允许前端携带凭证（如 Cookie 或 Authorization 里的 token）
                .allowCredentials(true)
                // 预检请求的缓存时间（秒），避免每次真实请求前都发送 OPTIONS 请求
                .maxAge(3600);
    }
}