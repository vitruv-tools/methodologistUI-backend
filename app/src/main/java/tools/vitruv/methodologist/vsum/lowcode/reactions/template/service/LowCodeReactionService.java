package tools.vitruv.methodologist.vsum.lowcode.reactions.template.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadataResponse;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import freemarker.template.*;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LowCodeReactionService {
    private final LowCodeReactionMetadataService lowCodeReactionMetadataService;
    private final FileStorageService fileStorageService;
    private final VsumUserRepository vsumUserRepository;

    public FileStorageResponse generateAndSaveReaction(String callerUserEmail, LowCodeReactionRequestBase lowCodeReactionRequestBase) throws Exception {
        // Step 1: Check if this template reaction already exists for this source, target, and template
        // Get the vsum user relationship for the request
        var optVsumUser = vsumUserRepository.findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(lowCodeReactionRequestBase.getVsumId(), callerUserEmail);
        if (optVsumUser.isEmpty()) {
            throw new IllegalArgumentException(String.format("User does not have access to vsum %s", lowCodeReactionRequestBase.getVsumId()));
            // TODO(REINBOLD): Can this happen if we are creating a new vsum and have never saved? In that case we should just continue
        }
        var vsumUser = optVsumUser.get();

        var metaModelRelations = vsumUser.getVsum().getMetaModelRelations();
        // Find fine granular model relation for the same template and the same source and target, if it already exists
        var optFineGranularModelRelation = metaModelRelations.stream().flatMap(mr -> mr.getFineGranularMetaModelRelationSet().stream()).filter(fmr -> fmr.getSourceId().equals(lowCodeReactionRequestBase.getSource()) && fmr.getTargetId().equals(lowCodeReactionRequestBase.getTarget())).findFirst();

        // Step 2: Run template engine
        var output = applyTemplate(lowCodeReactionRequestBase);
        var data = output.getBytes(StandardCharsets.UTF_8);

        // Step 3: Create / update the file in the database
        FileStorageResponse fileStorageResponse;
        if (optFineGranularModelRelation.isPresent()) {
            var reactFS = optFineGranularModelRelation.get().getReactionFileStorage();
            fileStorageResponse = fileStorageService.updateFile(callerUserEmail, reactFS.getId(), data, reactFS.getFilename(), reactFS.getContentType());
        } else {
            var fileName = lowCodeReactionRequestBase.getName() + ".reactions";
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            fileStorageResponse = fileStorageService.storeFile(callerUserEmail, data, fileName, "text/plain", FileEnumType.REACTION);
        }

        // Step 4: Build response. For now we are fine with the file storage response as dto
        return FileStorageResponse.builder().id(fileStorageResponse.getId()).build();
    }

    protected String applyTemplate(LowCodeReactionRequestBase lowCodeReactionRequestBase) throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "/lowcode/reactions/template"
        );
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        StringWriter writer = new StringWriter();
        Template freemakerTemplate = cfg.getTemplate(lowCodeReactionRequestBase.getName() + ".ftl");
        freemakerTemplate.process(lowCodeReactionRequestBase.toTemplateData(), writer);
        return writer.toString();
    }
}
