package tools.vitruv.methodologist.general.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.service.GeneralService;

/**
 * REST controller for managing general application operations. Provides endpoints for retrieving
 * system-wide information.
 *
 * @see GeneralService
 */
@RestController
@RequestMapping("/api/")
@Validated
public class GeneralController {

  private final GeneralService generalService;

  /**
   * Constructs a new GeneralController with the specified service.
   *
   * @param generalService the service for general operations
   */
  public GeneralController(GeneralService generalService) {
    this.generalService = generalService;
  }

  /**
   * Retrieves the latest version information for a specified client.
   *
   * @param clientName the name of the client to get version information for
   * @return ResponseTemplateDto containing the latest version information
   */
  @GetMapping("v1/general/latestVersion/{clientName}")
  public ResponseTemplateDto<LatestVersionResponse> getLatestVersion(
      @PathVariable(value = "clientName") String clientName) {
    return generalService.getLatestVersion(clientName);
  }
}
