package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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

@ExtendWith(MockitoExtension.class)
class VsumInvitationServiceTest {

  private static final String FRONTEND_BASE_URL = "http://localhost:3000";
  private static final String OWNER_EMAIL = "owner@example.com";
  private static final String INVITEE_EMAIL = "viewer@example.com";

  @Mock private VsumRepository vsumRepository;
  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumUserService vsumUserService;
  @Mock private VsumInvitationRepository vsumInvitationRepository;
  @Mock private UserRepository userRepository;
  @Mock private SmtpMailService mailService;

  private VsumInvitationService service;

  private Vsum vsum;
  private User owner;

  @BeforeEach
  void setUp() {
    service =
        new VsumInvitationService(
            vsumRepository,
            vsumUserRepository,
            vsumUserService,
            vsumInvitationRepository,
            userRepository,
            mailService,
            FRONTEND_BASE_URL);

    owner = new User();
    owner.setId(1L);
    owner.setEmail(OWNER_EMAIL);

    vsum = new Vsum();
    vsum.setId(42L);
    vsum.setName("My VSUM");
  }

  private VsumInvitationPostRequest request(String email) {
    return VsumInvitationPostRequest.builder().vsumId(vsum.getId()).email(email).build();
  }

  private VsumUser ownerMembership() {
    return VsumUser.builder().vsum(vsum).user(owner).role(VsumRole.OWNER).build();
  }

  @Test
  void invite_registeredUser_createsViewerMembership_marksAccepted_andSendsEmail() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(ownerMembership()));

    User invitee = new User();
    invitee.setId(2L);
    invitee.setEmail(INVITEE_EMAIL);
    invitee.setLastName("Doe");
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL))
        .thenReturn(Optional.of(invitee));
    when(vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, invitee))
        .thenReturn(false);

    service.invite(OWNER_EMAIL, request(INVITEE_EMAIL));

    verify(vsumUserService).create(vsum, invitee, VsumRole.VIEWER);

    ArgumentCaptor<VsumInvitation> captor = ArgumentCaptor.forClass(VsumInvitation.class);
    verify(vsumInvitationRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VsumInvitationStatus.ACCEPTED);
    assertThat(captor.getValue().getRole()).isEqualTo(VsumRole.VIEWER);
    assertThat(captor.getValue().getInviteeEmail()).isEqualTo(INVITEE_EMAIL);
    assertThat(captor.getValue().getAcceptedAt()).isNotNull();

    verify(mailService)
        .sendVsumInvitationMail(
            INVITEE_EMAIL, "Doe", "My VSUM", FRONTEND_BASE_URL + "/canvas/" + vsum.getId());
  }

  @Test
  void invite_unregisteredEmail_storesPendingInvitation_andSendsEmail() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(ownerMembership()));
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL))
        .thenReturn(Optional.empty());
    when(vsumInvitationRepository.existsByVsumAndInviteeEmailIgnoreCaseAndStatus(
            vsum, INVITEE_EMAIL, VsumInvitationStatus.PENDING))
        .thenReturn(false);

    service.invite(OWNER_EMAIL, request(INVITEE_EMAIL));

    verify(vsumUserService, never()).create(any(), any(), any());

    ArgumentCaptor<VsumInvitation> captor = ArgumentCaptor.forClass(VsumInvitation.class);
    verify(vsumInvitationRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VsumInvitationStatus.PENDING);
    assertThat(captor.getValue().getRole()).isEqualTo(VsumRole.VIEWER);
    assertThat(captor.getValue().getInviteeEmail()).isEqualTo(INVITEE_EMAIL);
    assertThat(captor.getValue().getAcceptedAt()).isNull();

    verify(mailService)
        .sendVsumInvitationMail(
            INVITEE_EMAIL, null, "My VSUM", FRONTEND_BASE_URL + "/canvas/" + vsum.getId());
  }

  @Test
  void invite_normalizesEmail_beforeLookupAndStorage() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(ownerMembership()));
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL))
        .thenReturn(Optional.empty());
    when(vsumInvitationRepository.existsByVsumAndInviteeEmailIgnoreCaseAndStatus(
            vsum, INVITEE_EMAIL, VsumInvitationStatus.PENDING))
        .thenReturn(false);

    service.invite(OWNER_EMAIL, request("  Viewer@Example.COM "));

    verify(userRepository).findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL);
    ArgumentCaptor<VsumInvitation> captor = ArgumentCaptor.forClass(VsumInvitation.class);
    verify(vsumInvitationRepository).save(captor.capture());
    assertThat(captor.getValue().getInviteeEmail()).isEqualTo(INVITEE_EMAIL);
  }

  @Test
  void invite_throwsNotFound_whenVsumMissing() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.invite(OWNER_EMAIL, request(INVITEE_EMAIL)))
        .isInstanceOf(NotFoundException.class);

    verifyNoInteractions(mailService);
  }

  @Test
  void invite_throwsAccessDenied_whenCallerNotMember() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.invite(OWNER_EMAIL, request(INVITEE_EMAIL)))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(mailService);
  }

  @Test
  void invite_throwsAccessDenied_whenCallerIsViewer() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    VsumUser viewerMembership =
        VsumUser.builder().vsum(vsum).user(owner).role(VsumRole.VIEWER).build();
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(viewerMembership));

    assertThatThrownBy(() -> service.invite(OWNER_EMAIL, request(INVITEE_EMAIL)))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(mailService);
  }

  @Test
  void invite_allowsMember_toInviteRegisteredUserAsViewer() {
    String memberEmail = "member@example.com";
    User member = new User();
    member.setId(3L);
    member.setEmail(memberEmail);

    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, memberEmail))
        .thenReturn(
            Optional.of(VsumUser.builder().vsum(vsum).user(member).role(VsumRole.MEMBER).build()));

    User invitee = new User();
    invitee.setId(2L);
    invitee.setEmail(INVITEE_EMAIL);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL))
        .thenReturn(Optional.of(invitee));
    when(vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, invitee))
        .thenReturn(false);

    service.invite(memberEmail, request(INVITEE_EMAIL));

    verify(vsumUserService).create(vsum, invitee, VsumRole.VIEWER);
    verify(mailService)
        .sendVsumInvitationMail(
            INVITEE_EMAIL, null, "My VSUM", FRONTEND_BASE_URL + "/canvas/" + vsum.getId());
  }

  @Test
  void invite_throwsSelfInvite_whenInvitingOwnEmail() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(ownerMembership()));

    assertThatThrownBy(() -> service.invite(OWNER_EMAIL, request("Owner@Example.com")))
        .isInstanceOf(OwnerCannotAddSelfAsMemberException.class);

    verifyNoInteractions(mailService);
  }

  @Test
  void invite_throwsDuplicateMembership_whenRegisteredUserAlreadyMember() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(ownerMembership()));

    User invitee = new User();
    invitee.setId(2L);
    invitee.setEmail(INVITEE_EMAIL);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL))
        .thenReturn(Optional.of(invitee));
    when(vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, invitee))
        .thenReturn(true);

    assertThatThrownBy(() -> service.invite(OWNER_EMAIL, request(INVITEE_EMAIL)))
        .isInstanceOf(DuplicateVsumMembershipException.class);

    verify(vsumUserService, never()).create(any(), any(), any());
    verifyNoInteractions(mailService);
  }

  @Test
  void invite_throwsInvitationAlreadyExists_whenPendingInvitationExists() {
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, OWNER_EMAIL))
        .thenReturn(Optional.of(ownerMembership()));
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(INVITEE_EMAIL))
        .thenReturn(Optional.empty());
    when(vsumInvitationRepository.existsByVsumAndInviteeEmailIgnoreCaseAndStatus(
            vsum, INVITEE_EMAIL, VsumInvitationStatus.PENDING))
        .thenReturn(true);

    assertThatThrownBy(() -> service.invite(OWNER_EMAIL, request(INVITEE_EMAIL)))
        .isInstanceOf(VsumInvitationAlreadyExistsException.class);

    verify(vsumInvitationRepository, never()).save(any());
    verifyNoInteractions(mailService);
  }

  @Test
  void applyPendingInvitations_createsMemberships_andMarksAccepted() {
    User user = new User();
    user.setId(2L);
    user.setEmail(INVITEE_EMAIL);

    VsumInvitation invitation =
        VsumInvitation.builder()
            .id(7L)
            .vsum(vsum)
            .inviteeEmail(INVITEE_EMAIL)
            .role(VsumRole.VIEWER)
            .status(VsumInvitationStatus.PENDING)
            .build();
    when(vsumInvitationRepository.findAllByInviteeEmailIgnoreCaseAndStatus(
            INVITEE_EMAIL, VsumInvitationStatus.PENDING))
        .thenReturn(List.of(invitation));
    when(vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, user))
        .thenReturn(false);

    service.applyPendingInvitations(user);

    verify(vsumUserService).create(vsum, user, VsumRole.VIEWER);
    ArgumentCaptor<VsumInvitation> captor = ArgumentCaptor.forClass(VsumInvitation.class);
    verify(vsumInvitationRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(VsumInvitationStatus.ACCEPTED);
    assertThat(captor.getValue().getAcceptedAt()).isNotNull();
  }

  @Test
  void applyPendingInvitations_skipsMembershipCreation_whenAlreadyMember_butMarksAccepted() {
    User user = new User();
    user.setId(2L);
    user.setEmail(INVITEE_EMAIL);

    VsumInvitation invitation =
        VsumInvitation.builder()
            .vsum(vsum)
            .inviteeEmail(INVITEE_EMAIL)
            .role(VsumRole.VIEWER)
            .status(VsumInvitationStatus.PENDING)
            .build();
    when(vsumInvitationRepository.findAllByInviteeEmailIgnoreCaseAndStatus(
            INVITEE_EMAIL, VsumInvitationStatus.PENDING))
        .thenReturn(List.of(invitation));
    when(vsumUserRepository.existsByVsumAndVsum_RemovedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, user))
        .thenReturn(true);

    service.applyPendingInvitations(user);

    verify(vsumUserService, never()).create(any(), any(), any());
    verify(vsumInvitationRepository).save(any(VsumInvitation.class));
  }

  @Test
  void applyPendingInvitations_skipsInvitation_whenVsumRemoved() {
    User user = new User();
    user.setId(2L);
    user.setEmail(INVITEE_EMAIL);

    Vsum removedVsum = new Vsum();
    removedVsum.setId(99L);
    removedVsum.setRemovedAt(Instant.now());

    VsumInvitation invitation =
        VsumInvitation.builder()
            .vsum(removedVsum)
            .inviteeEmail(INVITEE_EMAIL)
            .role(VsumRole.VIEWER)
            .status(VsumInvitationStatus.PENDING)
            .build();
    when(vsumInvitationRepository.findAllByInviteeEmailIgnoreCaseAndStatus(
            INVITEE_EMAIL, VsumInvitationStatus.PENDING))
        .thenReturn(List.of(invitation));

    service.applyPendingInvitations(user);

    verify(vsumUserService, never()).create(any(), any(), any());
    verify(vsumInvitationRepository, never()).save(any());
  }

  @Test
  void applyPendingInvitations_isNoop_whenUserOrEmailNull() {
    service.applyPendingInvitations(null);
    service.applyPendingInvitations(new User());

    verifyNoInteractions(vsumInvitationRepository);
    verifyNoInteractions(vsumUserService);
  }
}
