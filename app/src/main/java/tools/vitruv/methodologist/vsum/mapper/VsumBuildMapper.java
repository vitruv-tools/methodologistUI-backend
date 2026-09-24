package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.model.VsumBuild;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VsumBuildMapper {

  @Mapping(source = "vsum.id", target = "vsumId")
  @Mapping(source = "requestedBy.email", target = "requestedBy")
  @Mapping(target = "artifactAvailable", expression = "java(build.getArtifact() != null)")
  VsumBuildResponse toResponse(VsumBuild build);
}
