package tools.vitruv.methodologist.general.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.mapper.VersioningMapper;
import tools.vitruv.methodologist.general.model.Versioning;
import tools.vitruv.methodologist.general.model.repository.VersioningRepository;

@ExtendWith(MockitoExtension.class)
class GeneralServiceTest {

  @Mock private VersioningRepository versioningRepository;

  @Mock private VersioningMapper versioningMapper;

  @InjectMocks private GeneralService generalService;

  @Test
  void getLatestVersion_existingClient_returnsLatestVersionResponse() {
    final String clientName = "web-client";
    final Versioning versioning =
        Versioning.builder()
            .id(1L)
            .appName(clientName)
            .version("1.0.0")
            .forceUpdate(false)
            .build();
    final LatestVersionResponse latestVersionResponse =
        LatestVersionResponse.builder().version("1.0.0").forceUpdate(false).build();

    when(versioningRepository.findTopByAppNameOrderByIdDesc(clientName))
        .thenReturn(Optional.of(versioning));
    when(versioningMapper.toLatestVersionResponse(versioning)).thenReturn(latestVersionResponse);

    final ResponseTemplateDto<LatestVersionResponse> result =
        generalService.getLatestVersion(clientName);

    assertNotNull(result);
    assertNotNull(result.getData());
    assertEquals("1.0.0", result.getData().getVersion());
    assertEquals(false, result.getData().getForceUpdate());
    verify(versioningRepository).findTopByAppNameOrderByIdDesc(clientName);
    verify(versioningMapper).toLatestVersionResponse(versioning);
  }

  @Test
  void getLatestVersion_unknownClient_throwsNotFoundException() {
    final String clientName = "unknown-client";
    when(versioningRepository.findTopByAppNameOrderByIdDesc(clientName))
        .thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> generalService.getLatestVersion(clientName));

    verify(versioningRepository).findTopByAppNameOrderByIdDesc(clientName);
  }
}
