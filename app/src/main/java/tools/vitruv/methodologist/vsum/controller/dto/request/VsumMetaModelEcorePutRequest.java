package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for replacing the Ecore content of a meta model within a VSUM.
 *
 * @param ecore the complete serialized Ecore (XMI) content of the project's meta model copy
 */
public record VsumMetaModelEcorePutRequest(@NotBlank String ecore) {}
