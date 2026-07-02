package org.example.xyyx.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

/**
 * 资源服务器从 HttpOnly Cookie（XYYX_AT）里取 JWT，而不是只认 Authorization 头。
 * 迁移期保留 Authorization 头回退：Cookie 不存在时仍接受头（老客户端 / 回滚只需还原前端即可继续用头认证）。
 * 前端已改为纯 Cookie；待全部客户端切换、旧会话过期后，可删除头回退，让 token 只存在于 HttpOnly Cookie。
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    public static final String COOKIE_NAME = "XYYX_AT";

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return headerResolver.resolve(request);
    }
}
