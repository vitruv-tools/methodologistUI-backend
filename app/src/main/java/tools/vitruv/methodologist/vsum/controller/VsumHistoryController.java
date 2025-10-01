package tools.vitruv.methodologist.vsum.controller;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.service.VsumHistoryService;

@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumHistoryController {
  VsumHistoryService vsumHistoryService;

  @GetMapping("/v1/vsums-history")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> findAllByUser(KeycloakAuthentication authentication) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumHistoryService.findAllByUser(callerEmail);
    return ResponseTemplateDto.<Void>builder().message("HOORA").build();
  }
}
