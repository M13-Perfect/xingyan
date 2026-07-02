package org.example.xyyx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            org.example.xyyx.service.TokenDenylistService tokenDenylistService,
            org.example.xyyx.service.CurrentUserService currentUserService) throws Exception {
        TokenDenylistFilter tokenDenylistFilter = new TokenDenylistFilter(tokenDenylistService, currentUserService);
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/security/public-key").permitAll()
                        // 建立会话：前端把 token 交来换 HttpOnly Cookie，此刻还没有 Cookie，故放行；端点内部用 JWKS 校验 token。
                        .requestMatchers(HttpMethod.POST, "/api/auth/session").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, request.getAttribute(RequestIdFilter.ATTRIBUTE), "UNAUTHENTICATED", "登录状态已失效，请重新登录", 401))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, request.getAttribute(RequestIdFilter.ATTRIBUTE), "PHONE_VIEW_DENIED", "你没有权限查看完整手机号", 403))
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        // 优先从 HttpOnly Cookie 取 JWT，取不到再回退 Authorization 头（迁移期兼容）。
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(Customizer.withDefaults()))
                // 令牌撤销：认证成功后立刻检查拒绝名单，命中即 401（登出后 token 立即失效）。
                .addFilterAfter(tokenDenylistFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, Object requestId, String code, String message, int status) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safeRequestId = requestId == null ? "" : requestId.toString();
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"requestId\":\"" + safeRequestId + "\"}");
    }
}
