package org.example.xyyx.controller;

import org.example.xyyx.entity.Survey;
import org.example.xyyx.mapper.SurveyMapper;
import org.example.xyyx.service.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class SurveyController {

    @Autowired
    private SurveyMapper surveyMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CryptoService cryptoService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/security/public-key")
    public Map<String, Object> getPublicKey() {
        Map<String, Object> result = new HashMap<>();
        result.put("publicKey", cryptoService.getPublicKeyBase64());
        result.put("algorithm", "RSA-OAEP-256");
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        Map<String, Object> result = new HashMap<>();
        String username = loginData.get("username");
        String encryptedPassword = loginData.get("password");
        if (username == null || username.isBlank() || encryptedPassword == null || encryptedPassword.isBlank()) {
            result.put("success", false);
            result.put("message", "账号或密码不能为空");
            return result;
        }

        String plainPassword;
        try {
            plainPassword = cryptoService.decryptPassword(encryptedPassword);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "密码解密失败");
            return result;
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT password, role FROM user WHERE username = ?",
                username
        );
        if (users.isEmpty()) {
            result.put("success", false);
            result.put("message", "账号或密码错误");
            return result;
        }

        Map<String, Object> dbUser = users.get(0);
        String passwordHash = asString(dbUser.get("password"));
        if (passwordHash == null || !passwordEncoder.matches(plainPassword, passwordHash)) {
            result.put("success", false);
            result.put("message", "账号或密码错误");
            return result;
        }

        result.put("success", true);
        result.put("role", asString(dbUser.get("role")));
        result.put("username", username);
        return result;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers(@RequestParam String operatorUsername) {
        ensureAdminOperator(operatorUsername);
        return jdbcTemplate.queryForList("SELECT id, username, role FROM user WHERE role != 'admin'");
    }

    @PostMapping("/users")
    public String addUser(@RequestBody Map<String, String> payload) {
        ensureAdminOperator(payload.get("operatorUsername"));
        String username = payload.get("username");
        String encryptedPassword = payload.get("password");
        String role = payload.getOrDefault("role", "staff");
        if (username == null || username.isBlank()) return "账号不能为空";
        if (encryptedPassword == null || encryptedPassword.isBlank()) return "初始密码不能为空";
        if ("admin".equalsIgnoreCase(role)) return "禁止创建管理员账号";

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) return "账号已存在";

        String plainPassword;
        try {
            plainPassword = cryptoService.decryptPassword(encryptedPassword);
        } catch (IllegalArgumentException e) {
            return "密码解密失败";
        }

        if (plainPassword.length() < 6) return "密码长度至少 6 位";

        String passwordHash = passwordEncoder.encode(plainPassword);
        jdbcTemplate.update("INSERT INTO user (username, password, role) VALUES (?, ?, ?)", username, passwordHash, role);
        return "员工添加成功";
    }

    @DeleteMapping("/users/{id}")
    public String deleteUser(@PathVariable Long id, @RequestParam String operatorUsername) {
        ensureAdminOperator(operatorUsername);
        List<Map<String, Object>> targetUsers = jdbcTemplate.queryForList("SELECT role FROM user WHERE id = ?", id);
        if (targetUsers.isEmpty()) return "员工不存在";
        if ("admin".equals(asString(targetUsers.get(0).get("role")))) return "禁止删除管理员账号";

        int affected = jdbcTemplate.update("DELETE FROM user WHERE id = ?", id);
        return affected > 0 ? "员工删除成功" : "员工不存在";
    }

    @PutMapping("/users/{id}/password")
    public String updateUserPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        ensureAdminOperator(payload.get("operatorUsername"));
        String encryptedPassword = payload.get("password");
        if (encryptedPassword == null || encryptedPassword.isBlank()) return "新密码不能为空";

        List<Map<String, Object>> targetUsers = jdbcTemplate.queryForList("SELECT role FROM user WHERE id = ?", id);
        if (targetUsers.isEmpty()) return "员工不存在";
        if ("admin".equals(asString(targetUsers.get(0).get("role")))) return "禁止修改管理员密码";

        String plainPassword;
        try {
            plainPassword = cryptoService.decryptPassword(encryptedPassword);
        } catch (IllegalArgumentException e) {
            return "密码解密失败";
        }
        if (plainPassword.length() < 6) return "密码长度至少 6 位";

        String passwordHash = passwordEncoder.encode(plainPassword);
        jdbcTemplate.update("UPDATE user SET password = ? WHERE id = ?", passwordHash, id);
        return "员工密码修改成功";
    }

    @GetMapping("/surveys/pending-count")
    public int getPendingCount(@RequestParam String username, @RequestParam String role) {
        if ("admin".equals(role)) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey WHERE status = '未处理'", Integer.class);
        } else {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM survey WHERE status = '未处理' AND (owner = ? OR visibility = 'PUBLIC' OR FIND_IN_SET(?, shared_users) > 0)",
                    Integer.class,
                    username,
                    username
            );
        }
    }

    @GetMapping("/surveys")
    public Map<String, Object> getSurveys(
            @RequestParam String username,
            @RequestParam String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        int offset = (page - 1) * size;
        List<Survey> list;
        int total;
        String queryStatus = ("全部".equals(status) || status == null || status.isEmpty()) ? null : status;

        if ("admin".equals(role)) {
            list = surveyMapper.selectAdminPaged(queryStatus, keyword, city, size, offset);
            total = surveyMapper.countAdmin(queryStatus, keyword, city);
        } else {
            list = surveyMapper.selectStaffPaged(username, queryStatus, keyword, city, size, offset);
            total = surveyMapper.countStaff(username, queryStatus, keyword, city);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", list);
        response.put("total", total);
        response.put("pages", (int) Math.ceil((double) total / size));
        return response;
    }

    @PostMapping("/surveys")
    public Map<String, Object> addSurvey(@RequestBody Map<String, Object> params) {
        Map<String, Object> res = new HashMap<>();
        try {
            Survey s = new Survey();
            s.setName((String) params.get("name"));
            s.setPhone(params.get("phone") != null && !params.get("phone").toString().isEmpty() ? params.get("phone").toString() : null);
            s.setWechat(params.get("wechat") != null && !params.get("wechat").toString().isEmpty() ? params.get("wechat").toString() : null);
            s.setCity((String) params.get("city"));
            s.setProject((String) params.get("project"));
            s.setBudget((String) params.get("budget"));
            s.setRemarks((String) params.get("remarks"));
            s.setOwner((String) params.get("owner"));
            int days = Integer.parseInt(params.getOrDefault("remindDays", 3).toString());
            s.setNextSurveyDate(LocalDateTime.now().plusDays(days));
            surveyMapper.insert(s);
            res.put("success", true);
        } catch (DuplicateKeyException e) {
            res.put("success", false);
            res.put("message", "录入失败：电话或微信号在系统中已存在");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "录入异常：" + e.getMessage());
        }
        return res;
    }

    @PutMapping("/surveys/{id}/process")
    public String process(@PathVariable Long id) {
        surveyMapper.updateStatus(id);
        return "处理完成";
    }

    @DeleteMapping("/surveys/{id}")
    public String delete(@PathVariable Long id) {
        surveyMapper.deleteById(id);
        return "删除成功";
    }

    @PutMapping("/surveys/{id}/date")
    public String updateDate(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            surveyMapper.updateNextDate(id, LocalDate.parse(payload.get("date")).atStartOfDay());
            return "更新成功";
        } catch (Exception e) {
            return "日期错误";
        }
    }

    @PutMapping("/surveys/{id}/share")
    public String shareSurvey(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        surveyMapper.updateVisibility(id, payload.get("visibility"), payload.get("sharedUsers"));
        return "共享权限设置成功";
    }

    @PutMapping("/surveys/{id}/remarks")
    public String updateRemarks(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        surveyMapper.updateRemarks(id, payload.get("remarks"));
        return "备注保存成功";
    }

    private void ensureAdminOperator(String operatorUsername) {
        if (operatorUsername == null || operatorUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限：缺少管理员身份");
        }
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                operatorUsername
        );
        if (roles.isEmpty() || !"admin".equals(roles.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限：仅管理员可执行");
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
