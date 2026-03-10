package tools.vitruv.methodologist.vsum.lowcode.reactions.template.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadataResponse;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionMetadataService;

import java.util.*;

import static tools.vitruv.methodologist.messages.Message.LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY;

@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
public class LowCodeReactionController {
    private final LowCodeReactionMetadataService lowCodeReactionMetadataService;

    @Operation(summary = "Get metadata for low-code reactions", description = "Gets the configuration metadata for a low-code reactions")
    @GetMapping("/lowcode-metadata")
    @PreAuthorize("hasRole('user')")
    public ResponseTemplateDto<LowCodeReactionMetadataResponse> getAllLowCodeReactionMetadata() {
        return ResponseTemplateDto.<LowCodeReactionMetadataResponse>builder().data(lowCodeReactionMetadataService.getAllLowCodeReactionMetadata()).message(LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY).build();
    }
}