package tools.vitruv.methodologist.user.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.user.RoleType;

/**
 * Data transfer object for creating a new user. Contains required user information for
 * registration.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPostRequest {
  @Email @NotNull private String email;

  @NotNull @Builder.Default private RoleType roleType = RoleType.USER;

  @NotNull @NotBlank private String username;

  @NotNull @NotBlank private String firstName;

  @NotNull @NotBlank private String lastName;

  // todo: consider using a more secure password policy And need to hide it and dont show it in the
  // logs
  @NotNull @NotBlank private String password;
}
