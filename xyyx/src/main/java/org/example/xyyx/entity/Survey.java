package org.example.xyyx.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Data
public class Survey {
    private Long id;
    private String name;
    private String phone;
    private String city;
    private String project;
    private String wechat;
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