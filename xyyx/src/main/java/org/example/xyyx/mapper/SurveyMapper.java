package org.example.xyyx.mapper;

import org.apache.ibatis.annotations.*;
import org.example.xyyx.entity.Survey;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SurveyMapper {

    // ================= 1. 管理员查询 =================
    @Select("<script>" +
            "SELECT * FROM survey " +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            // 🌟 核心修改：在 keyword 搜索逻辑中，加入了 OR wechat LIKE ...
            "  <if test='keyword != null and keyword != \"\"'> AND (name LIKE CONCAT('%',#{keyword},'%') OR phone LIKE CONCAT('%',#{keyword},'%') OR wechat LIKE CONCAT('%',#{keyword},'%') OR project LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "  <if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "</where>" +
            "ORDER BY create_time DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Survey> selectAdminPaged(@Param("status") String status, @Param("keyword") String keyword, @Param("city") String city, @Param("size") int size, @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey " +
            "<where>" +
            "  <if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            // 🌟 同步修改
            "  <if test='keyword != null and keyword != \"\"'> AND (name LIKE CONCAT('%',#{keyword},'%') OR phone LIKE CONCAT('%',#{keyword},'%') OR wechat LIKE CONCAT('%',#{keyword},'%') OR project LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "  <if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "</where>" +
            "</script>")
    int countAdmin(@Param("status") String status, @Param("keyword") String keyword, @Param("city") String city);

    // ================= 2. 员工查询 =================
    @Select("<script>" +
            "SELECT * FROM survey " +
            "WHERE (owner = #{username} OR visibility = 'PUBLIC' OR FIND_IN_SET(#{username}, shared_users) > 0) " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if> " +
            // 🌟 同步修改
            "<if test='keyword != null and keyword != \"\"'> AND (name LIKE CONCAT('%',#{keyword},'%') OR phone LIKE CONCAT('%',#{keyword},'%') OR wechat LIKE CONCAT('%',#{keyword},'%') OR project LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "ORDER BY create_time DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Survey> selectStaffPaged(@Param("username") String username, @Param("status") String status, @Param("keyword") String keyword, @Param("city") String city, @Param("size") int size, @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey " +
            "WHERE (owner = #{username} OR visibility = 'PUBLIC' OR FIND_IN_SET(#{username}, shared_users) > 0) " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            // 🌟 同步修改
            "<if test='keyword != null and keyword != \"\"'> AND (name LIKE CONCAT('%',#{keyword},'%') OR phone LIKE CONCAT('%',#{keyword},'%') OR wechat LIKE CONCAT('%',#{keyword},'%') OR project LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "<if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "</script>")
    int countStaff(@Param("username") String username, @Param("status") String status, @Param("keyword") String keyword, @Param("city") String city);

    // ================= 3. 数据操作 =================
    @Insert("INSERT INTO survey(name, phone, wechat, city, project, budget, remarks, owner, visibility, status, next_survey_date) " +
            "VALUES(#{name}, #{phone}, #{wechat}, #{city}, #{project}, #{budget}, #{remarks}, #{owner}, 'PRIVATE', '未处理', #{nextSurveyDate})")
    int insert(Survey survey);

    @Update("UPDATE survey SET status = '已处理' WHERE id = #{id}")
    int updateStatus(Long id);

    @Delete("DELETE FROM survey WHERE id = #{id}")
    int deleteById(Long id);

    @Update("UPDATE survey SET next_survey_date = #{date} WHERE id = #{id}")
    void updateNextDate(@Param("id") Long id, @Param("date") LocalDateTime date);

    @Update("UPDATE survey SET visibility = #{visibility}, shared_users = #{sharedUsers} WHERE id = #{id}")
    void updateVisibility(@Param("id") Long id, @Param("visibility") String visibility, @Param("sharedUsers") String sharedUsers);

    @Update("UPDATE survey SET remarks = #{remarks} WHERE id = #{id}")
    void updateRemarks(@Param("id") Long id, @Param("remarks") String remarks);
}