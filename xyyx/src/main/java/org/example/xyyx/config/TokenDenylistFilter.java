package org.example.xyyx.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.xyyx.service.CurrentUserService;
import org.example.xyyx.service.TokenDenylistService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在 Bearer token 认证之后运行：若当前 token 的会话指纹已被登出加入拒绝名单，直接返回 401，
 * 不再进入业务代码。放在网关层（而不是只在 requireUser 里判）是为了覆盖任何将来忘记调用
 * requireUser 的端点——撤销不能依赖每个开发者都记得手动检查。
 * 不加 @Component：由 SecurityConfig 显式 new 并注册进安全过滤链，避免被 Spring Boot 额外注册到普通 servlet 链而重复运行。
 */
public class TokenDenylistFilter extends OncePerRequestFilter {

    private final TokenDenylistService tokenDenylistService;
    private final CurrentUserService currentUserService;

    public TokenDenylistFilter(TokenDenylistService tokenDenylistService, CurrentUserService currentUserService) {
        this.tokenDenylistService = tokenDenylistService;
        this.currentUserService = currentUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String revocationKey = currentUserService.tokenRevocationKey(jwtAuth.getToken()).orElse(null);
            if (revocationKey != null && tokenDenylistService.isDenied(revocationKey)) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safeRequestId = requestId == null ? "" : requestId.toString();
        response.getWriter().write(
                "{\"code\":\"TOKEN_REVOKED\",\"message\":\"登录状态已失效，请重新登录\",\"requestId\":\"" + safeRequestId + "\"}");
    }
}
