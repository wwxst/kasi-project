package com.kasi.backend;

import com.kasi.backend.config.TestRedisConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Tag("integration")
class KasiBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
