package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RuleSetPutRequest(
    @NotBlank String name, String color, String description, String oclContent) {}
