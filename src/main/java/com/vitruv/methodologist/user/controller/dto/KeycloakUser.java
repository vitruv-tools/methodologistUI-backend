package com.vitruv.methodologist.user.controller.dto;

import com.vitruv.methodologist.user.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) representing user information for Keycloak operations. Contains user
 * profile details and role information used when creating or updating users in the Keycloak
 * authentication server.
 */
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
