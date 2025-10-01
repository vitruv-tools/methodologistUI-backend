package tools.vitruv.methodologist.vsum.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;

/**
 * Application service that creates and persists VSUM history snapshots.
 *
 * <p>Builds a {@link VsumHistory} from a domain {@link Vsum} via {@link VsumHistoryMapper} and
 * stores it using {@link VsumHistoryRepository}.
 */
@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumHistoryService {
  VsumHistoryRepository vsumHistoryRepository;
  VsumHistoryMapper vsumHistoryMapper;

  @Value("${vsum.history.limit}")
  long historyLimit;

  /**
   * Creates and persists a history snapshot for the given VSUM.
   *
   * <p>Constructs a JSON\-serializable representation using the mapper and saves the history record
   * within a transactional boundary.
   *
   * @param vsum the aggregate whose state is snapshotted; must not be {@code null}
   * @param creator the user who initiated the snapshot; must not be {@code null}
   * @return the persisted {@link VsumHistory} entity
   */
  @Transactional
  public VsumHistory create(Vsum vsum, User creator) {
    long countOfHistory = vsumHistoryRepository.countByVsum(vsum);

    if (countOfHistory > historyLimit) {
      vsumHistoryRepository
          .findTopByVsumOrderByCreatedAtDesc(vsum)
          .ifPresent(vsumHistoryRepository::delete);
    }

    VsumHistory vsumHistory =
        VsumHistory.builder()
            .creator(creator)
            .vsum(vsum)
            .representation(vsumHistoryMapper.toVsumRepresentation(vsum))
            .build();
    vsumHistoryRepository.save(vsumHistory);

    return vsumHistory;
  }
}
