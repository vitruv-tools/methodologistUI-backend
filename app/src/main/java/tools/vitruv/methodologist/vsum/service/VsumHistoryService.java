package tools.vitruv.methodologist.vsum.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
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
public class VsumHistoryService {
  private final VsumHistoryRepository vsumHistoryRepository;
  private final VsumHistoryMapper vsumHistoryMapper;
  private final Long historyLimit;

  /**
   * Constructs a {@link VsumHistoryService} with required dependencies.
   *
   * @param vsumHistoryRepository repository for persisting history records
   * @param vsumHistoryMapper mapper for converting VSUM entities to representations
   * @param historyLimit maximum number of history snapshots to retain per VSUM
   */
  public VsumHistoryService(
      VsumHistoryRepository vsumHistoryRepository,
      VsumHistoryMapper vsumHistoryMapper,
      @Value("${vsum.history.limit}") Long historyLimit) {
    this.vsumHistoryRepository = vsumHistoryRepository;
    this.vsumHistoryMapper = vsumHistoryMapper;
    this.historyLimit = historyLimit;
  }

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
    long existsHistoryCount = vsumHistoryRepository.countByVsum(vsum);

    if (existsHistoryCount > historyLimit) {
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

  /**
   * Deletes all {@link VsumHistory} records associated with the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose history records should be deleted
   */
  public void delete(Vsum vsum) {
    vsumHistoryRepository.deleteVsumHistoryByVsum(vsum);
  }

  /**
   * Retrieves VSUM history snapshots for the specified VSUM id and caller email, filters out
   * records for removed users or removed VSUMs, orders results by {@code createdAt} ascending, and
   * maps each entity to a {@link VsumHistoryResponse} DTO.
   *
   * @param callerEmail the email address of the VSUM owner used to filter history records; must not
   *     be {@code null}
   * @param vsumId the VSUM id to filter history records by; must not be {@code null}
   * @return a list of {@link VsumHistoryResponse} ordered by {@code createdAt} ascending; never
   *     {@code null} (may be empty)
   */
  public List<VsumHistoryResponse> findAllByVsumId(String callerEmail, Long vsumId) {
    List<VsumHistory> vsumHistories =
        vsumHistoryRepository
            .findAllByVsum_IdAndVsum_User_EmailAndVsum_User_RemovedAtIsNullAndVsum_RemovedAtIsNullOrderByCreatedAtDesc(
                vsumId, callerEmail);
    return vsumHistories.stream().map(vsumHistoryMapper::toVsumHistoryResponse).toList();
  }
}
