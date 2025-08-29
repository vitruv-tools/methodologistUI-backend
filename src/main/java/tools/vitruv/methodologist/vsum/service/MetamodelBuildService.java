package tools.vitruv.methodologist.vsum.service;

import lombok.Builder;
import lombok.Getter;

public interface MetamodelBuildService {
  BuildResult buildAndValidate(MetamodelBuildInput in);

  @Builder @Getter
  class MetamodelBuildInput {
    Long metaModelId;
    byte[] ecoreBytes;
    byte[] genModelBytes;
    boolean runMwe2;
  }

  @Builder @Getter
  class BuildResult {
    boolean success;
    int errors;
    int warnings;
    String report;            // human-readable summary
    String discoveredNsUris;  // comma-separated, optional
  }
}
