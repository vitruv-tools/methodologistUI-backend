package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.util.List;

/**
 * Result of a successful build check of a VSUM.
 *
 * @param constraintRuleSets the names of the constraint rule sets built into the VSUM, each as its
 *     own OCL file; empty if the VSUM has no constraints, {@code null} if the rule sets changed
 *     while the VSUM was built, so the built ones are not known
 */
public record VsumBuildCheckResponse(List<String> constraintRuleSets) {}
