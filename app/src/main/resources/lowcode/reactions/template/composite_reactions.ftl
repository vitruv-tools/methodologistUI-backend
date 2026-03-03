<#-- Import namespaces -->
import "${model1Uri}" as ${model1Alias}
import "${model2Uri}" as ${model2Alias}

<#-- Define reactions -->
reactions: ${reactionName}
in reaction to changes in ${model1Alias}
execute actions in ${model2Alias}

<#-- Loop over items to import -->
<#list imports as imp>
    import ${imp}
</#list>