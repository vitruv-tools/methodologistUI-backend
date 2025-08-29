package tools.vitruv.methodologist.vsum.mapper;

import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.model.Vsum;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-08-29T19:19:42+0200",
    comments = "version: 1.6.2, compiler: javac, environment: Java 17.0.14 (JetBrains s.r.o.)"
)
@Component
public class VsumMapperImpl implements VsumMapper {

    @Override
    public Vsum toVsum(VsumPostRequest vsumPostRequest) {
        if ( vsumPostRequest == null ) {
            return null;
        }

        Vsum.VsumBuilder vsum = Vsum.builder();

        vsum.name( vsumPostRequest.getName() );

        return vsum.build();
    }

    @Override
    public void updateByVsumPutRequest(VsumPutRequest vsumPutRequest, Vsum vsum) {
        if ( vsumPutRequest == null ) {
            return;
        }

        vsum.setName( vsumPutRequest.getName() );
    }

    @Override
    public VsumResponse toVsumResponse(Vsum vsum) {
        if ( vsum == null ) {
            return null;
        }

        VsumResponse.VsumResponseBuilder vsumResponse = VsumResponse.builder();

        vsumResponse.id( vsum.getId() );
        vsumResponse.name( vsum.getName() );
        vsumResponse.createdAt( vsum.getCreatedAt() );
        vsumResponse.updatedAt( vsum.getUpdatedAt() );
        vsumResponse.removedAt( vsum.getRemovedAt() );

        return vsumResponse.build();
    }
}
