package com.vitruv.methodologist.user.controller.dto.request;

import com.vitruv.methodologist.user.RoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPostRequest {
  @Email
  @NotNull
  private String email;

  @NotNull
  @Builder.Default
  private RoleType roleType = RoleType.USER;

  @NotNull
  @NotBlank
  private String username;

  @NotNull
  @NotBlank
  private String firstName;

  @NotNull
  @NotBlank
  private String lastName;
}
