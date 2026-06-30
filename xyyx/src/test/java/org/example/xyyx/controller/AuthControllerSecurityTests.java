package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/jwks"
})
@AutoConfigureMockMvc
class AuthControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void meRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meMapsPreferredUsernameToLocalRole() throws Exception {
        when(jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                "alice"
        )).thenReturn(List.of("staff"));

        mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject("casdoor-user-id")
                        .claim("preferred_username", "alice")
                        .audience(List.of("xyyx-web")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("staff"));
    }

    @Test
    void meFallsBackToCasdoorNameClaimWhenPreferredUsernameIsMissing() throws Exception {
        when(jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                "18007300157"
        )).thenReturn(List.of("staff"));

        mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token
                        .subject("casdoor-user-id")
                        .claim("name", "18007300157")
                        .audience(List.of("xyyx-web")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("18007300157"))
                .andExpect(jsonPath("$.role").value("staff"));
    }

    @Test
    void usersEndpointRejectsForgedOperatorUsernameWhenJwtUserIsNotAdmin() throws Exception {
        when(jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                "staff-user"
        )).thenReturn(List.of("staff"));
        when(jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                "admin"
        )).thenReturn(List.of("admin"));

        mockMvc.perform(get("/api/users")
                        .param("operatorUsername", "admin")
                        .with(jwt().jwt(token -> token
                                .subject("casdoor-staff-id")
                                .claim("preferred_username", "staff-user")
                                .audience(List.of("xyyx-web")))))
                .andExpect(status().isForbidden());
    }
}
