package tools.vitruv.methodologist.vsum.model.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
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
