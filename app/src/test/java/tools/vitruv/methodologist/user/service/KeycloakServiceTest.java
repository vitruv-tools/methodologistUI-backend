package tools.vitruv.methodologist.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;

class KeycloakServiceTest {

    private KeycloakService keycloakService;

    @BeforeEach
    void setUp() {
        keycloakService = new KeycloakService(
                "http://localhost:8080",
                "test-realm",
                "admin",
                "password",
                "secret",
                "client-id");
    }

    @Test
    void preparePasswordRepresentation_returnsCorrectCredentialRepresentation() {

        CredentialRepresentation credential = keycloakService.preparePasswordRepresentation("123456", false);

        assertEquals("123456", credential.getValue());
        assertEquals(CredentialRepresentation.PASSWORD, credential.getType());
        assertFalse(credential.isTemporary());
    }

    @Test
    void preparePasswordRepresentation_setsTemporaryTrue() {

        CredentialRepresentation credential = keycloakService.preparePasswordRepresentation("abc123", true);

        assertEquals("abc123", credential.getValue());
        assertEquals(CredentialRepresentation.PASSWORD, credential.getType());
        assertEquals(true, credential.isTemporary());
    }

}
