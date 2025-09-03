package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

/**
 * Service class for managing VSUM (Virtual Single Underlying Model) operations. Handles the
 * business logic for VSUM creation, updates, retrieval and removal while ensuring proper validation
 * and persistence.
 *
 * @see tools.vitruv.methodologist.vsum.model.Vsum
 * @see tools.vitruv.methodologist.vsum.model.repository.VsumRepository
 * @see tools.vitruv.methodologist.vsum.mapper.VsumMapper
 */
@Service
@Slf4j
public class VsumService {
  private final VsumMapper vsumMapper;
  private final VsumRepository vsumRepository;
  private final MetaModelMapper metaModelMapper;

  /**
   * Constructs a new VsumService with the specified dependencies.
   *
   * @param vsumMapper mapper for VSUM conversions
   * @param vsumRepository repository for VSUM operations
   */
  public VsumService(
      VsumMapper vsumMapper, VsumRepository vsumRepository, MetaModelMapper metaModelMapper) {
    this.vsumMapper = vsumMapper;
    this.vsumRepository = vsumRepository;
    this.metaModelMapper = metaModelMapper;
  }

  /**
   * Creates a new VSUM with the specified details.
   *
   * @param vsumPostRequest DTO containing the VSUM creation details
   * @return the created Vsum entity
   */
  @Transactional
  public Vsum create(VsumPostRequest vsumPostRequest) {
    var vsum = vsumMapper.toVsum(vsumPostRequest);
    vsumRepository.save(vsum);

    return vsum;
  }

  /**
   * Updates an existing VSUM with the specified details.
   *
   * @param id the ID of the VSUM to update
   * @param vsumPutRequest DTO containing the update details
   * @return the updated Vsum entity
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM ID is not found or
   *     is marked as removed
   */
  @Transactional
  public Vsum update(Long id, VsumPutRequest vsumPutRequest) {
    var vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsumMapper.updateByVsumPutRequest(vsumPutRequest, vsum);
    vsumRepository.save(vsum);

    return vsum;
  }

  /**
   * Retrieves a VSUM by its ID.
   *
   * @param id the ID of the VSUM to retrieve
   * @return VsumResponse DTO containing the VSUM details
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM ID is not found or
   *     is marked as removed
   */
  @Transactional
  public VsumResponse findById(Long id) {
    var vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    return vsumMapper.toVsumResponse(vsum);
  }

  /**
   * Marks a VSUM as removed by setting its removal timestamp.
   *
   * @param id the ID of the VSUM to remove
   * @return the removed Vsum entity
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM ID is not found or
   *     is marked as removed
   */
  @Transactional
  public Vsum remove(Long id) {
    var vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsum.setRemovedAt(Instant.now());
    vsumRepository.save(vsum);

    return vsum;
  }

  /**
   * Fetches a VSUM owned by the caller and returns its details together with the mapped
   * meta-models. Throws {@code NotFoundException} if the VSUM does not exist or does not belong to
   * the caller.
   *
   * @param callerEmail the authenticated user's email (owner of the VSUM)
   * @param id the VSUM id to fetch
   * @return a response DTO with VSUM data and its meta-models
   * @throws NotFoundException if no matching VSUM is found
   */
  @Transactional(readOnly = true)
  public VsumMetaModelResponse findVsumWithDetails(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    VsumMetaModelResponse response = vsumMapper.toVsumMetaModelResponse(vsum);

    List<MetaModelResponse> metaModels =
        (vsum.getVsumMetaModels() == null
                ? List.<tools.vitruv.methodologist.vsum.model.VsumMetaModel>of()
                : vsum.getVsumMetaModels())
            .stream()
                .map(link -> metaModelMapper.toMetaModelResponse(link.getMetaModel()))
                .toList();

    response.setMetaModels(metaModels);
    return response;
  }
}
