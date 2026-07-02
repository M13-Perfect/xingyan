package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pattern B 会话建立端点的安全属性：只有 JWKS 校验通过的 token 才会被换成 HttpOnly Cookie；
 * 非法 token 必须 401 且绝不下发 Cookie；缺 token 为 400。
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/jwks"
})
@AutoConfigureMockMvc
class AuthSessionControllerTests {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private JdbcTemplate jdbcTemplate;
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void validTokenMintsHttpOnlyScopedCookie() throws Exception {
        Jwt jwt = Jwt.withTokenValue("good-token")
                .header("alg", "RS256")
                .claim("preferred_username", "alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode("good-token")).thenReturn(jwt);
        when(jdbcTemplate.queryForList("SELECT role FROM user WHERE username = ?", String.class, "alice"))
                .thenReturn(List.of("staff"));

        mockMvc.perform(post("/api/auth/session")
                        .contentType("application/json")
                        .content("{\"accessToken\":\"good-token\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("XYYX_AT=good-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api")));
    }

    @Test
    void invalidTokenIsRejectedWithoutMintingCookie() throws Exception {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid signature"));

        mockMvc.perform(post("/api/auth/session")
                        .contentType("application/json")
                        .content("{\"accessToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void missingTokenIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
