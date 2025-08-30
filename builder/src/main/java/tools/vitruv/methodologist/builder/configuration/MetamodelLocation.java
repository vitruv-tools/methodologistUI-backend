package tools.vitruv.methodologist.builder.configuration;

import java.io.File;

/**
 * The MetamodelLocation class is used to store the location of a metamodel and its corresponding
 * genmodel.
 */
public record MetamodelLocation(File metamodel, File genmodel, String genmodelUri) {}
