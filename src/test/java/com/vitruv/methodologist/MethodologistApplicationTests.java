package com.vitruv.methodologist;

import com.vitruv.methodologist.config.TestApplicationConfiguration;
import com.vitruv.methodologist.config.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestApplicationConfiguration.class})
class MethodologistApplicationTests {
	@Test
	void contextLoads() {}
}