package tools.vitruv.methodologist.builder.configuration;

import java.io.File;

public record MetamodelLocation(File ecore, File genmodel, String nsUri) {}
