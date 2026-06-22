package tools.vitruv.methodologist.vsum.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConstraintRuleSetService {

  ConstraintRuleSetRepository ruleSetRepository;
  VsumRepository vsumRepository;
  UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<RuleSetResponse> findAll(Long vsumId) {
    return ruleSetRepository.findByVsumId(vsumId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public RuleSetResponse create(String callerEmail, Long vsumId, RuleSetPostRequest request) {
    User user = resolveUser(callerEmail);
    Vsum vsum = resolveVsum(vsumId);

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

  @Transactional
  public RuleSetResponse update(
      String callerEmail, Long vsumId, Long ruleSetId, RuleSetPutRequest request) {
    User user = resolveUser(callerEmail);
    ConstraintRuleSet ruleSet =
        ruleSetRepository
            .findByIdAndVsumId(ruleSetId, vsumId)
            .orElseThrow(() -> new NotFoundException("RuleSet not found"));

    ruleSet.setName(request.name());
    if (request.color() != null) ruleSet.setColor(request.color());
    ruleSet.setDescription(request.description());

    String content = request.oclContent() != null ? request.oclContent() : "";
    FileStorage oclFile = buildFileStorage(request.name(), content, user);
    ruleSet.setOclFile(oclFile);

    return toResponse(ruleSetRepository.save(ruleSet));
  }

  @Transactional
  public void delete(Long vsumId, Long ruleSetId) {
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

  private Vsum resolveVsum(Long vsumId) {
    return vsumRepository.findById(vsumId).orElseThrow(() -> new NotFoundException("VSUM not found"));
  }
}
