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

@Repository
public interface VsumBuildRepository extends CrudRepository<VsumBuild, Long> {

  Optional<VsumBuild> findByIdAndVsum(Long id, Vsum vsum);

  List<VsumBuild> findAllByVsumOrderByCreatedAtDesc(Vsum vsum, Pageable pageable);

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

  List<VsumBuild> findAllByStatusIn(Collection<VsumBuildStatus> statuses);

  long countByArtifact(FileStorage artifact);
}
