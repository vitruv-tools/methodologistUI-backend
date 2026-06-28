package tools.vitruv.methodologist.user.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for changing a user's password.
 *
 * <p>Contains a single write-only {@code password} field. Validation enforces a strong password
 * policy: at least 8 characters, with at least one uppercase letter, one lowercase letter, one
 * digit and one special character.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPutChangePasswordRequest {

  @NotNull
  @NotBlank
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @Pattern(
      regexp = "^(?=.{8,256}$)(?=.*\\p{Ll})(?=.*\\p{Lu})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{Nd}\\s]).*$",
      message = "The password needs to be at least 8 characters long.")
  private String password;

  @Override
  public String toString() {
    return "UserPutChangePasswordRequest(password=****)";
  }
}
