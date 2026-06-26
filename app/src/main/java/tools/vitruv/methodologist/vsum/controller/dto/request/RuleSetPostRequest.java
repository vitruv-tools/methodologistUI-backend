package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for creating a new OCL constraint rule set. */
public record RuleSetPostRequest(
    @NotBlank String name, String color, String description, String oclContent) {}
