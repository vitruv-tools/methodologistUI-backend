package com.vitruv.methodologist;

import com.vitruv.methodologist.config.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
class MethodologistApplicationTests {
	@Test
	void contextLoads() {}
}