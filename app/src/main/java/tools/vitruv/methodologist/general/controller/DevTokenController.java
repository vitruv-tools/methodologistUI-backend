package tools.vitruv.methodologist.general.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.service.UserService;

@RestController
@Profile("dev")
@Validated
@RequestMapping("/api/")
public class DevTokenController {
    private final UserService userService;

    public DevTokenController(UserService userService) { this.userService = userService; }

    // Setup local environment variable for this
    @Value("${keycloak.methodologist.username:none}")
    private String username;

    // Setup local environment variable for this
    @Value("${keycloak.methodologist.password:none}")
    private String password;

    @GetMapping("/token")
    public String getToken() {
        return userService.getAccessToken(new PostAccessTokenRequest(username, password)).getAccessToken();
    }
}
