package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

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
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumService {
  VsumMapper vsumMapper;
  VsumRepository vsumRepository;
  MetaModelMapper metaModelMapper;
  VsumMetaModelService vsumMetaModelService;
  UserRepository userRepository;
  VsumUserRepository vsumUserRepository;
  private final VsumUserService vsumUserService;

  /**
   * Creates a new VSUM with the specified details.
   *
   * @param vsumPostRequest DTO containing the VSUM creation details
   * @return the created Vsum entity
   */
  @Transactional
  public Vsum create(String callerEmail, VsumPostRequest vsumPostRequest) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(UnauthorizedException::new);
    Vsum vsum = vsumMapper.toVsum(vsumPostRequest);
    vsum.setUser(user);
    vsum = vsumRepository.save(vsum);
    vsumUserService.create(vsum, user, VsumRole.OWNER);
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
  public Vsum update(String callerEmail, Long id, VsumPutRequest vsumPutRequest) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsumMapper.updateByVsumPutRequest(vsumPutRequest, vsum);
    vsumMetaModelService.sync(vsum, vsumPutRequest.getMetaModelIds());
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
  public VsumResponse findById(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
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
  public Vsum remove(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
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
                .map(metaModel -> metaModelMapper.toMetaModelResponse(metaModel.getMetaModel()))
                .toList();

    response.setMetaModels(metaModels);
    return response;
  }

  /**
   * Retrieves all VSUMs associated with a given user's email. Returns a list of VSUMs where the
   * user has any role or permission.
   *
   * @param callerEmail the email address of the user whose VSUMs should be retrieved
   * @return a list of VsumResponse DTOs containing the VSUM details
   */
  @Transactional
  public List<VsumResponse> findAllByUser(String callerEmail) {
    List<VsumUser> vsumsUser = vsumUserRepository.findAllByUser_Email(callerEmail);

    return vsumsUser.stream().map(VsumUser::getVsum).map(vsumMapper::toVsumResponse).toList();
  }
}
