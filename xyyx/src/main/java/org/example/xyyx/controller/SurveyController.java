package org.example.xyyx.controller;

import org.example.xyyx.entity.Survey;
import org.example.xyyx.mapper.SurveyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        String sql = "SELECT role FROM user WHERE username = ? AND password = ?";
        List<String> roles = jdbcTemplate.queryForList(sql, String.class, loginData.get("username"), loginData.get("password"));
        Map<String, Object> result = new HashMap<>();
        if (!roles.isEmpty()) { result.put("success", true); result.put("role", roles.get(0)); result.put("username", loginData.get("username")); }
        else { result.put("success", false); result.put("message", "账号或密码错误"); }
        return result;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers() { return jdbcTemplate.queryForList("SELECT id, username, role FROM user WHERE role != 'admin'"); }

    @PostMapping("/users")
    public String addUser(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) return "账号已存在";
        jdbcTemplate.update("INSERT INTO user (username, password, role) VALUES (?, ?, ?)", username, payload.get("password"), payload.get("role"));
        return "员工添加成功";
    }

    @GetMapping("/surveys/pending-count")
    public int getPendingCount(@RequestParam String username, @RequestParam String role) {
        if ("admin".equals(role)) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey WHERE status = '未处理'", Integer.class); }
        else { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey WHERE status = '未处理' AND (owner = ? OR visibility = 'PUBLIC' OR FIND_IN_SET(?, shared_users) > 0)", Integer.class, username, username); }
    }

    // 🌟 修改：接收前端传来的 keyword 和 city 搜索参数
    @GetMapping("/surveys")
    public Map<String, Object> getSurveys(
            @RequestParam String username, @RequestParam String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {

        int offset = (page - 1) * size;
        List<Survey> list; int total;
        String queryStatus = ("全部".equals(status) || status == null || status.isEmpty()) ? null : status;

        if ("admin".equals(role)) {
            list = surveyMapper.selectAdminPaged(queryStatus, keyword, city, size, offset);
            total = surveyMapper.countAdmin(queryStatus, keyword, city);
        } else {
            list = surveyMapper.selectStaffPaged(username, queryStatus, keyword, city, size, offset);
            total = surveyMapper.countStaff(username, queryStatus, keyword, city);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", list); response.put("total", total); response.put("pages", (int) Math.ceil((double) total / size));
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
            s.setCity((String) params.get("city")); s.setProject((String) params.get("project")); s.setBudget((String) params.get("budget"));
            s.setRemarks((String) params.get("remarks")); s.setOwner((String) params.get("owner"));
            int days = Integer.parseInt(params.getOrDefault("remindDays", 3).toString());
            s.setNextSurveyDate(LocalDateTime.now().plusDays(days));
            surveyMapper.insert(s); res.put("success", true);
        } catch (DuplicateKeyException e) { res.put("success", false); res.put("message", "录入失败：电话或微信号在系统中已存在！"); }
        catch (Exception e) { res.put("success", false); res.put("message", "录入异常：" + e.getMessage()); }
        return res;
    }

    @PutMapping("/surveys/{id}/process")
    public String process(@PathVariable Long id) { surveyMapper.updateStatus(id); return "处理完成"; }

    @DeleteMapping("/surveys/{id}")
    public String delete(@PathVariable Long id) { surveyMapper.deleteById(id); return "删除成功"; }

    @PutMapping("/surveys/{id}/date")
    public String updateDate(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try { surveyMapper.updateNextDate(id, LocalDate.parse(payload.get("date")).atStartOfDay()); return "更新成功"; } catch (Exception e) { return "日期错误"; }
    }

    @PutMapping("/surveys/{id}/share")
    public String shareSurvey(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        surveyMapper.updateVisibility(id, payload.get("visibility"), payload.get("sharedUsers")); return "共享权限设置成功";
    }

    // 🌟 新增：独立修改备注的接口
    @PutMapping("/surveys/{id}/remarks")
    public String updateRemarks(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        surveyMapper.updateRemarks(id, payload.get("remarks")); return "备注保存成功";
    }
}