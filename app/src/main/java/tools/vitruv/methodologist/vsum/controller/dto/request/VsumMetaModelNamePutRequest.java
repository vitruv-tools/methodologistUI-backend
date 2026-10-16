package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request payload for renaming a meta model within a VSUM. */
public record VsumMetaModelNamePutRequest(@NotBlank String name) {}
