package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOES_NOT_HAVE_ACCESS;

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
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UtilityMethodValidationException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodParameterDto;
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodNameAvailabilityResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodProblemResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodTypeResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.UtilityMethod;
import tools.vitruv.methodologist.vsum.model.UtilityMethodParameter;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.UtilityMethodRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.ClassInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.ClassifierInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.EcoreFile;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex.EnumInfo;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.UtilityMethodError;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodCompilationException;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodCompiler;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodDefinition;

/**
 * Service for the utility methods of a VSUM.
 *
 * <p>Utility methods are stored with an Xtend body and compiled to Java on demand: every save
 * validates that the utility method compiles against the VSUM's metamodels, and every build
 * generates the utility class from the utility methods that exist at that time. Deleting a utility
 * method therefore also removes its Java code.
 */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UtilityMethodService {

  UtilityMethodRepository utilityMethodRepository;
  VsumUserRepository vsumUserRepository;
  VsumMetaModelRepository vsumMetaModelRepository;
  UtilityMethodCompiler utilityMethodCompiler;

  /**
   * Returns all utility methods of the given VSUM, each with its generated Java method or its
   * problems.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return the utility methods, ordered by name
   */
  @Transactional(readOnly = true)
  public List<UtilityMethodResponse> findAll(String callerEmail, Long vsumId) {
    findAccessibleVsum(callerEmail, vsumId);
    List<UtilityMethod> utilityMethods = utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId);
    UtilityMethodClassGenerator.Result result =
        compile(definitions(utilityMethods), projectEcores(vsumId));
    return utilityMethods.stream().map(utilityMethod -> toResponse(utilityMethod, result)).toList();
  }

  /**
   * Creates a utility method after checking that its name is free and that it compiles to Java.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param request the utility method
   * @return the created utility method
   * @throws UtilityMethodValidationException if the name is taken or the utility method cannot be
   *     compiled
   */
  @Transactional
  public UtilityMethodResponse create(
      String callerEmail, Long vsumId, UtilityMethodRequest request) {
    Vsum vsum = findAccessibleVsum(callerEmail, vsumId);
    List<UtilityMethod> existing = utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId);
    UtilityMethodDefinition definition = toDefinition(request);
    UtilityMethodClassGenerator.Result result =
        validate(definition, null, existing, projectEcores(vsumId));

    UtilityMethod utilityMethod = UtilityMethod.builder().vsum(vsum).build();
    apply(utilityMethod, request);
    return toResponse(utilityMethodRepository.save(utilityMethod), result);
  }

  /**
   * Updates a utility method after checking that its name is free, that it compiles to Java and
   * that the utility methods calling it still do.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param utilityMethodId the utility method ID
   * @param request the new utility method definition
   * @return the updated utility method
   * @throws UtilityMethodValidationException if the update would break a utility method
   */
  @Transactional
  public UtilityMethodResponse update(
      String callerEmail, Long vsumId, Long utilityMethodId, UtilityMethodRequest request) {
    findAccessibleVsum(callerEmail, vsumId);
    UtilityMethod utilityMethod = findUtilityMethod(utilityMethodId, vsumId);
    List<UtilityMethod> existing = utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId);
    UtilityMethodDefinition definition = toDefinition(request);
    UtilityMethodClassGenerator.Result result =
        validate(definition, utilityMethod, existing, projectEcores(vsumId));

    apply(utilityMethod, request);
    return toResponse(utilityMethodRepository.save(utilityMethod), result);
  }

  /**
   * Deletes a utility method, which removes its method from the generated Java class.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param utilityMethodId the utility method ID
   * @throws UtilityMethodValidationException if other utility methods still call the utility method
   */
  @Transactional
  public void delete(String callerEmail, Long vsumId, Long utilityMethodId) {
    findAccessibleVsum(callerEmail, vsumId);
    UtilityMethod utilityMethod = findUtilityMethod(utilityMethodId, vsumId);
    List<UtilityMethod> existing = utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId);
    List<FileStorage> ecores = projectEcores(vsumId);
    List<UtilityMethodDefinition> before = definitions(existing);
    List<UtilityMethodDefinition> after =
        definitions(
            existing.stream().filter(h -> !Objects.equals(h.getId(), utilityMethodId)).toList());
    Set<String> broken = newlyBrokenUtilityMethods(compile(before, ecores), compile(after, ecores));
    if (!broken.isEmpty()) {
      throw new UtilityMethodValidationException(
          "The utility method '"
              + utilityMethod.getName()
              + "' cannot be deleted because it is used by: "
              + String.join(", ", broken)
              + ".");
    }
    utilityMethodRepository.delete(utilityMethod);
  }

  /**
   * Compiles an unsaved utility method without storing it, for live feedback in the editor.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param utilityMethodId the ID of the utility method being edited, or {@code null} for a new
   *     utility method
   * @param request the utility method
   * @return the Xtend definition, the Java method and the problems found
   */
  @Transactional(readOnly = true)
  public UtilityMethodPreviewResponse preview(
      String callerEmail, Long vsumId, Long utilityMethodId, UtilityMethodRequest request) {
    findAccessibleVsum(callerEmail, vsumId);
    List<UtilityMethod> existing = utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId);
    UtilityMethodDefinition definition = toDefinition(request);
    List<UtilityMethodProblemResponse> problems = new ArrayList<>();
    String nameProblem = nameProblem(request.name(), utilityMethodId, existing);
    if (nameProblem != null) {
      problems.add(
          new UtilityMethodProblemResponse(
              request.name(), nameProblem, null, null, UtilityMethodClassGenerator.ERROR));
    }
    String javaMethod = null;
    try {
      UtilityMethodClassGenerator.Result result =
          compile(withCandidate(definition, utilityMethodId, existing), projectEcores(vsumId));
      result.errorsOf(definition.name()).stream().map(this::toProblem).forEach(problems::add);
      javaMethod = result.methods().get(definition.name());
    } catch (UtilityMethodValidationException e) {
      problems.add(
          new UtilityMethodProblemResponse(
              definition.name(), e.getMessage(), null, null, UtilityMethodClassGenerator.ERROR));
    }
    boolean hasErrors =
        problems.stream()
            .anyMatch(problem -> UtilityMethodClassGenerator.ERROR.equals(problem.severity()));
    return new UtilityMethodPreviewResponse(
        UtilityMethodClassGenerator.xtendDefinition(definition),
        hasErrors ? null : javaMethod,
        problems);
  }

  /**
   * Checks whether a name can be used for a utility method.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param name the name
   * @param utilityMethodId the ID of the utility method being renamed, or {@code null} for a new
   *     utility method
   * @return whether the name is available, and why not
   */
  @Transactional(readOnly = true)
  public UtilityMethodNameAvailabilityResponse checkName(
      String callerEmail, Long vsumId, String name, Long utilityMethodId) {
    findAccessibleVsum(callerEmail, vsumId);
    String problem =
        nameProblem(
            name, utilityMethodId, utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId));
    return new UtilityMethodNameAvailabilityResponse(problem == null, problem);
  }

  /**
   * Returns the Java utility class generated from all utility methods of the VSUM.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return the class source and the problems of utility methods that are left out
   */
  @Transactional(readOnly = true)
  public UtilityMethodClassResponse generatedClass(String callerEmail, Long vsumId) {
    findAccessibleVsum(callerEmail, vsumId);
    UtilityMethodClassGenerator.Result result =
        compile(
            definitions(utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId)),
            projectEcores(vsumId));
    return new UtilityMethodClassResponse(
        UtilityMethodClassGenerator.QUALIFIED_CLASS_NAME,
        UtilityMethodClassGenerator.FILE_NAME,
        result.source(),
        result.errors().stream().map(this::toProblem).toList());
  }

  /**
   * Returns the metamodel types utility methods of the VSUM can use, for type pickers and imports.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return the classes, enumerations and data types of the VSUM's metamodels
   */
  @Transactional(readOnly = true)
  public List<UtilityMethodTypeResponse> availableTypes(String callerEmail, Long vsumId) {
    findAccessibleVsum(callerEmail, vsumId);
    return EcoreTypeIndex.of(ecoreFiles(projectEcores(vsumId))).allClassifiers().stream()
        .map(this::toTypeResponse)
        .toList();
  }

  /**
   * Generates the utility class for a VSUM build, using the metamodels the build is made from.
   *
   * @param vsum the VSUM; must be called inside a transaction
   * @param ecores the metamodel files of the build
   * @return the Java file to compile with the reactions, or empty if the VSUM has no utility
   *     methods
   * @throws VsumBuildingException if a utility method does not compile
   */
  @Transactional(readOnly = true)
  public Optional<FileStorage> generateBuildFile(Vsum vsum, Collection<FileStorage> ecores) {
    List<UtilityMethod> utilityMethods =
        utilityMethodRepository.findByVsumIdOrderByNameAsc(vsum.getId());
    if (utilityMethods.isEmpty()) {
      return Optional.empty();
    }
    UtilityMethodClassGenerator.Result result;
    try {
      result = compile(definitions(utilityMethods), ecores);
    } catch (UtilityMethodValidationException e) {
      throw new VsumBuildingException(e.getMessage());
    }
    if (result.hasErrors()) {
      throw new VsumBuildingException(
          "The utility methods do not compile: "
              + result.errors().stream()
                  .filter(UtilityMethodError::isError)
                  .map(error -> error.utilityMethodName() + ": " + describe(error))
                  .collect(Collectors.joining("; ")));
    }
    byte[] source = result.source().getBytes(StandardCharsets.UTF_8);
    // Not persisted: the file is sent along with the reactions, whose source folder the
    // consistency module compiles as Java.
    return Optional.of(
        FileStorage.builder()
            .filename(UtilityMethodClassGenerator.FILE_NAME)
            .type(FileEnumType.REACTION)
            .contentType("text/x-java-source")
            .sizeBytes(source.length)
            .data(source)
            .build());
  }

  /**
   * Generates the utility class for the reactions editor, using the metamodels the editor is given.
   * Unlike for a build, utility methods that do not compile are left out instead of failing.
   *
   * @param vsumId the VSUM ID
   * @param ecores the metamodel files of the editor's workspace
   * @return the source of the class, or empty if the VSUM has no utility methods
   */
  @Transactional(readOnly = true)
  public Optional<String> generateEditorSource(Long vsumId, Collection<FileStorage> ecores) {
    List<UtilityMethod> utilityMethods = utilityMethodRepository.findByVsumIdOrderByNameAsc(vsumId);
    if (utilityMethods.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(compile(definitions(utilityMethods), ecores).source());
    } catch (UtilityMethodValidationException e) {
      return Optional.empty();
    }
  }

  // ── validation ───────────────────────────────────────────────────────────

  /**
   * Checks the candidate's name and compilation and that no other utility method breaks because of
   * it.
   *
   * @return the generation result including the candidate
   */
  private UtilityMethodClassGenerator.Result validate(
      UtilityMethodDefinition candidate,
      UtilityMethod replaced,
      List<UtilityMethod> existing,
      List<FileStorage> ecores) {
    Long replacedId = replaced == null ? null : replaced.getId();
    String nameProblem = nameProblem(candidate.name(), replacedId, existing);
    if (nameProblem != null) {
      throw new UtilityMethodValidationException(nameProblem);
    }
    UtilityMethodClassGenerator.Result after =
        compile(withCandidate(candidate, replacedId, existing), ecores);
    List<UtilityMethodError> errors =
        after.errorsOf(candidate.name()).stream().filter(UtilityMethodError::isError).toList();
    if (!errors.isEmpty()) {
      throw new UtilityMethodValidationException(describe(errors.get(0)));
    }
    Set<String> broken = newlyBrokenUtilityMethods(compile(definitions(existing), ecores), after);
    if (!broken.isEmpty()) {
      throw new UtilityMethodValidationException(
          "Saving would break the utility methods that call '"
              + (replaced == null ? candidate.name() : replaced.getName())
              + "': "
              + String.join(", ", broken)
              + ".");
    }
    return after;
  }

  private String nameProblem(String name, Long utilityMethodId, List<UtilityMethod> existing) {
    String problem = UtilityMethodClassGenerator.nameProblem(name);
    if (problem != null) {
      return problem;
    }
    String normalized = name.toLowerCase(Locale.ROOT);
    boolean taken =
        existing.stream()
            .anyMatch(
                utilityMethod ->
                    !Objects.equals(utilityMethod.getId(), utilityMethodId)
                        && utilityMethod.getName().toLowerCase(Locale.ROOT).equals(normalized));
    return taken ? "A utility method named '" + name + "' already exists in this project." : null;
  }

  /** Utility methods that compile in {@code before} but not in {@code after}. */
  private static Set<String> newlyBrokenUtilityMethods(
      UtilityMethodClassGenerator.Result before, UtilityMethodClassGenerator.Result after) {
    Set<String> brokenBefore = before.failedUtilityMethods();
    return after.failedUtilityMethods().stream()
        .filter(name -> !brokenBefore.contains(name) && before.methods().containsKey(name))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String describe(UtilityMethodError error) {
    if (error.line() == null) {
      return error.message();
    }
    return "Line " + error.line() + ", column " + error.column() + ": " + error.message();
  }

  // ── generation ───────────────────────────────────────────────────────────

  /**
   * Compiles the utility methods.
   *
   * @throws UtilityMethodValidationException if compiling fails for a reason other than a problem
   *     in the utility methods, for example a timeout
   */
  private UtilityMethodClassGenerator.Result compile(
      List<UtilityMethodDefinition> definitions, Collection<FileStorage> ecores) {
    try {
      return utilityMethodCompiler.compile(definitions, ecores);
    } catch (UtilityMethodCompilationException e) {
      throw new UtilityMethodValidationException(e.getMessage());
    }
  }

  /**
   * The existing utility methods with {@code candidate} added or replacing the utility method being
   * edited.
   */
  private List<UtilityMethodDefinition> withCandidate(
      UtilityMethodDefinition candidate, Long replacedId, List<UtilityMethod> existing) {
    List<UtilityMethodDefinition> definitions =
        new ArrayList<>(
            definitions(
                existing.stream()
                    .filter(h -> !Objects.equals(h.getId(), replacedId))
                    .filter(h -> !h.getName().equalsIgnoreCase(candidate.name()))
                    .toList()));
    definitions.add(candidate);
    return definitions;
  }

  private List<FileStorage> projectEcores(Long vsumId) {
    return vsumMetaModelRepository.findByVsumId(vsumId).stream()
        .filter(link -> link.getRemovedAt() == null)
        .map(VsumMetaModel::getMetaModel)
        .filter(Objects::nonNull)
        .map(MetaModel::getEcoreFile)
        .filter(Objects::nonNull)
        .toList();
  }

  private static List<EcoreFile> ecoreFiles(Collection<FileStorage> ecores) {
    return ecores.stream()
        .filter(file -> file.getData() != null)
        .map(file -> new EcoreFile(file.getFilename(), file.getData()))
        .toList();
  }

  // ── mapping ──────────────────────────────────────────────────────────────

  private static List<UtilityMethodDefinition> definitions(List<UtilityMethod> utilityMethods) {
    return utilityMethods.stream().map(UtilityMethodService::toDefinition).toList();
  }

  private static UtilityMethodDefinition toDefinition(UtilityMethod utilityMethod) {
    return new UtilityMethodDefinition(
        utilityMethod.getName(),
        utilityMethod.getDescription(),
        utilityMethod.getParameters().stream()
            .map(p -> new UtilityMethodDefinition.Parameter(p.getName(), p.getType()))
            .toList(),
        utilityMethod.getReturnType(),
        utilityMethod.getImports(),
        utilityMethod.getBody());
  }

  private static UtilityMethodDefinition toDefinition(UtilityMethodRequest request) {
    List<UtilityMethodParameterDto> parameters =
        request.parameters() == null ? List.of() : request.parameters();
    return new UtilityMethodDefinition(
        request.name() == null ? "" : request.name().trim(),
        request.description(),
        parameters.stream()
            .map(p -> new UtilityMethodDefinition.Parameter(trim(p.name()), trim(p.type())))
            .toList(),
        trim(request.returnType()),
        cleanImports(request.imports()),
        request.body());
  }

  private static void apply(UtilityMethod utilityMethod, UtilityMethodRequest request) {
    UtilityMethodDefinition definition = toDefinition(request);
    utilityMethod.setName(definition.name());
    utilityMethod.setDescription(
        request.description() == null || request.description().isBlank()
            ? null
            : request.description().strip());
    utilityMethod.setReturnType(definition.returnType());
    utilityMethod.setBody(definition.body().strip());
    utilityMethod.setImports(new ArrayList<>(definition.imports()));
    utilityMethod.getParameters().clear();
    definition
        .parameters()
        .forEach(
            p -> utilityMethod.getParameters().add(new UtilityMethodParameter(p.name(), p.type())));
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

  private UtilityMethodResponse toResponse(
      UtilityMethod utilityMethod, UtilityMethodClassGenerator.Result result) {
    UtilityMethodDefinition definition = toDefinition(utilityMethod);
    return new UtilityMethodResponse(
        utilityMethod.getId(),
        utilityMethod.getVsum().getId(),
        utilityMethod.getName(),
        utilityMethod.getDescription(),
        utilityMethod.getReturnType(),
        utilityMethod.getParameters().stream()
            .map(p -> new UtilityMethodParameterDto(p.getName(), p.getType()))
            .toList(),
        List.copyOf(utilityMethod.getImports()),
        utilityMethod.getBody(),
        UtilityMethodClassGenerator.xtendDefinition(definition),
        result.methods().get(utilityMethod.getName()),
        result.errorsOf(utilityMethod.getName()).stream().map(this::toProblem).toList(),
        utilityMethod.getCreatedAt(),
        utilityMethod.getUpdatedAt());
  }

  private UtilityMethodProblemResponse toProblem(UtilityMethodError error) {
    return new UtilityMethodProblemResponse(
        error.utilityMethodName(), error.message(), error.line(), error.column(), error.severity());
  }

  private UtilityMethodTypeResponse toTypeResponse(ClassifierInfo classifier) {
    return new UtilityMethodTypeResponse(
        String.join("::", classifier.pkg().path()),
        classifier.name(),
        classifier.qualifiedName(),
        kindOf(classifier),
        classifier.javaQualifiedName());
  }

  private static String kindOf(ClassifierInfo classifier) {
    return switch (classifier) {
      case ClassInfo ignored -> "CLASS";
      case EnumInfo ignored -> "ENUM";
      default -> "DATATYPE";
    };
  }

  // ── access ───────────────────────────────────────────────────────────────

  private Vsum findAccessibleVsum(String callerEmail, Long vsumId) {
    return vsumUserRepository
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
            vsumId, callerEmail)
        .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS))
        .getVsum();
  }

  private UtilityMethod findUtilityMethod(Long utilityMethodId, Long vsumId) {
    return utilityMethodRepository
        .findByIdAndVsumId(utilityMethodId, vsumId)
        .orElseThrow(() -> new NotFoundException("Utility method not found"));
  }
}
