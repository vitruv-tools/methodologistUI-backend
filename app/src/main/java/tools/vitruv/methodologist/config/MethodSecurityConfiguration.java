package tools.vitruv.methodologist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@Profile("!noauth")
@EnableMethodSecurity(jsr250Enabled = true)
public class MethodSecurityConfiguration {}
