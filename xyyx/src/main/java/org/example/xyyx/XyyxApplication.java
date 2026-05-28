package org.example.xyyx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("org.example.xyyx.mapper")
public class XyyxApplication {
    public static void main(String[] args) {
        SpringApplication.run(XyyxApplication.class, args);
    }
}