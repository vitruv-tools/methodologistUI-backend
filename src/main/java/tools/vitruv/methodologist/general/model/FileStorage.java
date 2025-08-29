package tools.vitruv.methodologist.general.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.user.model.User;

/**
 * Entity class representing a file stored in the database. Contains metadata and binary content of
 * uploaded files.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class FileStorage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String filename;

  @NotNull
  @Enumerated(EnumType.STRING)
  private FileEnumType type;

  @NotNull private String contentType;

  @NotNull private long sizeBytes;

  @NotNull private String sha256;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @NotNull
  private byte[] data;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @CreationTimestamp private Instant createdAt;
}
