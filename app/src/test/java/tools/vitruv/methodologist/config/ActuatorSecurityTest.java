package tools.vitruv.methodologist.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.vitruv.methodologist.user.service.UserService;

/**
 * Checks the URL rules of {@link SecurityConfiguration} against the real actuator endpoints, with
 * the management settings from {@code application.properties}. The context contains only the
 * security configuration and the actuator, so no database is needed.
 *
 * <p>MockMvc is built by hand instead of via {@code @AutoConfigureMockMvc}: the project pins
 * spring-boot-test-autoconfigure to a newer Boot line than the rest of Spring Boot, and that
 * annotation then imports auto-configurations that do not exist in the Boot version in use.
 */
@SpringBootTest(
    classes = ActuatorSecurityTest.TestApplication.class,
    properties = {
      "management.endpoints.web.exposure.include=health,info",
      "management.endpoint.health.probes.enabled=true",
      "management.endpoint.health.show-details=when_authorized",
      "management.endpoint.health.show-components=when_authorized",
      "management.endpoint.health.group.readiness.include=readinessState,keycloak,setupService"
    })
class ActuatorSecurityTest {

  @Autowired WebApplicationContext context;

  @MockitoBean UserService userService;
  @MockitoBean JwtDecoder jwtDecoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void healthAndProbes_answerWithoutAToken_butOnlyWithTheStatus() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void health_showsTheComponents_toAnAuthenticatedUser() throws Exception {
    mockMvc
        .perform(get("/actuator/health").with(user()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.keycloak.status").value("UP"))
        .andExpect(jsonPath("$.components.setupService.status").value("UP"));
    mockMvc
        .perform(get("/actuator/health/readiness").with(user()))
        .andExpect(jsonPath("$.components.keycloak").exists())
        .andExpect(jsonPath("$.components.setupService").exists())
        .andExpect(jsonPath("$.components.livenessState").doesNotExist());
  }

  @Test
  void info_requiresAToken() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/info").with(user())).andExpect(status().isOk());
  }

  @Test
  void info_isForbidden_forATokenWithoutTheUserRole() throws Exception {
    mockMvc.perform(get("/actuator/info").with(jwt())).andExpect(status().isForbidden());
  }

  @Test
  void otherActuatorEndpoints_areNeitherOpenNorExposed() throws Exception {
    for (String endpoint :
        new String[] {
          "/actuator",
          "/actuator/env",
          "/actuator/beans",
          "/actuator/heapdump",
          "/actuator/configprops",
          "/actuator/loggers",
          "/actuator/mappings"
        }) {
      mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
    }
    // Even with a token they do not exist, because only health and info are exposed.
    for (String endpoint :
        new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump"}) {
      mockMvc.perform(get(endpoint).with(user())).andExpect(status().isNotFound());
    }
  }

  private static RequestPostProcessor user() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_user"));
  }

  /** Security configuration plus actuator; persistence is left out on purpose. */
  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  @Import(SecurityConfiguration.class)
  static class TestApplication {

    // Stand-ins named like the real indicators, so the readiness group resolves.
    @Bean
    HealthIndicator keycloakHealthIndicator() {
      return () -> Health.up().build();
    }

    @Bean
    HealthIndicator setupServiceHealthIndicator() {
      return () -> Health.up().build();
    }
  }
}
