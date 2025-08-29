package tools.vitruv.methodologist.vsum.mapper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-08-29T19:19:42+0200",
    comments = "version: 1.6.2, compiler: javac, environment: Java 17.0.14 (JetBrains s.r.o.)"
)
@Component
public class MetaModelMapperImpl implements MetaModelMapper {

    @Override
    public MetaModel toMetaModel(MetaModelPostRequest metaModelPostRequest) {
        if ( metaModelPostRequest == null ) {
            return null;
        }

        MetaModel.MetaModelBuilder metaModel = MetaModel.builder();

        metaModel.name( metaModelPostRequest.getName() );
        metaModel.description( metaModelPostRequest.getDescription() );
        metaModel.domain( metaModelPostRequest.getDomain() );
        List<String> list = metaModelPostRequest.getKeyword();
        if ( list != null ) {
            metaModel.keyword( new ArrayList<String>( list ) );
        }

        return metaModel.build();
    }

    @Override
    public MetaModelResponse toMetaModelResponse(MetaModel metaModel) {
        if ( metaModel == null ) {
            return null;
        }

        MetaModelResponse.MetaModelResponseBuilder metaModelResponse = MetaModelResponse.builder();

        metaModelResponse.ecoreFileId( metaModelEcoreFileId( metaModel ) );
        metaModelResponse.genModelFileId( metaModelGenModelFileId( metaModel ) );
        metaModelResponse.id( metaModel.getId() );
        metaModelResponse.name( metaModel.getName() );
        metaModelResponse.createdAt( metaModel.getCreatedAt() );
        metaModelResponse.updatedAt( metaModel.getUpdatedAt() );
        metaModelResponse.removedAt( metaModel.getRemovedAt() );

        return metaModelResponse.build();
    }

    private Long metaModelEcoreFileId(MetaModel metaModel) {
        FileStorage ecoreFile = metaModel.getEcoreFile();
        if ( ecoreFile == null ) {
            return null;
        }
        return ecoreFile.getId();
    }

    private Long metaModelGenModelFileId(MetaModel metaModel) {
        FileStorage genModelFile = metaModel.getGenModelFile();
        if ( genModelFile == null ) {
            return null;
        }
        return genModelFile.getId();
    }
}
