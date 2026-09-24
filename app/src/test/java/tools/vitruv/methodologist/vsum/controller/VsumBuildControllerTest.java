package tools.vitruv.methodologist.vsum.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Message.VSUM_BUILD_REUSED;
import static tools.vitruv.methodologist.messages.Message.VSUM_BUILD_STARTED;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;
import tools.vitruv.methodologist.vsum.service.VsumBuildService;
import tools.vitruv.methodologist.vsum.service.VsumService;

@ExtendWith(MockitoExtension.class)
class VsumBuildControllerTest {

  private static final String EMAIL = "test@example.com";

  @InjectMocks VsumBuildController controller;
  @Mock VsumBuildService vsumBuildService;
  @Mock VsumService vsumService;
  @Mock KeycloakAuthentication authentication;
  @Mock KeycloakAuthentication.ParsedToken parsedToken;

  private VsumBuildResponse build;

  @BeforeEach
  void setUp() {
    when(authentication.getParsedToken()).thenReturn(parsedToken);
    when(parsedToken.getEmail()).thenReturn(EMAIL);
    build = VsumBuildResponse.builder().id(42L).vsumId(7L).status(VsumBuildStatus.QUEUED).build();
  }

  @Test
  void requestBuild_returnsAccepted_whenANewBuildWasStarted() {
    when(vsumBuildService.requestBuild(EMAIL, 7L, false))
        .thenReturn(new VsumBuildService.BuildRequest(build, true));

    ResponseEntity<ResponseTemplateDto<VsumBuildResponse>> response =
        controller.requestBuild(authentication, 7L, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().getData()).isSameAs(build);
    assertThat(response.getBody().getMessage()).isEqualTo(VSUM_BUILD_STARTED);
  }

  @Test
  void requestBuild_returnsOk_whenAnExistingBuildWasReused() {
    build.setStatus(VsumBuildStatus.SUCCEEDED);
    when(vsumBuildService.requestBuild(EMAIL, 7L, true))
        .thenReturn(new VsumBuildService.BuildRequest(build, false));

    ResponseEntity<ResponseTemplateDto<VsumBuildResponse>> response =
        controller.requestBuild(authentication, 7L, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getMessage()).isEqualTo(VSUM_BUILD_REUSED);
  }

  @Test
  void findAll_pagesThroughTheBuildService() {
    when(vsumBuildService.findAll(EMAIL, 7L, PageRequest.of(1, 5))).thenReturn(List.of(build));

    ResponseTemplateDto<List<VsumBuildResponse>> response =
        controller.findAll(authentication, 7L, 1, 5);

    assertThat(response.getData()).containsExactly(build);
  }

  @Test
  void findById_returnsTheBuild() {
    when(vsumBuildService.findBuild(EMAIL, 7L, 42L)).thenReturn(build);

    assertThat(controller.findById(authentication, 7L, 42L).getData()).isSameAs(build);
  }

  @Test
  void downloadArtifact_returnsTheJarAsAttachment() {
    byte[] jar = "JAR".getBytes(StandardCharsets.UTF_8);
    when(vsumBuildService.getArtifact(EMAIL, 7L, 42L)).thenReturn(jar);

    ResponseEntity<byte[]> response = controller.downloadArtifact(authentication, 7L, 42L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(jar);
    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("application/java-archive");
    assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("vsum.jar");
  }

  @Test
  void downloadBundle_packagesTheArtifactAsDeploymentBundle() {
    byte[] jar = "JAR".getBytes(StandardCharsets.UTF_8);
    byte[] zip = "ZIP".getBytes(StandardCharsets.UTF_8);
    when(vsumBuildService.getArtifact(EMAIL, 7L, 42L)).thenReturn(jar);
    when(vsumService.createDeploymentBundle(jar)).thenReturn(zip);

    ResponseEntity<byte[]> response = controller.downloadBundle(authentication, 7L, 42L);

    assertThat(response.getBody()).isEqualTo(zip);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/zip");
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .isEqualTo("vsum-deployment.zip");
  }
}
