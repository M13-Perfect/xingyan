package org.example.xyyx.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
    public void createUser(String username, String plainPassword, String displayName) {
        Map<String, Object> body = new HashMap<>();
        body.put("owner", organization);
        body.put("name", username);
        body.put("type", "normal-user");
        body.put("password", plainPassword);
        body.put("displayName", displayName);
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
     * 同步修改 Casdoor 用户密码（管理员重置 / 用户自助改密码时调用）。
     */
    public void updateUserPassword(String username, String plainPassword) {
        updateUserFields(username, "password", Map.of(
                "owner", organization,
                "name", username,
                "password", plainPassword
        ));
    }

    /**
     * 同步修改 Casdoor 用户昵称（displayName）。
     */
    public void updateUserDisplayName(String username, String displayName) {
        updateUserFields(username, "displayName", Map.of(
                "owner", organization,
                "name", username,
                "displayName", displayName
        ));
    }

    /**
     * columns 必须显式指定，否则 Casdoor /api/update-user 会用请求体里没带到的字段覆盖成空值。
     */
    private void updateUserFields(String username, String columns, Map<String, Object> body) {
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/update-user")
                            .queryParam("id", organization + "/" + username)
                            .queryParam("columns", columns)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Casdoor 更新用户失败: " + e.getMessage(), e);
        }
        String status = response == null ? null : asString(response.get("status"));
        if (!"ok".equals(status)) {
            String msg = response == null ? null : asString(response.get("msg"));
            throw new IllegalStateException("Casdoor 更新用户失败: " + msg);
        }
    }

    /**
     * 批量获取组织下所有用户的昵称（displayName），用于工作台按 owner 展示昵称。
     * 尽力而为：Casdoor 不可用或返回异常时返回空 Map，调用方应回退到用户名本身。
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> listDisplayNames() {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/get-users")
                            .queryParam("owner", organization)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            return Map.of();
        }
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> rawList)) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> u)) continue;
            String name = asString(u.get("name"));
            String displayName = asString(u.get("displayName"));
            if (name != null && displayName != null && !displayName.isBlank()) {
                result.put(name, displayName);
            }
        }
        return result;
    }

    /**
     * 取单个用户的昵称/头像（个人中心展示用）。用 Basic(admin) 授权，避免前端持有可读 token 直连 Casdoor。
     * 尽力而为：Casdoor 不可用或返回异常时返回空 Map，调用方回退到用户名。只放非空字段，便于调用方 getOrDefault。
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getAccount(String username) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/get-user")
                            .queryParam("id", organization + "/" + username)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            return Map.of();
        }
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map<?, ?> user)) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        String displayName = asString(user.get("displayName"));
        String avatar = asString(user.get("avatar"));
        if (displayName != null && !displayName.isBlank()) result.put("displayName", displayName);
        if (avatar != null && !avatar.isBlank()) result.put("avatar", avatar);
        return result;
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
