package tools.vitruv.methodologist.general.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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
  void storeFile_NewFile_Success() throws Exception {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.of(testUser));
    when(fileStorageRepository.findBySha256AndSizeBytes(any(), anyLong()))
        .thenReturn(Optional.empty());
    when(fileStorageRepository.save(any(FileStorage.class))).thenReturn(testFileStorage);

    FileStorageResponse response =
        fileStorageService.storeFile("test@example.com", testFile, FileEnumType.GEN_MODEL);

    assertNotNull(response);
    assertEquals(1L, response.getId());
    verify(fileStorageRepository)
        .save(
            argThat(
                file ->
                    file.getFilename().equals("test.txt")
                        && file.getContentType().equals("text/plain")
                        && file.getType().equals(FileEnumType.GEN_MODEL)));
  }

  @Test
  void storeFile_ExistingFile_ReturnsExisting() throws Exception {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.of(testUser));
    when(fileStorageRepository.findBySha256AndSizeBytes(any(), anyLong()))
        .thenReturn(Optional.of(testFileStorage));

    FileStorageResponse response =
        fileStorageService.storeFile("test@example.com", testFile, FileEnumType.GEN_MODEL);

    assertNotNull(response);
    assertEquals(1L, response.getId());
    verify(fileStorageRepository, never()).save(any(FileStorage.class));
  }

  @Test
  void storeFile_UserNotFound_ThrowsNotFoundException() {
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () -> fileStorageService.storeFile("test@example.com", testFile, FileEnumType.GEN_MODEL));
  }

  @Test
  void storeFile_EmptyFile_ThrowsIllegalArgumentException() {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(anyString()))
        .thenReturn(Optional.of(testUser));

    assertThrows(
        IllegalArgumentException.class,
        () -> fileStorageService.storeFile("test@example.com", emptyFile, FileEnumType.GEN_MODEL));
  }

  @Test
  void getFile_ExistingFile_ReturnsFile() {
    when(fileStorageRepository.findById(1L)).thenReturn(Optional.of(testFileStorage));

    FileStorage result = fileStorageService.getFile(1L);

    assertNotNull(result);
    assertEquals(testFileStorage.getId(), result.getId());
  }

  @Test
  void getFile_NonExistingFile_ThrowsException() {
    when(fileStorageRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> fileStorageService.getFile(1L));
  }

  @Test
  void deleteFile_Success() {
    fileStorageService.deleteFile(1L);
    verify(fileStorageRepository).deleteById(1L);
  }

  @Test
  void clone_Success() {
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
  void deleteFiles_WithNullList_HandlesGracefully() {
    fileStorageService.deleteFiles(List.of(FileStorage.builder().build()));
    verify(fileStorageRepository).deleteAll(anyList());
  }

  @Test
  void deleteFiles_Success() {
    FileStorage file1 = new FileStorage();
    FileStorage file2 = new FileStorage();

    fileStorageService.deleteFiles(Arrays.asList(file1, file2));

    verify(fileStorageRepository).deleteAll(Arrays.asList(file1, file2));
  }
}
