package tools.vitruv.methodologist.vsum.model.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;

/** Spring Data repository for {@link VsumBuild} entities. */
@Repository
public interface VsumBuildRepository extends CrudRepository<VsumBuild, Long> {

  /**
   * Finds a build only if it belongs to the given VSUM.
   *
   * @param id the build id
   * @param vsum the VSUM the build must belong to
   * @return the build, or empty if it does not exist or belongs to another VSUM
   */
  Optional<VsumBuild> findByIdAndVsum(Long id, Vsum vsum);

  /**
   * Reads the current status of a build from the database.
   *
   * <p>Unlike {@link #findById}, this never answers from the caller's persistence context. With
   * open-in-view, a request keeps the build it created or looked up there, so {@code findById}
   * would keep returning that copy and miss the status the build worker writes in its own
   * transaction.
   *
   * @param id the build id
   * @return the current status, or empty if the build does not exist
   */
  @Query("SELECT b.status FROM VsumBuild b WHERE b.id = :id")
  Optional<VsumBuildStatus> findStatusById(@Param("id") Long id);

  /**
   * Reads the current error message of a build from the database, see {@link #findStatusById}.
   *
   * @param id the build id
   * @return the error message, or empty if the build does not exist or has none
   */
  @Query("SELECT b.errorMessage FROM VsumBuild b WHERE b.id = :id")
  Optional<String> findErrorMessageById(@Param("id") Long id);

  /**
   * Reads the JAR of a build from the database, see {@link #findStatusById}.
   *
   * @param id the build id
   * @return the JAR, or empty if the build does not exist or has no artifact
   */
  @Query("SELECT a.data FROM VsumBuild b JOIN b.artifact a WHERE b.id = :id")
  Optional<byte[]> findArtifactDataById(@Param("id") Long id);

  /**
   * Lists the builds of a VSUM, newest first.
   *
   * @param vsum the VSUM
   * @param pageable the page to return
   * @return the builds of the requested page
   */
  List<VsumBuild> findAllByVsumOrderByCreatedAtDesc(Vsum vsum, Pageable pageable);

  /**
   * Lists all builds of a VSUM, e.g. to delete them together with it.
   *
   * @param vsum the VSUM
   * @return every build of the VSUM
   */
  List<VsumBuild> findAllByVsum(Vsum vsum);

  /** The newest build of the given inputs that is still queued or running. */
  Optional<VsumBuild> findFirstByVsumAndFingerprintAndStatusInOrderByCreatedAtDesc(
      Vsum vsum, String fingerprint, Collection<VsumBuildStatus> statuses);

  /** The newest successful build of the given inputs whose artifact is still stored. */
  Optional<VsumBuild>
      findFirstByVsumAndFingerprintAndStatusAndArtifactIsNotNullOrderByFinishedAtDesc(
          Vsum vsum, String fingerprint, VsumBuildStatus status);

  /** Successful builds that still hold an artifact, newest first; used for retention. */
  List<VsumBuild> findAllByVsumAndStatusAndArtifactIsNotNullOrderByFinishedAtDesc(
      Vsum vsum, VsumBuildStatus status);

  /**
   * Lists the builds in any of the given states across all VSUMs, e.g. those a shutdown left
   * unfinished.
   *
   * @param statuses the states to match
   * @return the matching builds
   */
  List<VsumBuild> findAllByStatusIn(Collection<VsumBuildStatus> statuses);

  /**
   * Counts the builds pointing at an artifact; a build that adopted a twin's artifact shares it.
   *
   * @param artifact the stored JAR
   * @return the number of builds referencing it
   */
  long countByArtifact(FileStorage artifact);
}
