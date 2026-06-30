package org.example.xyyx.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyMapperCompatibilityTests {

    @Test
    void surveyQueriesUseExplicitColumnsBeforeAdditiveMigrations() throws Exception {
        String mapper = Files.readString(Path.of("src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"));

        assertFalse(mapper.contains("SELECT * FROM survey"));
        assertFalse(mapper.contains("SELECT id, name, phone,"));
        assertFalse(mapper.contains("OR phone LIKE"));
        assertTrue(mapper.contains("phone_mask AS phoneMask"));
        assertTrue(mapper.contains("phone_hash = #{phoneHash}"));
        assertTrue(mapper.contains("phone_suffix4_hash = #{phoneSuffix4Hash}"));
        assertTrue(mapper.contains("phone_ciphertext AS phoneCiphertext"));
        assertTrue(mapper.contains("'未处理'"));
        assertTrue(mapper.contains("status = '已处理'"));
    }
}
