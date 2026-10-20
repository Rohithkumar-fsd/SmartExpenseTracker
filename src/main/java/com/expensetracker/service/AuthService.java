package com.expensetracker.service;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.UserProfile;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.UserProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;

@Service
public class AuthService {

    private final UserProfileRepository userProfileRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserProfileRepository userProfileRepository,
                       CategoryRepository categoryRepository,
                       BudgetRepository budgetRepository,
                       PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.categoryRepository = categoryRepository;
        this.budgetRepository = budgetRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfile signup(String username, String email, String password, String name, String currency) {
        if (userProfileRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already taken! Please choose another.");
        }
        if (userProfileRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered! Please log in.");
        }

        String hashedPassword = passwordEncoder.encode(password);
        UserProfile user = new UserProfile(username, email, hashedPassword, name, currency);
        
        // Generate unique 6-digit Telegram Link Code
        user.setTelegramLinkCode(generateUniqueLinkCode());
        UserProfile savedUser = userProfileRepository.save(user);

        // Seed default budgets for this new user
        seedDefaultBudgetsForUser(savedUser);

        return savedUser;
    }

    public Optional<UserProfile> login(String usernameOrEmail, String rawPassword) {
        Optional<UserProfile> userOpt = userProfileRepository.findByUsernameIgnoreCase(usernameOrEmail);
        if (userOpt.isEmpty()) {
            userOpt = userProfileRepository.findByEmailIgnoreCase(usernameOrEmail);
        }

        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();
            if (passwordEncoder.matches(rawPassword, user.getPassword())) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<UserProfile> linkTelegramAccount(String linkCode, Long chatId) {
        Optional<UserProfile> userOpt = userProfileRepository.findByTelegramLinkCode(linkCode);
        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();

            // If this Telegram chatId is already linked to another user, unlink the previous user first
            userProfileRepository.findByTelegramChatId(chatId).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(user.getId())) {
                    existingUser.setTelegramChatId(null);
                    userProfileRepository.saveAndFlush(existingUser);
                }
            });

            user.setTelegramChatId(chatId);
            // Regenerate link code so it cannot be reused
            user.setTelegramLinkCode(generateUniqueLinkCode());
            return Optional.of(userProfileRepository.save(user));
        }
        return Optional.empty();
    }

    private String generateUniqueLinkCode() {
        Random random = new Random();
        String code;
        do {
            code = String.format("%06d", random.nextInt(900000) + 100000);
        } while (userProfileRepository.findByTelegramLinkCode(code).isPresent());
        return code;
    }

    private void seedDefaultBudgetsForUser(UserProfile user) {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();

        categoryRepository.findByNameIgnoreCase("Food & Dining").ifPresent(cat ->
            budgetRepository.save(new Budget(cat, user, 350.0, month, year)));
        categoryRepository.findByNameIgnoreCase("Transport").ifPresent(cat ->
            budgetRepository.save(new Budget(cat, user, 120.0, month, year)));
        categoryRepository.findByNameIgnoreCase("Shopping").ifPresent(cat ->
            budgetRepository.save(new Budget(cat, user, 180.0, month, year)));
        categoryRepository.findByNameIgnoreCase("Entertainment").ifPresent(cat ->
            budgetRepository.save(new Budget(cat, user, 80.0, month, year)));
        categoryRepository.findByNameIgnoreCase("Bills & Utilities").ifPresent(cat ->
            budgetRepository.save(new Budget(cat, user, 200.0, month, year)));
    }
}
