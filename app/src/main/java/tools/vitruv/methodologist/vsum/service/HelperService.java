package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.HelperValidationException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperParameterDto;
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperNameAvailabilityResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperProblemResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperTypeResponse;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.ClassInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.ClassifierInfo;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.EcoreFile;
import tools.vitruv.methodologist.vsum.helper.EcoreTypeIndex.EnumInfo;
import tools.vitruv.methodologist.vsum.helper.HelperClassGenerator;
import tools.vitruv.methodologist.vsum.helper.HelperClassGenerator.HelperError;
import tools.vitruv.methodologist.vsum.helper.HelperDefinition;
import tools.vitruv.methodologist.vsum.model.Helper;
import tools.vitruv.methodologist.vsum.model.HelperParameter;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.HelperRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Service for the helpers of a VSUM.
 *
 * <p>Helpers are stored as OCL and translated to Java on demand: every save validates that the
 * helper translates against the VSUM's metamodels, and every build generates the utility class from
 * the helpers that exist at that time. Deleting a helper therefore also removes its Java code.
 */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HelperService {

  HelperRepository helperRepository;
  VsumUserRepository vsumUserRepository;
  VsumMetaModelRepository vsumMetaModelRepository;

  /**
   * Returns all helpers of the given VSUM, each with its generated Java method or its problems.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return the helpers, ordered by name
   */
  @Transactional(readOnly = true)
  public List<HelperResponse> findAll(String callerEmail, Long vsumId) {
    findAccessibleVsum(callerEmail, vsumId);
    List<Helper> helpers = helperRepository.findByVsumIdOrderByNameAsc(vsumId);
    HelperClassGenerator.Result result = generate(definitions(helpers), metamodelIndex(vsumId));
    return helpers.stream().map(helper -> toResponse(helper, result)).toList();
  }

  /**
   * Creates a helper after checking that its name is free and that it translates to Java.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param request the helper
   * @return the created helper
   * @throws HelperValidationException if the name is taken or the helper cannot be translated
   */
  @Transactional
  public HelperResponse create(String callerEmail, Long vsumId, HelperRequest request) {
    Vsum vsum = findAccessibleVsum(callerEmail, vsumId);
    List<Helper> existing = helperRepository.findByVsumIdOrderByNameAsc(vsumId);
    HelperDefinition definition = toDefinition(request);
    HelperClassGenerator.Result result =
        validate(definition, null, existing, metamodelIndex(vsumId));

    Helper helper = Helper.builder().vsum(vsum).build();
    apply(helper, request);
    return toResponse(helperRepository.save(helper), result);
  }

  /**
   * Updates a helper after checking that its name is free, that it translates to Java and that the
   * helpers calling it still do.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param helperId the helper ID
   * @param request the new helper definition
   * @return the updated helper
   * @throws HelperValidationException if the update would break a helper
   */
  @Transactional
  public HelperResponse update(
      String callerEmail, Long vsumId, Long helperId, HelperRequest request) {
    findAccessibleVsum(callerEmail, vsumId);
    Helper helper = findHelper(helperId, vsumId);
    List<Helper> existing = helperRepository.findByVsumIdOrderByNameAsc(vsumId);
    HelperDefinition definition = toDefinition(request);
    HelperClassGenerator.Result result =
        validate(definition, helper, existing, metamodelIndex(vsumId));

    apply(helper, request);
    return toResponse(helperRepository.save(helper), result);
  }

  /**
   * Deletes a helper, which removes its method from the generated Java class.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param helperId the helper ID
   * @throws HelperValidationException if other helpers still call the helper
   */
  @Transactional
  public void delete(String callerEmail, Long vsumId, Long helperId) {
    findAccessibleVsum(callerEmail, vsumId);
    Helper helper = findHelper(helperId, vsumId);
    List<Helper> existing = helperRepository.findByVsumIdOrderByNameAsc(vsumId);
    EcoreTypeIndex index = metamodelIndex(vsumId);
    List<HelperDefinition> before = definitions(existing);
    List<HelperDefinition> after =
        definitions(existing.stream().filter(h -> !Objects.equals(h.getId(), helperId)).toList());
    Set<String> broken = newlyBrokenHelpers(generate(before, index), generate(after, index));
    if (!broken.isEmpty()) {
      throw new HelperValidationException(
          "The utility method '"
              + helper.getName()
              + "' cannot be deleted because it is used by: "
              + String.join(", ", broken)
              + ".");
    }
    helperRepository.delete(helper);
  }

  /**
   * Translates an unsaved helper without storing it, for live feedback in the editor.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param helperId the ID of the helper being edited, or {@code null} for a new helper
   * @param request the helper
   * @return the OCL definition, the Java method and the problems found
   */
  @Transactional(readOnly = true)
  public HelperPreviewResponse preview(
      String callerEmail, Long vsumId, Long helperId, HelperRequest request) {
    findAccessibleVsum(callerEmail, vsumId);
    List<Helper> existing = helperRepository.findByVsumIdOrderByNameAsc(vsumId);
    HelperDefinition definition = toDefinition(request);
    List<HelperProblemResponse> problems = new ArrayList<>();
    String nameProblem = nameProblem(request.name(), helperId, existing);
    if (nameProblem != null) {
      problems.add(new HelperProblemResponse(request.name(), nameProblem, null, null));
    }
    HelperClassGenerator.Result result =
        generate(withCandidate(definition, helperId, existing), metamodelIndex(vsumId));
    result.errorsOf(definition.name()).stream().map(this::toProblem).forEach(problems::add);
    return new HelperPreviewResponse(
        definition.oclDefinition(),
        problems.isEmpty() ? result.methods().get(definition.name()) : null,
        problems);
  }

  /**
   * Checks whether a name can be used for a helper.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param name the name
   * @param helperId the ID of the helper being renamed, or {@code null} for a new helper
   * @return whether the name is available, and why not
   */
  @Transactional(readOnly = true)
  public HelperNameAvailabilityResponse checkName(
      String callerEmail, Long vsumId, String name, Long helperId) {
    findAccessibleVsum(callerEmail, vsumId);
    String problem =
        nameProblem(name, helperId, helperRepository.findByVsumIdOrderByNameAsc(vsumId));
    return new HelperNameAvailabilityResponse(problem == null, problem);
  }

  /**
   * Returns the Java utility class generated from all helpers of the VSUM.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return the class source and the problems of helpers that are left out
   */
  @Transactional(readOnly = true)
  public HelperClassResponse generatedClass(String callerEmail, Long vsumId) {
    findAccessibleVsum(callerEmail, vsumId);
    HelperClassGenerator.Result result =
        generate(
            definitions(helperRepository.findByVsumIdOrderByNameAsc(vsumId)),
            metamodelIndex(vsumId));
    return new HelperClassResponse(
        HelperClassGenerator.QUALIFIED_CLASS_NAME,
        HelperClassGenerator.FILE_NAME,
        result.source(),
        result.errors().stream().map(this::toProblem).toList());
  }

  /**
   * Returns the metamodel types helpers of the VSUM can use, for type pickers and imports.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return the classes, enumerations and data types of the VSUM's metamodels
   */
  @Transactional(readOnly = true)
  public List<HelperTypeResponse> availableTypes(String callerEmail, Long vsumId) {
    findAccessibleVsum(callerEmail, vsumId);
    return metamodelIndex(vsumId).allClassifiers().stream().map(this::toTypeResponse).toList();
  }

  /**
   * Generates the utility class for a VSUM build, using the metamodels the build is made from.
   *
   * @param vsum the VSUM; must be called inside a transaction
   * @param ecores the metamodel files of the build
   * @return the Java file to compile with the reactions, or empty if the VSUM has no helpers
   * @throws VsumBuildingException if a helper cannot be translated
   */
  @Transactional(readOnly = true)
  public Optional<FileStorage> generateBuildFile(Vsum vsum, Collection<FileStorage> ecores) {
    List<Helper> helpers = helperRepository.findByVsumIdOrderByNameAsc(vsum.getId());
    if (helpers.isEmpty()) {
      return Optional.empty();
    }
    HelperClassGenerator.Result result =
        generate(definitions(helpers), EcoreTypeIndex.of(ecoreFiles(ecores)));
    if (result.hasErrors()) {
      throw new VsumBuildingException(
          "The utility methods cannot be translated to Java: "
              + result.errors().stream()
                  .map(error -> error.helperName() + ": " + error.message())
                  .collect(Collectors.joining("; ")));
    }
    byte[] source = result.source().getBytes(StandardCharsets.UTF_8);
    // Not persisted: the file is sent along with the reactions, whose source folder the
    // consistency module compiles as Java.
    return Optional.of(
        FileStorage.builder()
            .filename(HelperClassGenerator.FILE_NAME)
            .type(FileEnumType.REACTION)
            .contentType("text/x-java-source")
            .sizeBytes(source.length)
            .data(source)
            .build());
  }

  /**
   * Generates the utility class for the reactions editor, using the metamodels the editor is given.
   * Unlike for a build, helpers that cannot be translated are left out instead of failing.
   *
   * @param vsumId the VSUM ID
   * @param ecores the metamodel files of the editor's workspace
   * @return the source of the class, or empty if the VSUM has no helpers
   */
  @Transactional(readOnly = true)
  public Optional<String> generateEditorSource(Long vsumId, Collection<FileStorage> ecores) {
    List<Helper> helpers = helperRepository.findByVsumIdOrderByNameAsc(vsumId);
    if (helpers.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        generate(definitions(helpers), EcoreTypeIndex.of(ecoreFiles(ecores))).source());
  }

  // ── validation ───────────────────────────────────────────────────────────

  /**
   * Checks the candidate's name and translation and that no other helper breaks because of it.
   *
   * @return the generation result including the candidate
   */
  private HelperClassGenerator.Result validate(
      HelperDefinition candidate, Helper replaced, List<Helper> existing, EcoreTypeIndex index) {
    Long replacedId = replaced == null ? null : replaced.getId();
    String nameProblem = nameProblem(candidate.name(), replacedId, existing);
    if (nameProblem != null) {
      throw new HelperValidationException(nameProblem);
    }
    HelperClassGenerator.Result after =
        generate(withCandidate(candidate, replacedId, existing), index);
    List<HelperError> errors = after.errorsOf(candidate.name());
    if (!errors.isEmpty()) {
      throw new HelperValidationException(describe(errors.get(0)));
    }
    Set<String> broken = newlyBrokenHelpers(generate(definitions(existing), index), after);
    if (!broken.isEmpty()) {
      throw new HelperValidationException(
          "Saving would break the utility methods that call '"
              + (replaced == null ? candidate.name() : replaced.getName())
              + "': "
              + String.join(", ", broken)
              + ".");
    }
    return after;
  }

  private String nameProblem(String name, Long helperId, List<Helper> existing) {
    String problem = HelperClassGenerator.nameProblem(name);
    if (problem != null) {
      return problem;
    }
    String normalized = name.toLowerCase(Locale.ROOT);
    boolean taken =
        existing.stream()
            .anyMatch(
                helper ->
                    !Objects.equals(helper.getId(), helperId)
                        && helper.getName().toLowerCase(Locale.ROOT).equals(normalized));
    return taken ? "A utility method named '" + name + "' already exists in this project." : null;
  }

  /** Helpers that translate in {@code before} but not in {@code after}. */
  private static Set<String> newlyBrokenHelpers(
      HelperClassGenerator.Result before, HelperClassGenerator.Result after) {
    Set<String> brokenBefore =
        before.errors().stream().map(HelperError::helperName).collect(Collectors.toSet());
    return after.errors().stream()
        .map(HelperError::helperName)
        .filter(name -> !brokenBefore.contains(name) && before.methods().containsKey(name))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String describe(HelperError error) {
    if (error.line() == null) {
      return error.message();
    }
    return "Line " + error.line() + ", column " + error.column() + ": " + error.message();
  }

  // ── generation ───────────────────────────────────────────────────────────

  private static HelperClassGenerator.Result generate(
      List<HelperDefinition> definitions, EcoreTypeIndex index) {
    return HelperClassGenerator.generate(definitions, index);
  }

  /** The existing helpers with {@code candidate} added or replacing the helper being edited. */
  private List<HelperDefinition> withCandidate(
      HelperDefinition candidate, Long replacedId, List<Helper> existing) {
    List<HelperDefinition> definitions =
        new ArrayList<>(
            definitions(
                existing.stream()
                    .filter(h -> !Objects.equals(h.getId(), replacedId))
                    .filter(h -> !h.getName().equalsIgnoreCase(candidate.name()))
                    .toList()));
    definitions.add(candidate);
    return definitions;
  }

  private EcoreTypeIndex metamodelIndex(Long vsumId) {
    List<FileStorage> ecores =
        vsumMetaModelRepository.findByVsumId(vsumId).stream()
            .filter(link -> link.getRemovedAt() == null)
            .map(VsumMetaModel::getMetaModel)
            .filter(Objects::nonNull)
            .map(MetaModel::getEcoreFile)
            .filter(Objects::nonNull)
            .toList();
    return EcoreTypeIndex.of(ecoreFiles(ecores));
  }

  private static List<EcoreFile> ecoreFiles(Collection<FileStorage> ecores) {
    return ecores.stream()
        .filter(file -> file.getData() != null)
        .map(file -> new EcoreFile(file.getFilename(), file.getData()))
        .toList();
  }

  // ── mapping ──────────────────────────────────────────────────────────────

  private static List<HelperDefinition> definitions(List<Helper> helpers) {
    return helpers.stream().map(HelperService::toDefinition).toList();
  }

  private static HelperDefinition toDefinition(Helper helper) {
    return new HelperDefinition(
        helper.getName(),
        helper.getDescription(),
        helper.getParameters().stream()
            .map(p -> new HelperDefinition.Parameter(p.getName(), p.getType()))
            .toList(),
        helper.getReturnType(),
        helper.getImports(),
        helper.getBody());
  }

  private static HelperDefinition toDefinition(HelperRequest request) {
    List<HelperParameterDto> parameters =
        request.parameters() == null ? List.of() : request.parameters();
    return new HelperDefinition(
        request.name() == null ? "" : request.name().trim(),
        request.description(),
        parameters.stream()
            .map(p -> new HelperDefinition.Parameter(trim(p.name()), trim(p.type())))
            .toList(),
        trim(request.returnType()),
        cleanImports(request.imports()),
        request.body());
  }

  private static void apply(Helper helper, HelperRequest request) {
    HelperDefinition definition = toDefinition(request);
    helper.setName(definition.name());
    helper.setDescription(
        request.description() == null || request.description().isBlank()
            ? null
            : request.description().strip());
    helper.setReturnType(definition.returnType());
    helper.setBody(definition.body().strip());
    helper.setImports(new ArrayList<>(definition.imports()));
    helper.getParameters().clear();
    definition
        .parameters()
        .forEach(p -> helper.getParameters().add(new HelperParameter(p.name(), p.type())));
  }

  private static List<String> cleanImports(List<String> imports) {
    if (imports == null) {
      return List.of();
    }
    return imports.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .distinct()
        .toList();
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private HelperResponse toResponse(Helper helper, HelperClassGenerator.Result result) {
    HelperDefinition definition = toDefinition(helper);
    return new HelperResponse(
        helper.getId(),
        helper.getVsum().getId(),
        helper.getName(),
        helper.getDescription(),
        helper.getReturnType(),
        helper.getParameters().stream()
            .map(p -> new HelperParameterDto(p.getName(), p.getType()))
            .toList(),
        List.copyOf(helper.getImports()),
        helper.getBody(),
        definition.oclDefinition(),
        result.methods().get(helper.getName()),
        result.errorsOf(helper.getName()).stream().map(this::toProblem).toList(),
        helper.getCreatedAt(),
        helper.getUpdatedAt());
  }

  private HelperProblemResponse toProblem(HelperError error) {
    return new HelperProblemResponse(
        error.helperName(), error.message(), error.line(), error.column());
  }

  private HelperTypeResponse toTypeResponse(ClassifierInfo classifier) {
    String kind =
        switch (classifier) {
          case ClassInfo ignored -> "CLASS";
          case EnumInfo ignored -> "ENUM";
          default -> "DATATYPE";
        };
    return new HelperTypeResponse(
        String.join("::", classifier.pkg().path()),
        classifier.name(),
        classifier.qualifiedName(),
        kind,
        classifier.javaQualifiedName());
  }

  // ── access ───────────────────────────────────────────────────────────────

  private Vsum findAccessibleVsum(String callerEmail, Long vsumId) {
    return vsumUserRepository
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
            vsumId, callerEmail)
        .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS))
        .getVsum();
  }

  private Helper findHelper(Long helperId, Long vsumId) {
    return helperRepository
        .findByIdAndVsumId(helperId, vsumId)
        .orElseThrow(() -> new NotFoundException("Utility method not found"));
  }
}
