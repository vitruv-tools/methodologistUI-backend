package tools.vitruv.methodologist.general.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.mapper.FileStorageMapper;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

  @Mock private FileStorageRepository fileStorageRepository;

  @Mock private UserRepository userRepository;

  @Mock private FileStorageMapper fileStorageMapper;

  @InjectMocks private FileStorageService fileStorageService;

  private User testUser;
  private MockMultipartFile testFile;
  private FileStorage testFileStorage;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(1L);
    testUser.setEmail("test@example.com");

    byte[] fileContent = "Hello, World!".getBytes();
    testFile = new MockMultipartFile("file", "test.txt", "text/plain", fileContent);

    testFileStorage = new FileStorage();
    testFileStorage.setId(1L);
    testFileStorage.setFilename("test.txt");
    testFileStorage.setContentType("text/plain");
    testFileStorage.setUser(testUser);
    testFileStorage.setData(fileContent);
    testFileStorage.setSizeBytes(fileContent.length);
    testFileStorage.setType(FileEnumType.GEN_MODEL);
  }

  @Test
  void storeFile_newFile_success() throws Exception {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.of(testUser));
    //    when(fileStorageRepository.existsByUserAndSha256AndSizeBytes(any(), any(), anyLong()))
    //        .thenReturn(false);
    when(fileStorageRepository.save(any(FileStorage.class))).thenReturn(testFileStorage);

    FileStorageResponse response =
        fileStorageService.storeFile("test@example.com", testFile, FileEnumType.GEN_MODEL);

    assertNotNull(response);
    verify(fileStorageRepository)
        .save(
            argThat(
                file ->
                    file.getFilename().equals("test.txt")
                        && file.getContentType().equals("text/plain")
                        && file.getType().equals(FileEnumType.GEN_MODEL)));
  }

  @Test
  void storeFile_userNotFound_throwsNotFoundException() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> fileStorageService.storeFile("test@example.com", testFile, FileEnumType.GEN_MODEL));
  }

  @Test
  void storeFile_emptyFile_throwsIllegalArgumentException() {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.of(testUser));

    assertThrows(
        IllegalArgumentException.class,
        () -> fileStorageService.storeFile("test@example.com", emptyFile, FileEnumType.GEN_MODEL));
  }

  @Test
  void getFile_existingFile_returnsFile() {
    when(fileStorageRepository.findById(1L)).thenReturn(Optional.of(testFileStorage));

    FileStorage result = fileStorageService.getFile(1L);

    assertNotNull(result);
    assertEquals(testFileStorage.getId(), result.getId());
  }

  @Test
  void getFile_nonExistingFile_throwsException() {
    when(fileStorageRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> fileStorageService.getFile(1L));
  }

  @Test
  void clone_success() {
    FileStorage clonedStorage = new FileStorage();
    clonedStorage.setId(2L);
    clonedStorage.setData(testFileStorage.getData());
    clonedStorage.setFilename(testFileStorage.getFilename());
    clonedStorage.setContentType(testFileStorage.getContentType());
    clonedStorage.setType(testFileStorage.getType());

    when(fileStorageMapper.clone(testFileStorage)).thenReturn(clonedStorage);
    when(fileStorageRepository.save(any(FileStorage.class))).thenReturn(clonedStorage);

    FileStorage result = fileStorageService.clone(testFileStorage);

    assertNotNull(result);
    assertEquals(2L, result.getId());
    assertEquals(testFileStorage.getFilename(), result.getFilename());
    assertEquals(testFileStorage.getContentType(), result.getContentType());
    verify(fileStorageRepository).save(clonedStorage);
  }

  @Test
  void deleteFiles_withNullList_handlesGracefully() {
    fileStorageService.deleteFiles(List.of(FileStorage.builder().build()));
    verify(fileStorageRepository).deleteAll(anyList());
  }

  @Test
  void deleteFiles_success() {
    FileStorage file1 = new FileStorage();
    FileStorage file2 = new FileStorage();

    fileStorageService.deleteFiles(Arrays.asList(file1, file2));

    verify(fileStorageRepository).deleteAll(Arrays.asList(file1, file2));
  }

  @Test
  void updateFile_validRequest_updatesAndReturnsId() throws Exception {
    String email = "test@example.com";
    Long fileId = 10L;

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(testUser));

    FileStorage existing = new FileStorage();
    existing.setId(fileId);
    existing.setUser(testUser);
    existing.setType(FileEnumType.REACTION);

    when(fileStorageRepository.findByIdAndType(fileId, FileEnumType.REACTION))
        .thenReturn(Optional.of(existing));

    MockMultipartFile newFile =
        new MockMultipartFile("file", "new.txt", "text/plain", "new-content".getBytes());

    when(fileStorageRepository.save(any(FileStorage.class))).thenAnswer(inv -> inv.getArgument(0));

    FileStorageResponse response = fileStorageService.updateFile(email, fileId, newFile);

    assertNotNull(response);
    assertEquals(fileId, response.getId());

    verify(fileStorageRepository)
        .save(
            argThat(
                fs ->
                    fs.getId().equals(fileId)
                        && "new.txt".equals(fs.getFilename())
                        && "text/plain".equals(fs.getContentType())
                        && fs.getType() == FileEnumType.REACTION
                        && Arrays.equals(fs.getData(), "new-content".getBytes())));
  }

  @Test
  void updateFile_userNotFound_throwsNotFoundException() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> fileStorageService.updateFile("missing@example.com", 1L, testFile));
  }

  @Test
  void updateFile_fileNotFound_throwsNotFoundException() {
    String email = "test@example.com";

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(testUser));
    when(fileStorageRepository.findByIdAndType(1L, FileEnumType.REACTION))
        .thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> fileStorageService.updateFile(email, 1L, testFile));
  }

  @Test
  void updateFile_fileBelongsToAnotherUser_throwsIllegalArgumentException() {
    String email = "test@example.com";

    User otherUser = new User();
    otherUser.setId(99L);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(testUser));

    FileStorage existing = new FileStorage();
    existing.setId(1L);
    existing.setUser(otherUser);
    existing.setType(FileEnumType.REACTION);

    when(fileStorageRepository.findByIdAndType(1L, FileEnumType.REACTION))
        .thenReturn(Optional.of(existing));

    assertThrows(
        IllegalArgumentException.class, () -> fileStorageService.updateFile(email, 1L, testFile));
  }

  @Test
  void updateFile_emptyFile_throwsIllegalArgumentException() {
    String email = "test@example.com";

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(testUser));

    FileStorage existing = new FileStorage();
    existing.setId(1L);
    existing.setUser(testUser);
    existing.setType(FileEnumType.REACTION);

    when(fileStorageRepository.findByIdAndType(1L, FileEnumType.REACTION))
        .thenReturn(Optional.of(existing));

    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    assertThrows(
        IllegalArgumentException.class, () -> fileStorageService.updateFile(email, 1L, emptyFile));
  }
}
