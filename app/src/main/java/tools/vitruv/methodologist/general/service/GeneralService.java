package tools.vitruv.methodologist.general.service;

import static tools.vitruv.methodologist.messages.Error.CLIENT_NOT_FOUND_ERROR;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.mapper.VersioningMapper;
import tools.vitruv.methodologist.general.model.Versioning;
import tools.vitruv.methodologist.general.model.repository.VersioningRepository;

/**
 * Service class for managing general application operations. Handles version information retrieval
 * and other general functionality.
 */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeneralService {
  VersioningRepository versioningRepository;
  VersioningMapper versioningMapper;

  /**
   * Retrieves the latest version information for a specified client application.
   *
   * @param clientName the name of the client application
   * @return ResponseTemplateDto containing the latest version information
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the specified client is not
   *     found
   */
  @Transactional
  public ResponseTemplateDto<LatestVersionResponse> getLatestVersion(String clientName) {
    Versioning client =
        versioningRepository
            .findTopByAppNameOrderByIdDesc(clientName)
            .orElseThrow(() -> new NotFoundException(CLIENT_NOT_FOUND_ERROR));
    return ResponseTemplateDto.<LatestVersionResponse>builder()
        .data(versioningMapper.toLatestVersionResponse(client))
        .build();
  }
}
