package com.vitruv.methodologist.user.controller.dto;

import com.vitruv.methodologist.user.RoleType;
import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakUser {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String role;
    private RoleType roleType;
}
