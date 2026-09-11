package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;

/** Response DTO representing an OCL constraint rule set. */
public record RuleSetResponse(
    Long id,
    Long vsumId,
    String name,
    String color,
    String description,
    String oclContent,
    Instant createdAt,
    Instant updatedAt) {}
