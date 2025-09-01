/** Generated workflow */
module GeneratedWorkflow

import org.eclipse.emf.mwe.utils.*
import org.eclipse.xtext.generator.*
import org.eclipse.emf.codegen.ecore.generator.Generator

var targetDir = "${(items[0].targetDir)!'generated'}"

Workflow {
<#list items as it>
    // Model: ${(it.modelName)!it.genmodelPath}
    // nsURI: ${it.nsUri}
    component = org.eclipse.emf.mwe2.runtime.workflow.Component {
    // put your real MWE2 components here, this is just a placeholder
    }
</#list>
}