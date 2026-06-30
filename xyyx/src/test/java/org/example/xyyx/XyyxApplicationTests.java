package org.example.xyyx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/jwks"
})
class XyyxApplicationTests {

    @Test
    void contextLoads() {
    }

}
