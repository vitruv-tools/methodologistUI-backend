package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.OwnerCannotAddSelfAsMemberException;
import tools.vitruv.methodologist.exception.OwnerRequiredException;
import tools.vitruv.methodologist.exception.OwnerRoleRemovalException;
import tools.vitruv.methodologist.exception.UserAlreadyExistInVsumWithSameRoleException;
import tools.vitruv.methodologist.exception.VsumUserAlreadyMemberException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumUserPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumUserResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumUserMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

@ExtendWith(MockitoExtension.class)
class VsumUserServiceTest {

  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private UserRepository userRepository;
  @Mock private VsumUserMapper vsumUserMapper;
  @Mock private VsumRepository vsumRepository;

  @InjectMocks private VsumUserService service;

  @Captor private ArgumentCaptor<VsumUser> vsumUserCaptor;

  private Vsum vsum;
  private User user;

  @BeforeEach
  void setUp() {
    vsum = Vsum.builder().id(1L).name("Demo VSUM").build();

    user = new User();
    user.setId(10L);
    user.setFirstName("Alice");
    user.setLastName("Smith");
  }

  @Test
  void create_shouldPersist_whenNotExists() {
    VsumRole role = VsumRole.MEMBER;
    when(vsumUserRepository.existsByVsumAndUserAndRole(vsum, user, role)).thenReturn(false);

    VsumUser created = service.create(vsum, user, role);

    verify(vsumUserRepository).save(vsumUserCaptor.capture());
    VsumUser saved = vsumUserCaptor.getValue();

    assertThat(saved.getVsum()).isEqualTo(vsum);
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getRole()).isEqualTo(role);

    assertThat(created.getVsum()).isEqualTo(vsum);
    assertThat(created.getUser()).isEqualTo(user);
    assertThat(created.getRole()).isEqualTo(role);
  }

  @Test
  void create_shouldThrow_whenExistsWithSameRole() {
    VsumRole role = VsumRole.MEMBER;
    when(vsumUserRepository.existsByVsumAndUserAndRole(vsum, user, role)).thenReturn(true);

    assertThatThrownBy(() -> service.create(vsum, user, role))
        .isInstanceOf(UserAlreadyExistInVsumWithSameRoleException.class)
        .hasMessageContaining("Alice Smith")
        .hasMessageContaining("Demo VSUM")
        .hasMessageContaining(role.getName());

    verify(vsumUserRepository, never()).save(any(VsumUser.class));
  }

  @Test
  void findAllMemberByVsum_returnsMembers_whenCallerIsOwner() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(100L);
    owner.setEmail(caller);
    owner.setFirstName("owner");
    owner.setLastName("dummy");

    User member = new User();
    member.setId(200L);
    member.setEmail("mem@x.test");
    member.setFirstName("mem");
    member.setLastName("dummy");

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(caller))
        .thenReturn(Optional.of(owner));

    VsumUser memberMembership =
        VsumUser.builder().id(2L).vsum(vsum).user(member).role(VsumRole.MEMBER).build();

    VsumUser ownerMembership =
        VsumUser.builder().id(1L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();

    when(vsumUserRepository.findAllByVsum_id(vsum.getId()))
        .thenReturn(List.of(ownerMembership, memberMembership));
    // mapper returns something simple; you can stub exact fields if needed
    when(vsumUserMapper.toVsumUserResponse(any()))
        .thenAnswer(
            inv -> {
              VsumUser u = inv.getArgument(0);
              return VsumUserResponse.builder()
                  .id(u.getId())
                  .vsumId(u.getVsum().getId())
                  .firstName(u.getUser().getFirstName())
                  .lastName(u.getUser().getLastName())
                  .email(u.getUser().getEmail())
                  .roleEn(u.getRole().getName())
                  .build();
            });

    var out = service.findAllMemberByVsum(caller, vsum.getId());

    assertThat(out).hasSize(2);
  }

  @Test
  void findAllMemberByVsum_throwsOwnerRequired_whenCallerNotOwner() {
    String caller = "member@x.test";
    User mem = new User();
    mem.setId(200L);
    mem.setEmail(caller);

    VsumUser ownerMembership =
        VsumUser.builder().id(1L).vsum(vsum).user(new User()).role(VsumRole.OWNER).build();
    VsumUser memberMembership =
        VsumUser.builder().id(2L).vsum(vsum).user(mem).role(VsumRole.MEMBER).build();

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(caller))
        .thenReturn(Optional.of(mem));
    when(vsumUserRepository.findAllByVsum_id(vsum.getId()))
        .thenReturn(List.of(ownerMembership, memberMembership));

    Long vsumId = vsum.getId();

    assertThatThrownBy(() -> service.findAllMemberByVsum(caller, vsumId))
        .isInstanceOf(OwnerRequiredException.class);
  }

  @Test
  void findAllMemberByVsum_throwsNotFound_whenCallerEmailMissing() {
    String caller = "ghost@x.test";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(caller))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findAllMemberByVsum(caller, vsum.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void addMember_createsMember_whenCallerIsOwner_andCandidateNotMember() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(100L);
    owner.setEmail(caller);

    User candidate = new User();
    candidate.setId(10L);
    candidate.setEmail("c@x.test");

    VsumUser callerMembership =
        VsumUser.builder().id(1L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));
    when(userRepository.findByIdAndRemovedAtIsNull(candidate.getId()))
        .thenReturn(Optional.of(candidate));
    when(vsumUserRepository.existsByVsumAndVsum_removedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, candidate))
        .thenReturn(false);
    when(vsumUserRepository.existsByVsumAndUserAndRole(vsum, candidate, VsumRole.MEMBER))
        .thenReturn(false);

    VsumUserPostRequest req =
        VsumUserPostRequest.builder().vsumId(vsum.getId()).userId(candidate.getId()).build();
    VsumUser created = service.addMember(caller, req);

    verify(vsumUserRepository).save(vsumUserCaptor.capture());
    assertThat(created.getVsum()).isEqualTo(vsum);
    assertThat(created.getUser()).isEqualTo(candidate);
    assertThat(created.getRole()).isEqualTo(VsumRole.MEMBER);
  }

  @Test
  void addMember_throwsNotFound_whenVsumMissing() {
    String caller = "owner@x.test";

    when(vsumRepository.findByIdAndRemovedAtIsNull(999L)).thenReturn(Optional.empty());
    VsumUserPostRequest req = VsumUserPostRequest.builder().vsumId(999L).userId(10L).build();

    assertThatThrownBy(() -> service.addMember(caller, req)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void addMember_throwsOwnerRequired_whenCallerNotMemberOrOwner() {
    String caller = "stranger@x.test";

    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.empty());
    VsumUserPostRequest req =
        VsumUserPostRequest.builder().vsumId(vsum.getId()).userId(10L).build();

    assertThatThrownBy(() -> service.addMember(caller, req))
        .isInstanceOf(OwnerRequiredException.class);
  }

  @Test
  void addMember_throwsOwnerRequired_whenCallerIsMemberNotOwner() {
    String caller = "member@x.test";
    User mem = new User();
    mem.setId(2L);
    mem.setEmail(caller);

    VsumUser callerMembership =
        VsumUser.builder().id(1L).vsum(vsum).user(mem).role(VsumRole.MEMBER).build();
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));

    VsumUserPostRequest req =
        VsumUserPostRequest.builder().vsumId(vsum.getId()).userId(10L).build();
    assertThatThrownBy(() -> service.addMember(caller, req))
        .isInstanceOf(OwnerRequiredException.class);
  }

  @Test
  void addMember_throwsNotFound_whenCandidateMissing() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(100L);
    owner.setEmail(caller);

    VsumUser callerMembership =
        VsumUser.builder().id(1L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();

    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));
    when(userRepository.findByIdAndRemovedAtIsNull(10L)).thenReturn(Optional.empty());
    VsumUserPostRequest req =
        VsumUserPostRequest.builder().vsumId(vsum.getId()).userId(10L).build();

    assertThatThrownBy(() -> service.addMember(caller, req)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void addMember_throwsSelfAdd_whenOwnerTriesToAddThemselves() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(100L);
    owner.setEmail(caller);

    VsumUser callerMembership =
        VsumUser.builder().id(1L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();
    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));
    when(userRepository.findByIdAndRemovedAtIsNull(100L)).thenReturn(Optional.of(owner));
    VsumUserPostRequest req =
        VsumUserPostRequest.builder().vsumId(vsum.getId()).userId(100L).build();

    assertThatThrownBy(() -> service.addMember(caller, req))
        .isInstanceOf(OwnerCannotAddSelfAsMemberException.class);
  }

  @Test
  void addMember_throwsAlreadyMember_whenCandidateAlreadyMemberAnyRole() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(1L);
    owner.setEmail(caller);
    User candidate = new User();
    candidate.setId(10L);

    VsumUser callerMembership =
        VsumUser.builder().id(99L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();

    when(vsumRepository.findByIdAndRemovedAtIsNull(vsum.getId())).thenReturn(Optional.of(vsum));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));
    when(userRepository.findByIdAndRemovedAtIsNull(candidate.getId()))
        .thenReturn(Optional.of(candidate));
    when(vsumUserRepository.existsByVsumAndVsum_removedAtIsNullAndUserAndUser_RemovedAtIsNull(
            vsum, candidate))
        .thenReturn(true);
    VsumUserPostRequest req =
        VsumUserPostRequest.builder().vsumId(vsum.getId()).userId(candidate.getId()).build();

    assertThatThrownBy(() -> service.addMember(caller, req))
        .isInstanceOf(VsumUserAlreadyMemberException.class);
    verify(vsumUserRepository, never()).save(any(VsumUser.class));
  }

  @Test
  void deleteMember_deletes_whenCallerIsOwner_andTargetIsMember() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(1L);
    owner.setEmail(caller);
    User member = new User();
    member.setId(10L);

    VsumUser target =
        VsumUser.builder().id(123L).vsum(vsum).user(member).role(VsumRole.MEMBER).build();
    VsumUser callerMembership =
        VsumUser.builder().id(99L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();

    when(vsumUserRepository.findById(123L)).thenReturn(Optional.of(target));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));

    service.deleteMember(caller, 123L);

    verify(vsumUserRepository, times(1)).delete(target);
  }

  @Test
  void deleteMember_throwsNotFound_whenTargetMissing() {
    when(vsumUserRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteMember("owner@x.test", 999L))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deleteMember_throwsOwnerRequired_whenCallerNotOwner() {
    String caller = "member@x.test";
    User mem = new User();
    mem.setId(2L);
    mem.setEmail(caller);

    VsumUser target =
        VsumUser.builder().id(123L).vsum(vsum).user(new User()).role(VsumRole.MEMBER).build();
    VsumUser callerMembership =
        VsumUser.builder().id(77L).vsum(vsum).user(mem).role(VsumRole.MEMBER).build();

    when(vsumUserRepository.findById(123L)).thenReturn(Optional.of(target));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));

    assertThatThrownBy(() -> service.deleteMember(caller, 123L))
        .isInstanceOf(OwnerRequiredException.class);
    verify(vsumUserRepository, never()).delete(any(VsumUser.class));
  }

  @Test
  void deleteMember_throwsOwnerRoleRemoval_whenTargetIsOwner() {
    String caller = "owner@x.test";
    User owner = new User();
    owner.setId(1L);
    owner.setEmail(caller);

    VsumUser targetOwner =
        VsumUser.builder().id(123L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();
    VsumUser callerMembership =
        VsumUser.builder().id(99L).vsum(vsum).user(owner).role(VsumRole.OWNER).build();

    when(vsumUserRepository.findById(123L)).thenReturn(Optional.of(targetOwner));
    when(vsumUserRepository.findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, caller))
        .thenReturn(Optional.of(callerMembership));

    assertThatThrownBy(() -> service.deleteMember(caller, 123L))
        .isInstanceOf(OwnerRoleRemovalException.class);
    verify(vsumUserRepository, never()).delete(any(VsumUser.class));
  }
}
