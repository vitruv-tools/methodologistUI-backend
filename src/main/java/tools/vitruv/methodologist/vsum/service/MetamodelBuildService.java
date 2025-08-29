package tools.vitruv.methodologist.vsum.service;

import lombok.Builder;
import lombok.Getter;
import tools.vitruv.methodologist.general.model.FileStorage;

public interface MetamodelBuildService {
  BuildResult buildAndValidate(MetamodelBuildInput input);

  @Builder
  @Getter
  public class MetamodelBuildInput {
    private Long metaModelId;
    private FileStorage ecoreFile;
    private FileStorage genModelFile;
    private boolean runMwe2; // your flag; true to execute your fixed workflow
  }

  @Builder
  @Getter
  public class BuildResult {
    private boolean success;
    private int errors;
    private int warnings;
    private String report; // human-readable HTML/text summary
    private String discoveredNsUris; // e.g., "http://foo, http://bar"
  }
}
