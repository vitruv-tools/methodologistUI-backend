package tools.vitruv.methodologist.user.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

  @NotNull
  @NotBlank
  @Size(min = 4, message = "Username must be at least 4 characters long.")
  private String username;

  @NotNull @NotBlank private String firstName;

  @NotNull @NotBlank private String lastName;

  @NotNull
  @NotBlank
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Pattern(
      regexp = "^(?=.{8,256}$)(?=.*\\p{Ll})(?=.*\\p{Lu})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{Nd}\\s]).*$",
      message = "The password needs to be at least 8 characters long.")
  private String password;

  @Override
  public String toString() {
    return "UserPostRequest(email="
        + email
        + ", roleType="
        + roleType
        + ", username="
        + username
        + ", firstName="
        + firstName
        + ", lastName="
        + lastName
        + ", password=****)";
  }
}
