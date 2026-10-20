package com.expensetracker.repository;

import com.expensetracker.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUsernameIgnoreCase(String username);
    Optional<UserProfile> findByEmailIgnoreCase(String email);
    Optional<UserProfile> findByTelegramChatId(Long telegramChatId);
    Optional<UserProfile> findByTelegramLinkCode(String telegramLinkCode);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
}
