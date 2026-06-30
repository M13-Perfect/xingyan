package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveySocialAccountTests {

    @Test
    void socialAccountIsWiredThroughSurveyFiles() throws Exception {
        String entity = Files.readString(Path.of("src", "main", "java", "org", "example", "xyyx", "entity", "Survey.java"));
        String controller = Files.readString(Path.of("src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"));
        String mapper = Files.readString(Path.of("src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"));
        String schema = Files.readString(Path.of("..", "xyyx_db.sql"));

        assertTrue(entity.contains("private String socialAccount;"));
        assertTrue(controller.contains("s.setSocialAccount("));
        assertTrue(controller.contains("params.get(\"socialAccount\")"));
        assertTrue(mapper.contains("social_account"));
        assertTrue(mapper.contains("#{socialAccount}"));
        assertTrue(schema.contains("`social_account` varchar(100) DEFAULT NULL"));
    }
}
