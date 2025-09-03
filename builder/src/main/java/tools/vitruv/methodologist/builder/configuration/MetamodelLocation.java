package tools.vitruv.methodologist.builder.configuration;

import java.io.File;

/**
 * Immutable holder for a metamodel definition. Represents the pair of Ecore and GenModel files
 * together with the namespace URI. Used as an input descriptor for validation and workflow
 * generation.
 */
public record MetamodelLocation(File ecore, File genmodel, String nsUri) {}
