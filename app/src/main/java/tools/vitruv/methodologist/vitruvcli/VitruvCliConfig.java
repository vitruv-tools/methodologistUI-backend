package tools.vitruv.methodologist.vitruvcli;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that enables binding of external configuration to {@link
 * VitruvCliProperties}.
 *
 * <p>By declaring {@code @EnableConfigurationProperties(VitruvCliProperties.class)} this class
 * ensures that the properties prefixed with {@code vitruv.cli} are bound to the {@link
 * VitruvCliProperties} type and registered as a bean in the application context.
 */
@Configuration
@EnableConfigurationProperties(VitruvCliProperties.class)
public class VitruvCliConfig {}
