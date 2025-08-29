package tools.vitruv.methodologist.general.mapper;

import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.model.Versioning;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-08-29T19:19:42+0200",
    comments = "version: 1.6.2, compiler: javac, environment: Java 17.0.14 (JetBrains s.r.o.)"
)
@Component
public class VersioningMapperImpl implements VersioningMapper {

    @Override
    public LatestVersionResponse toLatestVersionResponse(Versioning versioning) {
        if ( versioning == null ) {
            return null;
        }

        LatestVersionResponse.LatestVersionResponseBuilder latestVersionResponse = LatestVersionResponse.builder();

        latestVersionResponse.forceUpdate( versioning.getForceUpdate() );
        latestVersionResponse.version( versioning.getVersion() );

        return latestVersionResponse.build();
    }
}
