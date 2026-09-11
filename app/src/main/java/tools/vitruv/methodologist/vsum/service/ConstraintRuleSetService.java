package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.RuleSetResponse;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

/** Service for managing OCL constraint rule sets associated with VSUMs. */
@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConstraintRuleSetService {

  ConstraintRuleSetRepository ruleSetRepository;
  VsumRepository vsumRepository;
  UserRepository userRepository;

  /**
   * Returns all rule sets for the given VSUM.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @return list of rule set responses
   */
  @Transactional(readOnly = true)
  public List<RuleSetResponse> findAll(String callerEmail, Long vsumId) {
    User user = resolveUser(callerEmail);
    resolveAccessibleVsum(user, vsumId);
    return ruleSetRepository.findByVsumId(vsumId).stream().map(this::toResponse).toList();
  }

  /**
   * Creates a new rule set for the given VSUM.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param request the creation request
   * @return the created rule set response
   */
  @Transactional
  public RuleSetResponse create(String callerEmail, Long vsumId, RuleSetPostRequest request) {
    User user = resolveUser(callerEmail);
    Vsum vsum = resolveAccessibleVsum(user, vsumId);

    String content = request.oclContent() != null ? request.oclContent() : "";
    FileStorage oclFile = buildFileStorage(request.name(), content, user);

    ConstraintRuleSet ruleSet =
        ConstraintRuleSet.builder()
            .vsum(vsum)
            .name(request.name())
            .color(request.color() != null ? request.color() : "#3b82f6")
            .description(request.description())
            .oclFile(oclFile)
            .build();

    return toResponse(ruleSetRepository.save(ruleSet));
  }

  /**
   * Updates an existing rule set.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param ruleSetId the rule set ID
   * @param request the update request
   * @return the updated rule set response
   */
  @Transactional
  public RuleSetResponse update(
      String callerEmail, Long vsumId, Long ruleSetId, RuleSetPutRequest request) {
    final User user = resolveUser(callerEmail);
    resolveAccessibleVsum(user, vsumId);
    ConstraintRuleSet ruleSet =
        ruleSetRepository
            .findByIdAndVsumId(ruleSetId, vsumId)
            .orElseThrow(() -> new NotFoundException("RuleSet not found"));

    ruleSet.setName(request.name());
    if (request.color() != null) {
      ruleSet.setColor(request.color());
    }
    ruleSet.setDescription(request.description());

    String content = request.oclContent() != null ? request.oclContent() : "";
    FileStorage oclFile = buildFileStorage(request.name(), content, user);
    ruleSet.setOclFile(oclFile);

    return toResponse(ruleSetRepository.save(ruleSet));
  }

  /**
   * Deletes a rule set by ID.
   *
   * @param callerEmail email of the authenticated user
   * @param vsumId the VSUM ID
   * @param ruleSetId the rule set ID to delete
   */
  @Transactional
  public void delete(String callerEmail, Long vsumId, Long ruleSetId) {
    User user = resolveUser(callerEmail);
    resolveAccessibleVsum(user, vsumId);
    ConstraintRuleSet ruleSet =
        ruleSetRepository
            .findByIdAndVsumId(ruleSetId, vsumId)
            .orElseThrow(() -> new NotFoundException("RuleSet not found"));
    ruleSetRepository.delete(ruleSet);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private RuleSetResponse toResponse(ConstraintRuleSet rs) {
    String content = "";
    if (rs.getOclFile() != null && rs.getOclFile().getData() != null) {
      content = new String(rs.getOclFile().getData(), StandardCharsets.UTF_8);
    }
    return new RuleSetResponse(
        rs.getId(),
        rs.getVsum().getId(),
        rs.getName(),
        rs.getColor(),
        rs.getDescription(),
        content,
        rs.getCreatedAt(),
        rs.getUpdatedAt());
  }

  private FileStorage buildFileStorage(String ruleSetName, String content, User user) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    String filename = toSafeFilename(ruleSetName) + ".ocl";
    return FileStorage.builder()
        .filename(filename)
        .type(FileEnumType.OCL)
        .contentType("text/plain")
        .sizeBytes(bytes.length)
        .sha256(sha256(bytes))
        .data(bytes)
        .user(user)
        .build();
  }

  private String toSafeFilename(String name) {
    return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
  }

  private String sha256(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private User resolveUser(String email) {
    return userRepository
        .findByEmailIgnoreCaseAndRemovedAtIsNull(email)
        .orElseThrow(UnauthorizedException::new);
  }

  private Vsum resolveAccessibleVsum(User user, Long vsumId) {
    Vsum vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(vsumId)
            .orElseThrow(() -> new NotFoundException("VSUM not found"));
    if (vsum.getVsumUsers() == null
        || vsum.getVsumUsers().stream().noneMatch(vsumUser -> belongsToUser(vsumUser, user))) {
      throw new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS);
    }
    return vsum;
  }

  private boolean belongsToUser(VsumUser vsumUser, User user) {
    User candidate = vsumUser.getUser();
    if (candidate == null) {
      return false;
    }
    if (user.getId() != null && candidate.getId() != null) {
      return user.getId().equals(candidate.getId());
    }
    return user.getEmail() != null && user.getEmail().equalsIgnoreCase(candidate.getEmail());
  }
}
