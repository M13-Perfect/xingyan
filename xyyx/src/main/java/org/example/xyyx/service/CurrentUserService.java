package org.example.xyyx.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CurrentUserService {

    private final JdbcTemplate jdbcTemplate;

    public CurrentUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CurrentUser requireUser(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Casdoor token");
        }

        String username = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name")
        );
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Casdoor token missing username claim");
        }

        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                username
        );
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Casdoor user is not registered locally");
        }

        return new CurrentUser(username, roles.get(0));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public CurrentUser requireAdmin(Jwt jwt) {
        CurrentUser user = requireUser(jwt);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission: admin only");
        }
        return user;
    }

    public record CurrentUser(String username, String role) {
        public boolean isAdmin() {
            return "admin".equalsIgnoreCase(role);
        }
    }
}
