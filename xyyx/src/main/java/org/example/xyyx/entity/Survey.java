package org.example.xyyx.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Data
public class Survey {
    private Long id;
    private String tenantId;
    private String customerUuid;
    private String name;
    @JsonIgnore
    private String phone;
    @JsonIgnore
    private byte[] phoneCiphertext;
    @JsonIgnore
    private byte[] phoneIv;
    @JsonIgnore
    private byte[] phoneTag;
    @JsonIgnore
    private String phoneEncKeyVersion;
    @JsonIgnore
    private byte[] phoneHash;
    @JsonIgnore
    private String phoneHashKeyVersion;
    private String phoneMask;
    @JsonIgnore
    private byte[] phoneSuffix4Hash;
    @JsonIgnore
    private String phoneRegion;
    @JsonIgnore
    private String phoneNormalizedVersion;
    private String city;
    private String project;
    private String wechat;
    private String socialAccount;
    private String budget;
    private String requirement;
    private String remarks;
    private String status;
    private String owner;
    private String visibility;
    private String sharedUsers;

    // @JsonFormat 能把数据库的时间自动变成好看的字符串传给 Vue
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "GMT+8")
    private LocalDateTime nextSurveyDate;
}
