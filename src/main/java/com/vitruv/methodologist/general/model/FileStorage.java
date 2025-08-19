package com.vitruv.methodologist.general.model;

import com.vitruv.methodologist.user.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity class representing a file stored in the database.
 * Contains metadata and binary content of uploaded files.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class FileStorage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String filename;

    @NotNull
    private String contentType;

    @NotNull
    private long sizeBytes;

    @NotNull
    private String sha256;

    @Lob
    @NotNull
    private byte[] data;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    private Instant createdAt;
}