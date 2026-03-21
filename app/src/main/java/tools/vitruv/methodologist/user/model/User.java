package tools.vitruv.methodologist.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.user.RoleType;

/** Entity representing a user in the system. Maps to the `usr` table in the database. */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "usr")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull @Email private String email;

  @NotNull
  @Enumerated(EnumType.STRING)
  private RoleType roleType;

  @NotNull private String username;

  @NotNull private String firstName;

  @NotNull private String lastName;
  private String otpSecret;
  private Instant otpExpiresAt;

  @NotNull @Builder.Default private Boolean verified = false;

  @CreationTimestamp private Instant createdAt;
  private Instant removedAt;
}
