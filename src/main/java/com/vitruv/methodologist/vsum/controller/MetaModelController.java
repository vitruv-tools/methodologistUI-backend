package com.vitruv.methodologist.vsum.controller;

import static com.vitruv.methodologist.messages.Message.META_MODEL_CREATED_SUCCESSFULLY;

import com.vitruv.methodologist.ResponseTemplateDto;
import com.vitruv.methodologist.config.KeycloakAuthentication;
import com.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import com.vitruv.methodologist.vsum.service.MetaModelService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/")
@Validated
public class MetaModelController {
  private final MetaModelService metaModelService;

    public MetaModelController(MetaModelService metaModelService) {
        this.metaModelService = metaModelService;
    }

  @PostMapping("/v1/meta-models")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> create(
      KeycloakAuthentication authentication,
      @Valid @RequestBody MetaModelPostRequest metaModelPostRequest) {
    var callerEmail = authentication.getParsedToken().getEmail();
    metaModelService.create(callerEmail, metaModelPostRequest);
    return ResponseTemplateDto.<Void>builder().message(META_MODEL_CREATED_SUCCESSFULLY).build();
  }

  @GetMapping("/v1/meta-models")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<MetaModelResponse>> findById(
      KeycloakAuthentication authentication) {
    var callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<List<MetaModelResponse>>builder()
        .data(metaModelService.findAllByUser(callerEmail))
        .build();
  }
}
