package com.prod.repository;

import com.prod.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity operations.
 *
 * Provides database access methods for user management, including
 * whitelist validation and user lookup by email.
 *
 * Spring Data JPA will automatically implement this interface at runtime.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their email address.
     *
     * @param email the email address to search for
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user with the given email exists.
     *
     * @param email the email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find a user by email and whitelist status.
     * Useful for authentication flow to verify both existence and whitelist status.
     *
     * @param email the email address to search for
     * @param isWhitelisted the whitelist status to match
     * @return Optional containing the user if found with matching criteria, empty otherwise
     */
    Optional<User> findByEmailAndIsWhitelisted(String email, Boolean isWhitelisted);
}
