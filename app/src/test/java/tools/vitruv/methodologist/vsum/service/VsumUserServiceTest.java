package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.UserAlreadyExistInVsumWithSameRoleException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

@ExtendWith(MockitoExtension.class)
class VsumUserServiceTest {

  @Mock private VsumUserRepository vsumUserRepository;

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
}
