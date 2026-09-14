import java.io.File;

import "${model1Uri}" as ${model1Alias}
import "${model2Uri}" as ${model2Alias}

reactions: ${reactionName}
in reaction to changes in ${model1Alias}
execute actions in ${model2Alias}

reaction RootObjectInsertedIn${model1Alias} {
    after element ${model1Alias}::${model1RootType} inserted as root
    call createAndRegister${model2RootType}(newValue)
}

routine createAndRegister${model2RootType}(
    ${model1Alias}::${model1RootType} ${model1RootVar}
) {
    match {
        require absence of ${model2Alias}::${model2RootType}
        corresponding to ${model1RootVar}
    }

    create {
        val ${model2Alias}Root =
        new ${model2Alias}::${model2RootType}
    }

    update {
        addCorrespondenceBetween(${model1RootVar}, ${model2Alias}Root)
    }
}