package com.prod.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity for whitelist-based authentication.
 *
 * Represents users in the productivity tracking system. Only whitelisted users
 * can authenticate and access the system via OTP-based authentication flow.
 *
 * Database Table: users
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Primary key using UUID for better security and scalability.
     * Generated automatically using database UUID generation.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * User's email address. Used for OTP-based authentication.
     * Must be unique and not null.
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Whitelist flag indicating whether the user is allowed to access the system.
     * Only whitelisted users can request OTP and authenticate.
     */
    @Column(name = "is_whitelisted", nullable = false)
    private Boolean isWhitelisted = false;

    /**
     * Timestamp of when the user record was created.
     * Automatically set by Hibernate on entity creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of when the user record was last updated.
     * Automatically updated by Hibernate on entity modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Constructor for creating a new user with email and whitelist status.
     *
     * @param email the user's email address
     * @param isWhitelisted whether the user is whitelisted
     */
    public User(String email, Boolean isWhitelisted) {
        this.email = email;
        this.isWhitelisted = isWhitelisted;
    }
}
