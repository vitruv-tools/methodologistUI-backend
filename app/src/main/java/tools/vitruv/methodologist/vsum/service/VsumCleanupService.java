package tools.vitruv.methodologist.vsum.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

/**
 * Service that permanently deletes VSUMs together with all data that belongs to them.
 *
 * <p>VSUMs moved to the trash are deleted by a scheduled job once they have been there for 30 days.
 * {@link VsumService#deletePermanently(String, Long)} uses the same cleanup when an owner empties a
 * VSUM from the trash right away.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumCleanupService {

  VsumRepository vsumRepository;
  VsumHistoryService vsumHistoryService;
  VsumUserService vsumUserService;
  VsumViewService vsumViewService;
  MetaModelRelationService metaModelRelationService;
  VsumMetaModelService vsumMetaModelService;
  VsumBuildService vsumBuildService;

  /**
   * Scheduled task that deletes all {@link Vsum} entities marked as removed for over 30 days, along
   * with their associated user relationships, metamodels, metamodel relations and builds.
   *
   * <p>Runs daily at midnight.
   */
  @Transactional
  @Scheduled(cron = "0 0 0 * * ?")
  public void delete() {
    Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
    List<Vsum> oldVsums = vsumRepository.findAllByRemovedAtBefore(cutoff);

    oldVsums.forEach(this::deleteProjectData);
  }

  /**
   * Removes everything that belongs to a project. Views and meta-model relations reference the
   * project's meta-model copies, so they are deleted before the copies to satisfy the foreign keys.
   *
   * <p>{@link Vsum} cascades {@code ALL} to its members, meta models, relations and views, and
   * every query run in between flushes the persistence context. The deleted children are therefore
   * removed from the loaded collections right after each step; otherwise the next flush cascades a
   * persist to them, undoes their deletion and breaks the foreign keys.
   *
   * @param vsum the project to delete
   */
  @Transactional
  public void deleteProjectData(Vsum vsum) {
    vsumHistoryService.delete(vsum);

    vsumUserService.delete(vsum);
    clearIfLoaded(vsum.getVsumUsers());

    vsumViewService.deleteByVsum(vsum);
    clearIfLoaded(vsum.getViews());

    metaModelRelationService.deleteByVsum(vsum);
    clearIfLoaded(vsum.getMetaModelRelations());

    vsumMetaModelService.delete(vsum);
    clearIfLoaded(vsum.getVsumMetaModels());

    vsumBuildService.deleteByVsum(vsum);
  }

  private static void clearIfLoaded(Set<?> children) {
    if (children != null) {
      children.clear();
    }
  }
}
