package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.general.model.FileStorage;

class UploadedFilesTest {

  @Test
  void resolve_prefersThePartOfTheSource_whenTwoPartsShareAName() {
    UploadedFiles uploads = new UploadedFiles();
    uploads.add(SetupServiceApiHandler.METAMODEL_FILES_PART, "shared.ecore", file(1L));
    uploads.add(SetupServiceApiHandler.VIEW_FILES_PART, "shared.ecore", file(2L));

    assertThat(
            uploads.resolve(
                List.of(
                    problem("METAMODEL", "shared.ecore"),
                    problem("VIEWS", "shared.ecore"),
                    problem("INTERNAL", "shared.ecore"))))
        .extracting(BuildProblem::fileId)
        .containsExactly(1L, 2L, null);
  }

  @Test
  void resolve_fallsBackToAnUnambiguousName_forOtherSources() {
    UploadedFiles uploads = new UploadedFiles();
    uploads.add(SetupServiceApiHandler.CONSTRAINT_FILES_PART, "rules.ocl", file(3L));

    assertThat(
            uploads.resolve(
                List.of(
                    problem("INFRASTRUCTURE", "rules.ocl"),
                    problem("REACTIONS", "rules.ocl"),
                    problem(null, "rules.ocl"),
                    problem("CONSTRAINTS", null))))
        .extracting(BuildProblem::fileId)
        .containsExactly(3L, 3L, 3L, null);
  }

  @Test
  void resolve_dropsAFileIdTheSetupServiceSent() {
    UploadedFiles uploads = new UploadedFiles();

    assertThat(
            uploads.resolve(
                List.of(new BuildProblem("VIEWS", "v.nj", 99L, 1, 1, "m", null, null, null))))
        .extracting(BuildProblem::fileId)
        .containsOnlyNulls();
  }

  private static BuildProblem problem(String source, String file) {
    return new BuildProblem(source, file, null, null, null, "message", null, null, null);
  }

  private static FileStorage file(Long id) {
    FileStorage file = new FileStorage();
    file.setId(id);
    return file;
  }
}
