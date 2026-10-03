package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.VSUM_BUILD_REUSED;
import static tools.vitruv.methodologist.messages.Message.VSUM_BUILD_STARTED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.service.VsumBuildService;
import tools.vitruv.methodologist.vsum.service.VsumService;

/**
 * Asynchronous VSUM builds: start one, follow its status, and download what it produced.
 *
 * <p>Every endpoint requires the caller to be a member of the VSUM. The synchronous {@code GET
 * /v1/vsums/{id}/build/*} endpoints in {@link VsumController} remain as blocking wrappers around
 * the same pipeline.
 */
@RestController
@RequestMapping("/api/")
@RequiredArgsConstructor
public class VsumBuildController {
  private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");
  private static final MediaType APPLICATION_JAR =
      MediaType.parseMediaType("application/java-archive");

  private final VsumBuildService vsumBuildService;
  private final VsumService vsumService;

  /**
   * Requests a build of the VSUM's current input files.
   *
   * <p>If a successful build of exactly these inputs still has its artifact, it is returned with
   * {@code 200 OK} instead of building again; the same happens if an identical build is already
   * queued or running. Otherwise a new build is started and returned with {@code 202 Accepted}.
   *
   * @param authentication the authenticated Keycloak principal
   * @param id the VSUM to build
   * @param force start a new build even if an up-to-date artifact exists
   * @return the build to follow
   */
  @PostMapping("/v1/vsums/{id}/builds")
  @PreAuthorize("hasRole('user')")
  public ResponseEntity<ResponseTemplateDto<VsumBuildResponse>> requestBuild(
      KeycloakAuthentication authentication,
      @PathVariable Long id,
      @RequestParam(defaultValue = "false") boolean force) {
    String callerEmail = authentication.getParsedToken().getEmail();
    VsumBuildService.BuildRequest request = vsumBuildService.requestBuild(callerEmail, id, force);

    ResponseTemplateDto<VsumBuildResponse> body =
        ResponseTemplateDto.<VsumBuildResponse>builder()
            .data(request.build())
            .message(request.started() ? VSUM_BUILD_STARTED : VSUM_BUILD_REUSED)
            .build();
    return ResponseEntity.status(request.started() ? HttpStatus.ACCEPTED : HttpStatus.OK)
        .body(body);
  }

  /**
   * Lists the builds of the VSUM, newest first.
   *
   * @param authentication the authenticated Keycloak principal
   * @param id the VSUM identifier
   * @param pageNumber zero-based page index (default 0)
   * @param pageSize the size of the page to be returned (default 20)
   * @return the builds of the requested page
   */
  @GetMapping("/v1/vsums/{id}/builds")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<VsumBuildResponse>> findAll(
      KeycloakAuthentication authentication,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "20") int pageSize) {
    String callerEmail = authentication.getParsedToken().getEmail();
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    return ResponseTemplateDto.<List<VsumBuildResponse>>builder()
        .data(vsumBuildService.findAll(callerEmail, id, pageable))
        .build();
  }

  /**
   * Returns the current state of one build.
   *
   * @param authentication the authenticated Keycloak principal
   * @param id the VSUM identifier
   * @param buildId the build identifier
   * @return the build
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the build does not belong to
   *     the VSUM
   */
  @GetMapping("/v1/vsums/{id}/builds/{buildId}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<VsumBuildResponse> findById(
      KeycloakAuthentication authentication, @PathVariable Long id, @PathVariable Long buildId) {
    String callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<VsumBuildResponse>builder()
        .data(vsumBuildService.findBuild(callerEmail, id, buildId))
        .build();
  }

  /**
   * Downloads the fat JAR of a successful build.
   *
   * @param authentication the authenticated Keycloak principal
   * @param id the VSUM identifier
   * @param buildId the build identifier
   * @return the JAR as {@code vsum.jar}
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the build does not belong to
   *     the VSUM, has not succeeded, or its artifact was removed by retention
   */
  @GetMapping("/v1/vsums/{id}/builds/{buildId}/artifact")
  @PreAuthorize("hasRole('user')")
  public ResponseEntity<byte[]> downloadArtifact(
      KeycloakAuthentication authentication, @PathVariable Long id, @PathVariable Long buildId) {
    String callerEmail = authentication.getParsedToken().getEmail();
    byte[] jar = vsumBuildService.getArtifact(callerEmail, id, buildId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JAR);
    headers.setContentDisposition(ContentDisposition.attachment().filename("vsum.jar").build());
    return ResponseEntity.ok().headers(headers).body(jar);
  }

  /**
   * Downloads the deployment bundle (JAR, launchers, Dockerfile, Compose file, README) of a
   * successful build.
   *
   * @param authentication the authenticated Keycloak principal
   * @param id the VSUM identifier
   * @param buildId the build identifier
   * @return the bundle as {@code vsum-deployment.zip}
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the build does not belong to
   *     the VSUM, has not succeeded, or its artifact was removed by retention
   * @throws tools.vitruv.methodologist.exception.BuildArtifactCreationException if the archive
   *     cannot be assembled
   */
  @GetMapping("/v1/vsums/{id}/builds/{buildId}/bundle")
  @PreAuthorize("hasRole('user')")
  public ResponseEntity<byte[]> downloadBundle(
      KeycloakAuthentication authentication, @PathVariable Long id, @PathVariable Long buildId) {
    String callerEmail = authentication.getParsedToken().getEmail();
    byte[] jar = vsumBuildService.getArtifact(callerEmail, id, buildId);
    byte[] bundle = vsumService.createDeploymentBundle(jar);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_ZIP);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename("vsum-deployment.zip").build());
    return ResponseEntity.ok().headers(headers).body(bundle);
  }
}
