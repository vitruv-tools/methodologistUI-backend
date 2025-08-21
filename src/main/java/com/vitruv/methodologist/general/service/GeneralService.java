package com.vitruv.methodologist.general.service;

import static com.vitruv.methodologist.messages.Error.CLIENT_NOT_FOUND_ERROR;

import com.vitruv.methodologist.ResponseTemplateDto;
import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import com.vitruv.methodologist.general.mapper.VersioningMapper;
import com.vitruv.methodologist.general.model.repository.VersioningRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for managing general application operations.
 * Handles version information retrieval and other general functionality.
 */
@Service
public class GeneralService {

  private final VersioningRepository versioningRepository;
  private final VersioningMapper versioningMapper;

  /**
   * Constructs a new GeneralService with the specified dependencies.
   *
   * @param versioningRepository repository for version information
   * @param versioningMapper mapper for version-related conversions
   */
  public GeneralService(
      VersioningRepository versioningRepository, VersioningMapper versioningMapper) {
    this.versioningRepository = versioningRepository;
    this.versioningMapper = versioningMapper;
  }

  /**
   * Retrieves the latest version information for a specified client application.
   *
   * @param clientName the name of the client application
   * @return ResponseTemplateDto containing the latest version information
   * @throws NotFoundException if the specified client is not found
   */
  @Transactional
  public ResponseTemplateDto<LatestVersionResponse> getLatestVersion(String clientName) {
    var client =
        versioningRepository
            .findTopByAppNameOrderByIdDesc(clientName)
            .orElseThrow(() -> new NotFoundException(CLIENT_NOT_FOUND_ERROR));
    return ResponseTemplateDto.<LatestVersionResponse>builder()
        .data(versioningMapper.toLatestVersionResponse(client))
        .build();
  }
}
