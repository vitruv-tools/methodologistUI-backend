package tools.vitruv.methodologist.vsum.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRepresentation;

/**
 * A stable version of a {@link Vsum}: a snapshot of the project's build inputs that never changes.
 *
 * <p>The input files are copies with their own {@link FileStorage} rows, so editing the working
 * copy's files afterwards does not reach the version. The snapshot is built by {@link #build}; as
 * long as that build has not succeeded, {@link #number} is {@code null} and the version is pending.
 * A pending version is deleted again if its build fails.
 */
@Builder
@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class VsumVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  /** 1, 2, ... per VSUM once the build succeeded; {@code null} while the version is pending. */
  @Column(name = "version_number")
  private Integer number;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private User createdBy;

  /** The build of this snapshot; its artifact is the version's JAR. */
  @NotNull
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "build_id")
  private VsumBuild build;

  /** Metamodels, names, relations and views of the working copy at publish time. */
  @NotNull
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private VsumRepresentation manifest;

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "vsum_version_ecore",
      joinColumns = @JoinColumn(name = "version_id"),
      inverseJoinColumns = @JoinColumn(name = "file_storage_id"))
  @OrderColumn(name = "file_order")
  private List<FileStorage> ecores = new ArrayList<>();

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "vsum_version_reaction",
      joinColumns = @JoinColumn(name = "version_id"),
      inverseJoinColumns = @JoinColumn(name = "file_storage_id"))
  @OrderColumn(name = "file_order")
  private List<FileStorage> reactions = new ArrayList<>();

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "vsum_version_constraint",
      joinColumns = @JoinColumn(name = "version_id"),
      inverseJoinColumns = @JoinColumn(name = "file_storage_id"))
  @OrderColumn(name = "file_order")
  private List<FileStorage> constraints = new ArrayList<>();

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "vsum_version_view",
      joinColumns = @JoinColumn(name = "version_id"),
      inverseJoinColumns = @JoinColumn(name = "file_storage_id"))
  @OrderColumn(name = "file_order")
  private List<FileStorage> views = new ArrayList<>();

  @CreationTimestamp private Instant createdAt;

  /**
   * Returns every copied input file of this version.
   *
   * @return the ecore, reaction, constraint and view copies
   */
  public List<FileStorage> allFiles() {
    return Stream.of(ecores, reactions, constraints, views).flatMap(List::stream).toList();
  }
}
