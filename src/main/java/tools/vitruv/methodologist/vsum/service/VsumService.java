package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

/**
 * Service class for managing VSUM (Virtual Single Underlying Model) operations. Handles the
 * business logic for VSUM creation, updates, retrieval and removal while ensuring proper validation
 * and persistence.
 *
 * @see Vsum
 * @see VsumRepository
 * @see VsumMapper
 */
@Service
@Slf4j
public class VsumService {
  private final VsumMapper vsumMapper;
  private final VsumRepository vsumRepository;

  /**
   * Constructs a new VsumService with the specified dependencies.
   *
   * @param vsumMapper mapper for VSUM conversions
   * @param vsumRepository repository for VSUM operations
   */
  public VsumService(VsumMapper vsumMapper, VsumRepository vsumRepository) {
    this.vsumMapper = vsumMapper;
    this.vsumRepository = vsumRepository;
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
   * @throws NotFoundException if the VSUM ID is not found or is marked as removed
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
   * @throws NotFoundException if the VSUM ID is not found or is marked as removed
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
   * @throws NotFoundException if the VSUM ID is not found or is marked as removed
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
}
