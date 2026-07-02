package org.example.xyyx.mapper;

import org.apache.ibatis.annotations.*;
import org.example.xyyx.entity.Survey;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SurveyMapper {
    String PUBLIC_COLUMNS = "SELECT id, tenant_id AS tenantId, customer_uuid AS customerUuid, name, " +
            "phone_mask AS phoneMask, wechat, social_account AS socialAccount, city, project, budget, remarks, " +
            "owner, visibility, status, create_time AS createTime, next_survey_date AS nextSurveyDate";
    String PHONE_SECRET_COLUMNS = "SELECT id, tenant_id AS tenantId, customer_uuid AS customerUuid, " +
            "phone_mask AS phoneMask, phone_ciphertext AS phoneCiphertext, phone_iv AS phoneIv, phone_tag AS phoneTag, " +
            "phone_enc_key_version AS phoneEncKeyVersion";
    String STAFF_SCOPE = "(owner = #{username} OR visibility = 'PUBLIC' OR FIND_IN_SET(#{username}, shared_users) > 0)";
    String KEYWORD_SCOPE = "(name LIKE CONCAT('%',#{keyword},'%') OR wechat LIKE CONCAT('%',#{keyword},'%') " +
            "OR social_account LIKE CONCAT('%',#{keyword},'%') OR project LIKE CONCAT('%',#{keyword},'%'))";
    String PHONE_BACKFILL_MISSING_SCOPE = "(phone_hash IS NULL OR phone_mask IS NULL OR phone_mask = '' OR phone_ciphertext IS NULL " +
            "OR phone_iv IS NULL OR phone_tag IS NULL OR phone_suffix4_hash IS NULL)";
    // 数字关键词可能是手机号也可能是 QQ 等纯数字社交账号：哈希命中与文本 LIKE 用 OR 并联，
    // 而不是互斥分支（外层 WHERE 已限定 tenant_id）。275 行量级下 OR 的索引退化可忽略。
    String SEARCH_SCOPE = "<if test='phoneHash != null or phoneSuffix4Hash != null or (keyword != null and keyword != \"\")'>" +
            " AND (" +
            "<trim prefixOverrides='OR'>" +
            "  <if test='phoneHash != null'> OR phone_hash = #{phoneHash} </if>" +
            "  <if test='phoneSuffix4Hash != null'> OR phone_suffix4_hash = #{phoneSuffix4Hash} </if>" +
            "  <if test='keyword != null and keyword != \"\"'> OR " + KEYWORD_SCOPE + " </if>" +
            "</trim>" +
            ") </if>";

    @Select("<script>" +
            PUBLIC_COLUMNS + " FROM survey " +
            "<where>" +
            "  AND tenant_id = #{tenantId} " +
            "  <if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            SEARCH_SCOPE +
            "  <if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "  <if test='dueBefore != null'> AND next_survey_date IS NOT NULL AND next_survey_date &lt; #{dueBefore} </if>" +
            "</where>" +
            "ORDER BY create_time DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Survey> selectAdminPaged(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("phoneHash") byte[] phoneHash,
            @Param("phoneSuffix4Hash") byte[] phoneSuffix4Hash,
            @Param("city") String city,
            @Param("dueBefore") LocalDateTime dueBefore,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey " +
            "<where>" +
            "  AND tenant_id = #{tenantId} " +
            "  <if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            SEARCH_SCOPE +
            "  <if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "  <if test='dueBefore != null'> AND next_survey_date IS NOT NULL AND next_survey_date &lt; #{dueBefore} </if>" +
            "</where>" +
            "</script>")
    int countAdmin(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("phoneHash") byte[] phoneHash,
            @Param("phoneSuffix4Hash") byte[] phoneSuffix4Hash,
            @Param("city") String city,
            @Param("dueBefore") LocalDateTime dueBefore);

    @Select("<script>" +
            PUBLIC_COLUMNS + " FROM survey " +
            "WHERE tenant_id = #{tenantId} AND " + STAFF_SCOPE + " " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if> " +
            SEARCH_SCOPE +
            "<if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "<if test='dueBefore != null'> AND next_survey_date IS NOT NULL AND next_survey_date &lt; #{dueBefore} </if>" +
            "ORDER BY create_time DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Survey> selectStaffPaged(
            @Param("username") String username,
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("phoneHash") byte[] phoneHash,
            @Param("phoneSuffix4Hash") byte[] phoneSuffix4Hash,
            @Param("city") String city,
            @Param("dueBefore") LocalDateTime dueBefore,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey " +
            "WHERE tenant_id = #{tenantId} AND " + STAFF_SCOPE + " " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            SEARCH_SCOPE +
            "<if test='city != null and city != \"\"'> AND city LIKE CONCAT('%',#{city},'%') </if>" +
            "<if test='dueBefore != null'> AND next_survey_date IS NOT NULL AND next_survey_date &lt; #{dueBefore} </if>" +
            "</script>")
    int countStaff(
            @Param("username") String username,
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("phoneHash") byte[] phoneHash,
            @Param("phoneSuffix4Hash") byte[] phoneSuffix4Hash,
            @Param("city") String city,
            @Param("dueBefore") LocalDateTime dueBefore);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' " +
            "AND next_survey_date >= #{start} AND next_survey_date &lt; #{end}" +
            "</script>")
    int countRevisitTodayAdmin(@Param("tenantId") String tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' " +
            "AND next_survey_date &lt; #{end}" +
            "</script>")
    int countRevisitOverdueAdmin(@Param("tenantId") String tenantId, @Param("end") LocalDateTime end);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' " +
            "AND next_survey_date >= #{start} AND next_survey_date &lt; #{end} " +
            "AND " + STAFF_SCOPE +
            "</script>")
    int countRevisitTodayStaff(@Param("username") String username, @Param("tenantId") String tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' " +
            "AND next_survey_date &lt; #{end} " +
            "AND " + STAFF_SCOPE +
            "</script>")
    int countRevisitOverdueStaff(@Param("username") String username, @Param("tenantId") String tenantId, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' AND owner = #{username}")
    int countPendingByOwner(@Param("tenantId") String tenantId, @Param("username") String username);

    @Select("<script>" +
            "SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' " +
            "AND owner = #{username} AND next_survey_date &lt; #{end}" +
            "</script>")
    int countOverdueByOwner(@Param("tenantId") String tenantId, @Param("username") String username, @Param("end") LocalDateTime end);

    @Select(PUBLIC_COLUMNS + " FROM survey WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash} LIMIT 1")
    List<Survey> selectAdminByPhoneHash(@Param("tenantId") String tenantId, @Param("phoneHash") byte[] phoneHash);

    @Select(PUBLIC_COLUMNS + " FROM survey WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash} AND " + STAFF_SCOPE + " LIMIT 1")
    List<Survey> selectStaffByPhoneHash(@Param("username") String username, @Param("tenantId") String tenantId, @Param("phoneHash") byte[] phoneHash);

    @Select(PUBLIC_COLUMNS + " FROM survey WHERE tenant_id = #{tenantId} AND phone_suffix4_hash = #{phoneSuffix4Hash} ORDER BY create_time DESC LIMIT #{limit}")
    List<Survey> selectAdminByPhoneSuffix4Hash(@Param("tenantId") String tenantId, @Param("phoneSuffix4Hash") byte[] phoneSuffix4Hash, @Param("limit") int limit);

    @Select(PUBLIC_COLUMNS + " FROM survey WHERE tenant_id = #{tenantId} AND phone_suffix4_hash = #{phoneSuffix4Hash} AND " + STAFF_SCOPE + " ORDER BY create_time DESC LIMIT #{limit}")
    List<Survey> selectStaffByPhoneSuffix4Hash(@Param("username") String username, @Param("tenantId") String tenantId, @Param("phoneSuffix4Hash") byte[] phoneSuffix4Hash, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash}")
    int countByPhoneHash(@Param("tenantId") String tenantId, @Param("phoneHash") byte[] phoneHash);

    @Select(PHONE_SECRET_COLUMNS + " FROM survey WHERE id = #{id} AND tenant_id = #{tenantId}")
    Survey selectAdminPhoneSecretsById(@Param("id") Long id, @Param("tenantId") String tenantId);

    @Select(PHONE_SECRET_COLUMNS + " FROM survey WHERE id = #{id} AND tenant_id = #{tenantId} AND " + STAFF_SCOPE)
    Survey selectStaffPhoneSecretsById(@Param("id") Long id, @Param("username") String username, @Param("tenantId") String tenantId);

    @Select("<script>" +
            PHONE_SECRET_COLUMNS + " FROM survey WHERE tenant_id = #{tenantId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Survey> selectAdminPhoneSecretsByIds(@Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Select("<script>" +
            PHONE_SECRET_COLUMNS + " FROM survey WHERE tenant_id = #{tenantId} AND " + STAFF_SCOPE + " AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Survey> selectStaffPhoneSecretsByIds(@Param("username") String username, @Param("tenantId") String tenantId, @Param("ids") List<Long> ids);

    @Insert("INSERT INTO survey(tenant_id, customer_uuid, name, phone, phone_ciphertext, phone_iv, phone_tag, phone_enc_key_version, " +
            "phone_hash, phone_hash_key_version, phone_mask, phone_suffix4_hash, phone_region, phone_normalized_version, " +
            "wechat, social_account, city, project, budget, remarks, owner, visibility, status, next_survey_date) " +
            "VALUES(#{tenantId}, #{customerUuid}, #{name}, NULL, #{phoneCiphertext}, #{phoneIv}, #{phoneTag}, #{phoneEncKeyVersion}, " +
            "#{phoneHash}, #{phoneHashKeyVersion}, #{phoneMask}, #{phoneSuffix4Hash}, #{phoneRegion}, #{phoneNormalizedVersion}, " +
            "#{wechat}, #{socialAccount}, #{city}, #{project}, #{budget}, #{remarks}, #{owner}, 'PRIVATE', '未处理', #{nextSurveyDate})")
    int insert(Survey survey);

    // 处理状态：admin 仅限本租户；staff 还需命中可见范围(owner/public/shared)，未命中返回 0 → 控制器判 403。
    @Update("UPDATE survey SET status = '已处理' WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateAdminStatus(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Update("UPDATE survey SET status = '已处理' WHERE tenant_id = #{tenantId} AND id = #{id} AND " + STAFF_SCOPE)
    int updateStaffStatus(@Param("username") String username, @Param("tenantId") String tenantId, @Param("id") Long id);

    // 物理删除是管理员专属动作，加租户作用域防跨租户误删；返回行数供控制器判定。
    @Delete("DELETE FROM survey WHERE tenant_id = #{tenantId} AND id = #{id}")
    int deleteByIdTenant(@Param("tenantId") String tenantId, @Param("id") Long id);

    @Update("UPDATE survey SET next_survey_date = #{date} WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateAdminNextDate(@Param("tenantId") String tenantId, @Param("id") Long id, @Param("date") LocalDateTime date);

    @Update("UPDATE survey SET next_survey_date = #{date} WHERE tenant_id = #{tenantId} AND id = #{id} AND " + STAFF_SCOPE)
    int updateStaffNextDate(@Param("username") String username, @Param("tenantId") String tenantId, @Param("id") Long id, @Param("date") LocalDateTime date);

    // 重复录入归属提示用：按哈希/微信定位已有记录的 owner（可能为 NULL，调用方需判空）。
    @Select("SELECT owner FROM survey WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash} LIMIT 1")
    String selectOwnerByPhoneHash(@Param("tenantId") String tenantId, @Param("phoneHash") byte[] phoneHash);

    @Select("SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND wechat = #{wechat}")
    int countByWechat(@Param("tenantId") String tenantId, @Param("wechat") String wechat);

    @Select("SELECT owner FROM survey WHERE tenant_id = #{tenantId} AND wechat = #{wechat} LIMIT 1")
    String selectOwnerByWechat(@Param("tenantId") String tenantId, @Param("wechat") String wechat);

    // 可见性/共享名单是管理员专属动作，加租户作用域并返回行数供控制器判定。
    @Update("UPDATE survey SET visibility = #{visibility}, shared_users = #{sharedUsers} WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateVisibilityTenant(@Param("tenantId") String tenantId, @Param("id") Long id, @Param("visibility") String visibility, @Param("sharedUsers") String sharedUsers);

    @Update("UPDATE survey SET remarks = #{remarks}, project = #{project}, budget = #{budget}, social_account = #{socialAccount} WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateAdminDetail(@Param("tenantId") String tenantId, @Param("id") Long id, @Param("remarks") String remarks, @Param("project") String project, @Param("budget") String budget, @Param("socialAccount") String socialAccount);

    @Update("UPDATE survey SET remarks = #{remarks}, project = #{project}, budget = #{budget}, social_account = #{socialAccount} WHERE tenant_id = #{tenantId} AND id = #{id} AND " + STAFF_SCOPE)
    int updateStaffDetail(@Param("username") String username, @Param("tenantId") String tenantId, @Param("id") Long id, @Param("remarks") String remarks, @Param("project") String project, @Param("budget") String budget, @Param("socialAccount") String socialAccount);

    @Select("SELECT id, tenant_id AS tenantId, customer_uuid AS customerUuid, phone " +
            "FROM survey WHERE tenant_id = #{tenantId} AND id > #{afterId} " +
            "AND phone IS NOT NULL AND " + PHONE_BACKFILL_MISSING_SCOPE + " ORDER BY id LIMIT #{limit}")
    List<Survey> selectPhoneBackfillBatch(
            @Param("tenantId") String tenantId,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);

    @Select("SELECT id, tenant_id AS tenantId, customer_uuid AS customerUuid, phone " +
            "FROM survey WHERE tenant_id = #{tenantId} AND id > #{afterId} " +
            "AND phone IS NOT NULL ORDER BY id LIMIT #{limit}")
    List<Survey> selectPhoneRekeyBatch(
            @Param("tenantId") String tenantId,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);

    @Update("UPDATE survey SET customer_uuid = COALESCE(customer_uuid, #{customerUuid}), " +
            "phone_ciphertext = #{phoneCiphertext}, phone_iv = #{phoneIv}, phone_tag = #{phoneTag}, " +
            "phone_enc_key_version = #{phoneEncKeyVersion}, phone_hash = #{phoneHash}, " +
            "phone_hash_key_version = #{phoneHashKeyVersion}, phone_mask = #{phoneMask}, " +
            "phone_suffix4_hash = #{phoneSuffix4Hash}, phone_region = #{phoneRegion}, " +
            "phone_normalized_version = #{phoneNormalizedVersion} " +
            "WHERE id = #{id} AND phone IS NOT NULL AND " + PHONE_BACKFILL_MISSING_SCOPE)
    int updatePhonePrivacyBackfill(Survey survey);

    @Update("UPDATE survey SET customer_uuid = COALESCE(customer_uuid, #{customerUuid}), " +
            "phone_ciphertext = #{phoneCiphertext}, phone_iv = #{phoneIv}, phone_tag = #{phoneTag}, " +
            "phone_enc_key_version = #{phoneEncKeyVersion}, phone_hash = #{phoneHash}, " +
            "phone_hash_key_version = #{phoneHashKeyVersion}, phone_mask = #{phoneMask}, " +
            "phone_suffix4_hash = #{phoneSuffix4Hash}, phone_region = #{phoneRegion}, " +
            "phone_normalized_version = #{phoneNormalizedVersion} " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND phone IS NOT NULL")
    int updatePhonePrivacyRekey(Survey survey);
}
