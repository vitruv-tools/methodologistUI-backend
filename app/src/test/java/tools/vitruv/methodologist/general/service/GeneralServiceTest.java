package tools.vitruv.methodologist.general.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.mapper.VersioningMapper;
import tools.vitruv.methodologist.general.model.Versioning;
import tools.vitruv.methodologist.general.model.repository.VersioningRepository;

class GeneralServiceTest {

  @Mock private VersioningRepository versioningRepository;

  @Mock private VersioningMapper versioningMapper;

  private GeneralService generalService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    generalService = new GeneralService(versioningRepository, versioningMapper);
  }

  @Test
  void getLatestVersion_returnsLatestVersionResponse() {

    String clientName = "desktop-client";

    Versioning versioning = new Versioning();
    LatestVersionResponse response = new LatestVersionResponse();

    when(versioningRepository.findTopByAppNameOrderByIdDesc(clientName))
        .thenReturn(Optional.of(versioning));

    when(versioningMapper.toLatestVersionResponse(versioning)).thenReturn(response);

    ResponseTemplateDto<LatestVersionResponse> result = generalService.getLatestVersion(clientName);

    assertEquals(response, result.getData());
  }

  @Test
  void getLatestVersion_throwsNotFoundException_whenClientDoesNotExist() {

    String clientName = "unknown-client";

    when(versioningRepository.findTopByAppNameOrderByIdDesc(clientName))
        .thenReturn(Optional.empty());

    NotFoundException exception =
        assertThrows(NotFoundException.class, () -> generalService.getLatestVersion(clientName));

    assertEquals("Client not found!", exception.getMessage());
  }
}
