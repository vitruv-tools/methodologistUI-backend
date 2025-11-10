package tools.vitruv.methodologist.vsum.mapper;

import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;

/**
 * MapStruct mapper that converts a domain {@link Vsum} aggregate into a serializable {@link
 * VsumRepresentation} used for VSUM history snapshots.
 *
 * <p>Unmapped targets are ignored and the mapper is exposed as a Spring bean.
 *
 * @see Mapper
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VsumHistoryMapper {

  /**
   * Builds a {@link VsumRepresentation} from the given {@link Vsum}.
   *
   * <p>Populates: \- \`vsumUsers\`: IDs of users from the VSUM aggregate. \- \`metaModels\`: IDs of
   * meta model sources. \- \`metaModelsRealation\`: Relation DTOs produced by {@link
   * #toMetaModelsRelation(Set)}.
   *
   * @param vsum the aggregate to transform; must not be \`null\`
   * @return a new representation reflecting the current VSUM state
   */
  default VsumRepresentation toVsumRepresentation(Vsum vsum) {
    return VsumRepresentation.builder()
        .vsumUsers(
            vsum.getVsumUsers().stream()
                .map(vsumUser -> vsumUser.getUser().getId())
                .collect(Collectors.toSet()))
        .metaModels(
            vsum.getVsumMetaModels().stream()
                .map(metaModel -> metaModel.getMetaModel().getSource().getId())
                .collect(Collectors.toSet()))
        .metaModelsRealation(toMetaModelsRelation(vsum.getMetaModelRelations()))
        .build();
  }

  /**
   * Maps domain {@link MetaModelRelation} entities to representation DTOs.
   *
   * <p>Extracts \`sourceId\`, \`targetId\`, and \`relationFileStorage\`.
   *
   * @param metaModelRelations the relations to transform; must not be \`null\`
   * @return a set of relation DTOs for the representation
   */
  default Set<VsumRepresentation.MetaModelRelation> toMetaModelsRelation(
      Set<MetaModelRelation> metaModelRelations) {
    return metaModelRelations.stream()
        .map(
            metaModelRelation ->
                VsumRepresentation.MetaModelRelation.builder()
                    .sourceId(metaModelRelation.getSource().getSource().getId())
                    .targetId(metaModelRelation.getTarget().getSource().getId())
                    .relationFileStorage(metaModelRelation.getReactionFileStorage().getId())
                    .build())
        .collect(Collectors.toSet());
  }

  /**
   * Converts a {@link VsumHistory} domain entity into a {@link VsumHistoryResponse} DTO.
   *
   * <p>This mapping exposes the fields required by API clients (for example {@code id} and {@code
   * createdAt}) and is intended to be used by MapStruct-generated implementations. Implementations
   * should handle a {@code null} source by returning {@code null}.
   *
   * @param vsumHistory the source history entity to map; may be {@code null}
   * @return a populated {@link VsumHistoryResponse} or {@code null} when {@code vsumHistory} is
   *     {@code null}
   */
  VsumHistoryResponse toVsumHistoryResponse(VsumHistory vsumHistory);
}
