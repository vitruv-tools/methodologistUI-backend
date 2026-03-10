package tools.vitruv.methodologist.vsum.lowcode.reactions.template.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.model.FileStorage;
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

    public FileStorage generateAndSaveReaction(String callerUserEmail, LowCodeReactionRequestBase lowCodeReactionRequestBase, FileStorage fileStorage) throws Exception {
        // Step 1: Run template engine
        var output = applyTemplate(lowCodeReactionRequestBase);
        var data = output.getBytes(StandardCharsets.UTF_8);

        // Step 2: Create / update the file in the database
        FileStorageResponse fileStorageResponse;
        if (fileStorage != null) {
            fileStorageResponse = fileStorageService.updateFile(callerUserEmail, fileStorage.getId(), data, fileStorage.getFilename(), fileStorage.getContentType());
        } else {
            var fileName = lowCodeReactionRequestBase.getName() + ".reactions";
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            fileStorageResponse = fileStorageService.storeFile(callerUserEmail, data, fileName, "text/plain", FileEnumType.REACTION);
        }

        // Step 3: Build response. For now we are fine with the file storage response as dto
        return fileStorageService.getFile(fileStorageResponse.getId());
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
