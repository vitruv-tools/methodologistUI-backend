package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.vitruv.methodologist.exception.NotFoundException;

// TODO: We have 7 points that we need to test and 2 that are optional:
// assignUserRole line 138, createUser, removeUser, verifyUserPasswordOrThrow
// resetPassword, sendResetPasswordEmail, setPassword
// exitUser, preparePasswordRepresentation
@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

  private static final String REALM = "test-realm";

  @Mock private Keycloak keycloakAdmin;
  @Mock private RealmResource realmResource;
  @Mock private UsersResource usersResource;
  @Mock private UserResource userResource;
  @Mock private RolesResource rolesResource;
  @Mock private RoleResource roleResource;
  @Mock private RoleMappingResource roleMappingResource;
  @Mock private RoleScopeResource roleScopeResource;

  private KeycloakService keycloakService;

  @BeforeEach
  void setUp() {
    keycloakService =
        new KeycloakService(
            "http://localhost/dummy",
            REALM,
            "admin",
            "password",
            "secret",
            "client-id");

    ReflectionTestUtils.setField(keycloakService, "keycloakAdmin", keycloakAdmin);
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
  }

  @Test
  void assignUserRole_assignsRole_whenUserExists() {
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId("user-1");
    RoleRepresentation roleRepresentation = new RoleRepresentation();

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(userRepresentation));
    when(realmResource.roles()).thenReturn(rolesResource);
    when(rolesResource.get("USER")).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(roleRepresentation);
    when(usersResource.get("user-1")).thenReturn(userResource);
    when(userResource.roles()).thenReturn(roleMappingResource);
    when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

    keycloakService.assignUserRole("alice", "USER");

    verify(roleScopeResource).add(List.of(roleRepresentation));
  }

  @Test
  void assignUserRole_throwsNotFound_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    assertThatThrownBy(() -> keycloakService.assignUserRole("missing", "USER"))
        .isInstanceOf(NotFoundException.class);

    verify(realmResource, never()).roles();
  }
}

