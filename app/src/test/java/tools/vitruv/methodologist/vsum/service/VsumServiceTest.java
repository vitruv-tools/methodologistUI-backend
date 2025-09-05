package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

@ExtendWith(MockitoExtension.class)
class VsumServiceTest {

  @Mock private VsumMapper vsumMapper;
  @Mock private VsumRepository vsumRepository;
  @Mock private MetaModelMapper metaModelMapper;
  @Mock private VsumMetaModelService vsumMetaModelService;
  @Mock private UserRepository userRepository;

  private VsumService service;

  @BeforeEach
  void setUp() {
    service =
        new VsumService(
            vsumMapper, vsumRepository, metaModelMapper, vsumMetaModelService, userRepository);
  }

  @Test
  void create_savesWithOwner_whenUserExists() {
    String email = "u@ex.com";
    User user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    VsumPostRequest req = new VsumPostRequest();
    Vsum mapped = new Vsum();
    when(vsumMapper.toVsum(req)).thenReturn(mapped);

    Vsum saved = new Vsum();
    saved.setId(10L);
    when(vsumRepository.save(mapped)).thenReturn(saved);

    Vsum result = service.create(email, req);

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(mapped.getUser()).isSameAs(user);
    verify(vsumRepository).save(mapped);
  }

  @Test
  void create_throwsUnauthorized_whenUserMissing() {
    String email = "none@ex.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    VsumPostRequest req = new VsumPostRequest();

    assertThatThrownBy(() -> service.create(email, req)).isInstanceOf(UnauthorizedException.class);

    verify(vsumRepository, never()).save(any(Vsum.class));
  }

  @Test
  void update_mergesAndSyncs_whenOwnedByCaller() {
    String email = "u@ex.com";
    VsumPutRequest put = new VsumPutRequest();
    put.setMetaModelIds(List.of(1L, 2L));

    Vsum existing = new Vsum();
    existing.setId(5L);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(5L, email))
        .thenReturn(Optional.of(existing));

    Vsum saved = new Vsum();
    saved.setId(5L);
    when(vsumRepository.save(existing)).thenReturn(saved);

    Vsum result = service.update(email, 5L, put);

    assertThat(result.getId()).isEqualTo(5L);
    verify(vsumMapper).updateByVsumPutRequest(put, existing);
    verify(vsumMetaModelService).sync(existing, put.getMetaModelIds());
    verify(vsumRepository).save(existing);
  }

  @Test
  void update_throwsNotFound_whenNotOwnedOrMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(7L, email))
        .thenReturn(Optional.empty());

    VsumPutRequest put = new VsumPutRequest();

    assertThatThrownBy(() -> service.update(email, 7L, put))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void findById_returnsMapped_whenOwned() {
    String email = "u@ex.com";
    Vsum entity = new Vsum();
    entity.setId(9L);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(9L, email))
        .thenReturn(Optional.of(entity));

    VsumResponse dto = new VsumResponse();
    dto.setId(9L);
    when(vsumMapper.toVsumResponse(entity)).thenReturn(dto);

    VsumResponse result = service.findById(email, 9L);

    assertThat(result.getId()).isEqualTo(9L);
  }

  @Test
  void findById_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(9L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(email, 9L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void remove_setsRemovedAtAndSaves_whenOwned() {
    String email = "u@ex.com";
    Vsum entity = new Vsum();
    entity.setId(3L);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(3L, email))
        .thenReturn(Optional.of(entity));

    ArgumentCaptor<Vsum> captor = ArgumentCaptor.forClass(Vsum.class);
    when(vsumRepository.save(any(Vsum.class))).thenAnswer(inv -> inv.getArgument(0));

    Vsum result = service.remove(email, 3L);

    assertThat(result.getRemovedAt()).isNotNull();
    assertThat(result.getRemovedAt()).isBeforeOrEqualTo(Instant.now());
    verify(vsumRepository).save(captor.capture());
    assertThat(captor.getValue().getRemovedAt()).isNotNull();
  }

  @Test
  void remove_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(3L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove(email, 3L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void findVsumWithDetails_handlesNullChildList() {
    String email = "u@ex.com";
    Vsum vsum = new Vsum();
    vsum.setId(77L);
    vsum.setVsumMetaModels(null);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(77L, email))
        .thenReturn(Optional.of(vsum));

    VsumMetaModelResponse base = new VsumMetaModelResponse();
    when(vsumMapper.toVsumMetaModelResponse(vsum)).thenReturn(base);

    VsumMetaModelResponse result = service.findVsumWithDetails(email, 77L);

    assertThat(result.getMetaModels()).isNotNull();
    assertThat(result.getMetaModels()).isEmpty();
    verifyNoInteractions(metaModelMapper);
  }

  @Test
  void findVsumWithDetails_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(1L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findVsumWithDetails(email, 1L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void findAllByUser_mapsList() {
    String email = "u@ex.com";
    Vsum a = new Vsum();
    a.setId(1L);
    Vsum b = new Vsum();
    b.setId(2L);
    when(vsumRepository.findAllByUser_emailAndUser_removedAtIsNull(email))
        .thenReturn(List.of(a, b));

    VsumResponse ra = new VsumResponse();
    ra.setId(1L);
    VsumResponse rb = new VsumResponse();
    rb.setId(2L);
    when(vsumMapper.toVsumResponse(a)).thenReturn(ra);
    when(vsumMapper.toVsumResponse(b)).thenReturn(rb);

    List<VsumResponse> result = service.findAllByUser(email);

    assertThat(result).extracting(VsumResponse::getId).containsExactly(1L, 2L);
  }
}
