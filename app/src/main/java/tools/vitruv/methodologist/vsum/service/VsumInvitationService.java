package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.DuplicateVsumMembershipException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.OwnerCannotAddSelfAsMemberException;
import tools.vitruv.methodologist.exception.VsumInvitationAlreadyExistsException;
import tools.vitruv.methodologist.general.service.SmtpMailService;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumInvitationStatus;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumInvitationPostRequest;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumInvitation;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumInvitationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Service that handles inviting users (by email) to a VSUM as read-only viewers.
 *
 * <p>Only the VSUM owner or a member may invite (viewers may not). If the invited email already
 * belongs to a registered user, a {@link tools.vitruv.methodologist.vsum.model.VsumUser} membership
 * with the {@link VsumRole#VIEWER} role is created immediately. Otherwise a {@link VsumInvitation}
 * is stored with status {@link VsumInvitationStatus#PENDING} and applied automatically once that
 * user registers (see {@link #applyPendingInvitations(User)}). In both cases an invitation email
 * with a link to the VSUM is sent.
 */
@Service
@Slf4j
public class VsumInvitationService {

  private final VsumRepository vsumRepository;
  private final VsumUserRepository vsumUserRepository;
  private final VsumUserService vsumUserService;
  private final VsumInvitationRepository vsumInvitationRepository;
  private final UserRepository userRepository;
  private final SmtpMailService mailService;
  private final String frontendBaseUrl;

  /**
   * Constructs a new VsumInvitationService.
   *
   * @param vsumRepository repository for VSUM lookups
   * @param vsumUserRepository repository for VSUM membership lookups
   * @param vsumUserService service used to create VSUM memberships
   * @param vsumInvitationRepository repository for invitation persistence
   * @param userRepository repository for resolving invitees by email
   * @param mailService service used to send invitation emails
   * @param frontendBaseUrl base URL of the frontend used to build the invitation link
   */
  public VsumInvitationService(
      VsumRepository vsumRepository,
      VsumUserRepository vsumUserRepository,
      VsumUserService vsumUserService,
      VsumInvitationRepository vsumInvitationRepository,
      UserRepository userRepository,
      SmtpMailService mailService,
      @Value("${app.frontend.base-url}") String frontendBaseUrl) {
    this.vsumRepository = vsumRepository;
    this.vsumUserRepository = vsumUserRepository;
    this.vsumUserService = vsumUserService;
    this.vsumInvitationRepository = vsumInvitationRepository;
    this.userRepository = userRepository;
    this.mailService = mailService;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  /**
   * Invites an email address to a VSUM as a read-only viewer.
   *
   * <p>Only the owner or a member of the VSUM may invite (viewers may not). Registered invitees
   * receive viewer access immediately; unregistered invitees get a pending invitation that is
   * applied when they register. An invitation email is always sent.
   *
   * @param callerEmail the authenticated caller's email; must be an owner or member of the VSUM
   * @param request the invitation request (VSUM id and invitee email)
   * @throws NotFoundException if the VSUM does not exist
   * @throws org.springframework.security.access.AccessDeniedException if the caller is not an owner
   *     or member of the VSUM
   * @throws OwnerCannotAddSelfAsMemberException if the caller invites their own email
   * @throws DuplicateVsumMembershipException if the invitee is already a member
   * @throws VsumInvitationAlreadyExistsException if a pending invitation already exists
   */
  @Transactional
  public void invite(String callerEmail, VsumInvitationPostRequest request) {
    String inviteeEmail = normalizeEmail(request.getEmail());

    Vsum vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(request.getVsumId())
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    VsumUser callerMembership =
        vsumUserRepository
            .findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));
    if (callerMembership.getRole() == VsumRole.VIEWER) {
      throw new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS);
    }

    if (inviteeEmail.equalsIgnoreCase(normalizeEmail(callerEmail))) {
      throw new OwnerCannotAddSelfAsMemberException();
    }

    Optional<User> invitee = userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(inviteeEmail);
    if (invitee.isPresent()) {
      inviteRegisteredUser(vsum, callerMembership.getUser(), invitee.get());
    } else {
      inviteUnregisteredEmail(vsum, callerMembership.getUser(), inviteeEmail);
    }

    mailService.sendVsumInvitationMail(
        inviteeEmail, invitee.map(User::getLastName).orElse(null), vsum.getName(), buildLink(vsum));
  }

  private void inviteRegisteredUser(Vsum vsum, User inviter, User invitee) {
    if (vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
        vsum, invitee)) {
      throw new DuplicateVsumMembershipException();
    }

    vsumUserService.create(vsum, invitee, VsumRole.VIEWER);

    VsumInvitation invitation = newInvitation(vsum, inviter, invitee.getEmail());
    invitation.setStatus(VsumInvitationStatus.ACCEPTED);
    invitation.setAcceptedAt(Instant.now());
    vsumInvitationRepository.save(invitation);
  }

  private void inviteUnregisteredEmail(Vsum vsum, User inviter, String inviteeEmail) {
    if (vsumInvitationRepository.existsByVsumAndInviteeEmailIgnoreCaseAndStatus(
        vsum, inviteeEmail, VsumInvitationStatus.PENDING)) {
      throw new VsumInvitationAlreadyExistsException();
    }

    VsumInvitation invitation = newInvitation(vsum, inviter, inviteeEmail);
    invitation.setStatus(VsumInvitationStatus.PENDING);
    vsumInvitationRepository.save(invitation);
  }

  /**
   * Applies all pending invitations for the given (newly registered) user.
   *
   * <p>For each pending invitation matching the user's email, a viewer membership is created
   * (unless one already exists) and the invitation is marked {@link VsumInvitationStatus#ACCEPTED}.
   * This is idempotent and safe to call multiple times.
   *
   * @param user the user whose pending invitations should be applied
   */
  @Transactional
  public void applyPendingInvitations(User user) {
    if (user == null || user.getEmail() == null) {
      return;
    }

    List<VsumInvitation> pending =
        vsumInvitationRepository.findAllByInviteeEmailIgnoreCaseAndStatus(
            user.getEmail(), VsumInvitationStatus.PENDING);

    for (VsumInvitation invitation : pending) {
      Vsum vsum = invitation.getVsum();
      if (vsum == null || vsum.getRemovedAt() != null) {
        continue;
      }

      if (!vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
          vsum, user)) {
        vsumUserService.create(vsum, user, invitation.getRole());
      }

      invitation.setStatus(VsumInvitationStatus.ACCEPTED);
      invitation.setAcceptedAt(Instant.now());
      vsumInvitationRepository.save(invitation);
    }
  }

  private VsumInvitation newInvitation(Vsum vsum, User inviter, String inviteeEmail) {
    return VsumInvitation.builder()
        .vsum(vsum)
        .invitedBy(inviter)
        .inviteeEmail(inviteeEmail)
        .role(VsumRole.VIEWER)
        .build();
  }

  private String buildLink(Vsum vsum) {
    String base =
        frontendBaseUrl.endsWith("/")
            ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
            : frontendBaseUrl;
    return base + "/vsums/" + vsum.getId();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}
