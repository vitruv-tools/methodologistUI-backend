package tools.vitruv.methodologist.general.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Message.FILE_UPLOADED_SUCCESSFULLY;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.service.FileStorageService;

@ExtendWith(MockitoExtension.class)
class FileStorageControllerTest {

  private static final String EMAIL = "test@example.com";

  @InjectMocks FileStorageController controller;
  @Mock FileStorageService fileStorageService;
  @Mock KeycloakAuthentication authentication;
  @Mock KeycloakAuthentication.ParsedToken parsedToken;

  @Test
  void updateNeoJoin_updatesTheFileAsANeoJoinFile() throws Exception {
    when(authentication.getParsedToken()).thenReturn(parsedToken);
    when(parsedToken.getEmail()).thenReturn(EMAIL);
    MockMultipartFile file =
        new MockMultipartFile("file", "Brake.nj", "text/plain", "export Brake".getBytes());
    FileStorageResponse updated = FileStorageResponse.builder().id(20L).build();
    when(fileStorageService.updateFile(EMAIL, 20L, FileEnumType.NEO_JOIN, file))
        .thenReturn(updated);

    ResponseTemplateDto<FileStorageResponse> response =
        controller.updateNeoJoin(authentication, file, 20L);

    assertThat(response.getData()).isSameAs(updated);
    assertThat(response.getMessage()).isEqualTo(FILE_UPLOADED_SUCCESSFULLY);
  }
}
