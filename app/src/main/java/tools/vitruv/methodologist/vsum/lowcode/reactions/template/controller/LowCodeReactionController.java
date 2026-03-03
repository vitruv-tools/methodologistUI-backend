package tools.vitruv.methodologist.vsum.lowcode.reactions.template.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadataResponse;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionMetadataService;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;

import java.util.*;

import static tools.vitruv.methodologist.messages.Message.LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.LOWCODE_REACTION_CREATED_SUCCESSFULLY;

@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
public class LowCodeReactionController {
    private final LowCodeReactionService lowCodeReactionService;
    private final LowCodeReactionMetadataService lowCodeReactionMetadataService;

    /**
     * Creates a low-code reaction.
     *
     * @param authentication         the Keycloak authentication object containing user details
     * @param lowCodeReactionRequestBase the low-code reaction the user wants to create
     * @return ResponseTemplateDto containing a success message
     * @throws Exception if lowcode reaction creation fails
     */
    @Operation(summary = "Create a low-code reaction", description = "Creates a low-code reaction with the given configuration")
    @PostMapping(
            value = "/lowcode",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('user')")
    public ResponseTemplateDto<FileStorageResponse> upload(
            KeycloakAuthentication authentication,
            @Parameter(
                    description = "Key-Value configuration for template",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            @RequestBody @Validated
            LowCodeReactionRequestBase lowCodeReactionRequestBase)
            throws Exception {
        String email = authentication.getParsedToken().getEmail();
        FileStorageResponse response = lowCodeReactionService.generateAndSaveReaction(email, lowCodeReactionRequestBase);

        return ResponseTemplateDto.<FileStorageResponse>builder()
                .data(response)
                .message(LOWCODE_REACTION_CREATED_SUCCESSFULLY)
                .build();
    }

    @Operation(summary = "Get metadata for low-code reactions", description = "Gets the configuration metadata for a low-code reactions")
    @GetMapping("/lowcode-metadata")
    @PreAuthorize("hasRole('user')")
    public ResponseTemplateDto<LowCodeReactionMetadataResponse> getAllLowCodeReactionMetadata() {
        return ResponseTemplateDto.<LowCodeReactionMetadataResponse>builder().data(lowCodeReactionMetadataService.getAllLowCodeReactionMetadata()).message(LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY).build();
    }
}