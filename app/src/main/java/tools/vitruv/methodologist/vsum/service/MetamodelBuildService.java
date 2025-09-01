package tools.vitruv.methodologist.vsum.service;

import lombok.Builder;
import lombok.Getter;

/** Service for validating and building uploaded metamodels. */
public interface MetamodelBuildService {
  /**
   * Validates and builds a metamodel using the given input.
   *
   * @param input input data (ecore/genmodel bytes, flags)
   * @return result of the build and validation
   */
  BuildResult buildAndValidate(MetamodelBuildInput input);

  /** Input data for metamodel build and validation. */
  @Builder
  @Getter
  class MetamodelBuildInput {
    Long metaModelId;
    byte[] ecoreBytes;
    byte[] genModelBytes;
    boolean runMwe2;
  }

  /** Result of metamodel build and validation. */
  @Builder
  @Getter
  class BuildResult {
    boolean success;
    int errors;
    int warnings;
    String report; // human-readable summary
    String discoveredNsUris; // comma-separated, optional
  }
}
