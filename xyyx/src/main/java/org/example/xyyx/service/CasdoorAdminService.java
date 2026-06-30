package org.example.xyyx.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 负责把本地员工账号同步到 Casdoor。
 * 鉴权方式：HTTP Basic，用户名=clientId，密码=clientSecret。
 */
@Service
public class CasdoorAdminService {

    private final RestClient restClient;
    private final String basicAuthHeader;
    private final String organization;
    private final String application;

    public CasdoorAdminService(
            @Value("${casdoor.endpoint}") String endpoint,
            @Value("${casdoor.organization}") String organization,
            @Value("${casdoor.application}") String application,
            @Value("${casdoor.client-id}") String clientId,
            @Value("${casdoor.client-secret}") String clientSecret) {
        this.organization = organization;
        this.application = application;
        String credentials = clientId + ":" + clientSecret;
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder().baseUrl(endpoint).build();
    }

    /**
     * 在 Casdoor 中创建用户。失败抛出 IllegalStateException，由调用方触发本地事务回滚。
     * 幂等：若用户已存在（msg 含 "exist"），视为成功直接返回。
     */
    public void createUser(String username, String plainPassword) {
        Map<String, Object> body = new HashMap<>();
        body.put("owner", organization);
        body.put("name", username);
        body.put("type", "normal-user");
        body.put("password", plainPassword);
        body.put("displayName", username);
        body.put("signupApplication", application);

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/api/add-user")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Casdoor 创建用户失败: " + e.getMessage(), e);
        }

        String status = response == null ? null : asString(response.get("status"));
        if ("ok".equals(status)) {
            return;
        }
        String msg = response == null ? null : asString(response.get("msg"));
        if (msg != null && msg.toLowerCase().contains("exist")) {
            // 用户已存在视为成功（幂等）。
            return;
        }
        throw new IllegalStateException("Casdoor 创建用户失败: " + msg);
    }

    /**
     * 在 Casdoor 中删除用户。尽力而为：失败只吞掉不抛，本地删除才是权威。
     */
    public void deleteUser(String username) {
        Map<String, Object> body = new HashMap<>();
        body.put("owner", organization);
        body.put("name", username);
        try {
            restClient.post()
                    .uri("/api/delete-user")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            // 尽力而为：Casdoor 删除失败不影响本地删除结果，吞掉异常。
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
