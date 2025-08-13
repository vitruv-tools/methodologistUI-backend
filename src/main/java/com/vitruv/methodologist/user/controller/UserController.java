package com.vitruv.methodologist.user.controller;

import com.vitruv.methodologist.user.service.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/")
@Validated
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

//    @PostMapping("/v1/users/login")
//    public ResponseTemplateDto<Void> createUser(@Valid @RequestBody PostUserLoginRequest postUserLoginRequest) {
//        userService.login(postUserLoginRequest);
//        return ResponseTemplateDto.<Void>builder()
//                .message(LOGIN_USER_SUCCESSFULLY)
//                .build();
//    }
//
//    @PostMapping("/v1/users/sign-up")
//    public ResponseTemplateDto<Void> createUser(@Valid @RequestBody PostUserSignUpRequest postUserSignUpRequest) {
//        userService.create(postUserSignUpRequest);
//        return ResponseTemplateDto.<Void>builder()
//                .message(SIGNUP_USER_SUCCESSFULLY)
//                .build();
//    }

//    @PostMapping("/v1/users/access-token")
//    public UserWebToken getAccessToken(@Valid @RequestBody PostAccessTokenRequest postAccessTokenRequest) {
//        return userService.getAccessToken(postAccessTokenRequest);
//    }
//
//    @PostMapping("/v1/users/access-token/by-refresh-token")
//    public UserWebToken getAccessTokenByRefreshToken(@Valid @RequestBody PostAccessTokenByRefreshTokenRequest postAccessTokenByRefreshTokenRequest) {
//        return userService.getAccessTokenByRefreshToken(postAccessTokenByRefreshTokenRequest);
//    }

//    @PutMapping("/v1/users/profile")
//    @PreAuthorize("hasRole('user')")
//    public ResponseTemplateDto<Void> updateProfile(KeycloakAuthentication authentication,
//                                                   @Valid @RequestBody PutUserProfileRequest putUserProfileRequest) {
//        String userName = authentication.getParsedToken().getPreferredUsername();
//        userService.update(userName, putUserProfileRequest);
//        return ResponseTemplateDto.<Void>builder()
//                .message(translate(USER_UPDATED_SUCCESSFULLY))
//                .build();
//    }
//
//    @GetMapping("/v1/users")
//    @PreAuthorize("hasRole('user')")
//    public ResponseTemplateDto<UserResponse> findAllByUsername(KeycloakAuthentication authentication) {
//        String userName = authentication.getParsedToken().getPreferredUsername();
//        return ResponseTemplateDto.<UserResponse>builder()
//                .data(userService.findByUsername(userName))
//                .build();
//    }
}