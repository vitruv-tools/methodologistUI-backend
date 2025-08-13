package com.vituv.methodologist.user.model;

import com.vituv.methodologist.user.RoleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "usr")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    private RoleType roleType;

    private String username;
    private String firstName;
    private String lastName;

    @CreationTimestamp
    private Instant createdAt;
    private Instant removedAt;
}