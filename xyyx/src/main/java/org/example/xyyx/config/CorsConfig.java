package org.example.xyyx.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // 显式白名单，逗号分隔。默认只放本地开发源；生产用 XYYX_CORS_ALLOWED_ORIGINS 覆盖为真实前端域名。
    // 逗号分隔。生产/开发前端与 /api 实际是同源（反向代理 / Vite 代理），CORS 只是"直连 :8080"时的安全网。
    @Value("${xyyx.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 绝不能用 allowedOriginPatterns("*") + allowCredentials(true)：那会把任意来源都当可信并允许携带凭证，
        // 一旦 token 改走 Cookie 即成高危 CSRF/数据窃取面。这里收敛为有限白名单。
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
