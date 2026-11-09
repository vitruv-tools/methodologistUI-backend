package tools.vitruv.methodologist.vsum.model.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumVersion;

/** Spring Data repository for {@link VsumVersion} entities. */
@Repository
public interface VsumVersionRepository extends JpaRepository<VsumVersion, Long> {

  /**
   * Finds the version whose snapshot the given build builds.
   *
   * @param buildId the build id
   * @return the version, or empty if the build is not the build of a version
   */
  Optional<VsumVersion> findByBuildId(Long buildId);

  /**
   * Finds the pending version of a VSUM, i.e. a publish whose build has not finished yet.
   *
   * @param vsum the VSUM
   * @return the pending version, or empty if no publish is running
   */
  Optional<VsumVersion> findFirstByVsumAndNumberIsNull(Vsum vsum);

  /**
   * Finds the newest stable version of a VSUM.
   *
   * @param vsum the VSUM
   * @return the version with the highest number, or empty if nothing has been published yet
   */
  Optional<VsumVersion> findFirstByVsumAndNumberIsNotNullOrderByNumberDesc(Vsum vsum);

  /**
   * Reads the highest version number of a VSUM.
   *
   * @param vsum the VSUM
   * @return the highest number, or empty if nothing has been published yet
   */
  @Query("SELECT MAX(v.number) FROM VsumVersion v WHERE v.vsum = :vsum")
  Optional<Integer> findMaxNumberByVsum(@Param("vsum") Vsum vsum);

  /**
   * Reads the ids of the builds that belong to versions of a VSUM; their artifacts are kept.
   *
   * @param vsum the VSUM
   * @return the build ids of all versions, pending or stable
   */
  @Query("SELECT v.build.id FROM VsumVersion v WHERE v.vsum = :vsum")
  Set<Long> findBuildIdsByVsum(@Param("vsum") Vsum vsum);

  /**
   * Lists all versions of a VSUM, e.g. to delete them together with it.
   *
   * @param vsum the VSUM
   * @return every version of the VSUM
   */
  List<VsumVersion> findAllByVsum(Vsum vsum);

  /**
   * Lists the pending versions built by any of the given builds.
   *
   * @param builds the builds
   * @return the pending versions of these builds
   */
  List<VsumVersion> findAllByBuildInAndNumberIsNull(Collection<VsumBuild> builds);
}
