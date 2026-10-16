package tools.vitruv.methodologist.vsum.lowcode.reactions.template.service;

import static tools.vitruv.methodologist.messages.Error.LOWCODE_TEMPLATE_APPLY_ERROR;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.LowCodeTemplateException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

/** Service for generating and saving reactions from low-code templates. */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LowCodeReactionService {
  FileStorageService fileStorageService;

  /**
   * Generates a reaction from the request template and stores it as a {@link FileEnumType#REACTION}
   * file. When {@code fileStorage} is present the existing file is overwritten.
   *
   * @param callerUserEmail the email of the caller
   * @param lowCodeReactionRequestBase the low-code reaction request
   * @param fileStorage the existing file storage, or {@code null} to create a new file
   * @return the saved file storage
   */
  public FileStorage generateAndSaveReaction(
      String callerUserEmail,
      LowCodeReactionRequestBase lowCodeReactionRequestBase,
      FileStorage fileStorage) {
    String output = applyTemplate(lowCodeReactionRequestBase);
    byte[] data = output.getBytes(StandardCharsets.UTF_8);

    FileStorageResponse fileStorageResponse;
    if (fileStorage != null) {
      fileStorageResponse =
          fileStorageService.updateFile(
              callerUserEmail,
              fileStorage.getId(),
              data,
              fileStorage.getFilename(),
              fileStorage.getContentType());
    } else {
      String fileName = lowCodeReactionRequestBase.getName() + ".reactions";
      fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
      fileStorageResponse =
          fileStorageService.storeFile(
              callerUserEmail, data, fileName, "text/plain", FileEnumType.REACTION);
    }

    return fileStorageService.getFile(fileStorageResponse.getId());
  }

  /**
   * Applies the FreeMarker template named {@code {request.name}.ftl}.
   *
   * @param lowCodeReactionRequestBase the low-code reaction request
   * @return the generated reaction as a string
   * @throws LowCodeTemplateException if the template cannot be loaded or processed
   */
  public String applyTemplate(LowCodeReactionRequestBase lowCodeReactionRequestBase) {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
    cfg.setClassLoaderForTemplateLoading(
        getClass().getClassLoader(), "/lowcode/reactions/template");
    cfg.setDefaultEncoding("UTF-8");
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(false);
    cfg.setWrapUncheckedExceptions(true);

    try {
      StringWriter writer = new StringWriter();
      Template freemarkerTemplate = cfg.getTemplate(lowCodeReactionRequestBase.getName() + ".ftl");
      freemarkerTemplate.process(lowCodeReactionRequestBase.toTemplateData(), writer);
      return writer.toString();
    } catch (IOException | TemplateException e) {
      throw new LowCodeTemplateException(LOWCODE_TEMPLATE_APPLY_ERROR, e);
    }
  }
}
