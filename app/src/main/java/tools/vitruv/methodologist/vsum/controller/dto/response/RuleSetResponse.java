package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;

public record RuleSetResponse(
    Long id,
    Long vsumId,
    String name,
    String color,
    String description,
    String oclContent,
    Instant createdAt,
    Instant updatedAt) {}
