package com.vitruv.methodologist.general.service;

import com.vitruv.methodologist.general.model.FileStorage;
import com.vitruv.methodologist.general.model.repository.FileStorageRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Service class that handles file storage operations including storing, retrieving, and deleting files.
 * Provides deduplication of files based on SHA-256 hash and file size.
 */
@Service
public class FileStorageService {
    private final FileStorageRepository fileStorageRepository;

    public FileStorageService(FileStorageRepository fileStorageRepository) {
        this.fileStorageRepository = fileStorageRepository;
    }

    @Transactional
    public FileStorage storeFile(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        byte[] data = file.getBytes();
        String sha = sha256Hex(data);

        // dedup check
        return fileStorageRepository.findBySha256AndSizeBytes(sha, data.length).orElseGet(() -> {
            FileStorage f = new FileStorage();
            f.setFilename(file.getOriginalFilename());
            f.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            f.setSizeBytes(data.length);
            f.setSha256(sha);
            f.setData(data);
            f.setCreatedBy("createdBy");
            return fileStorageRepository.save(f);
        });
    }

    @Transactional(readOnly = true)
    public FileStorage getFile(Long id) {
        return fileStorageRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
    }

    @Transactional
    public void deleteFile(Long id) {
        fileStorageRepository.deleteById(id);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }
}