package org.example.xyyx.controller;

import org.example.xyyx.service.CurrentUserService;
import org.example.xyyx.service.CurrentUserService.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = currentUserService.requireUser(jwt);
        return Map.of(
                "username", user.username(),
                "role", user.role(),
                "subject", jwt.getSubject(),
                "audience", jwt.getAudience()
        );
    }
}
