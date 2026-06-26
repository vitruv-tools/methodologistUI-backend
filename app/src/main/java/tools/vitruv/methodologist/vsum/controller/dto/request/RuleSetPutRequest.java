package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for updating an existing OCL constraint rule set. */
public record RuleSetPutRequest(
    @NotBlank String name, String color, String description, String oclContent) {}
