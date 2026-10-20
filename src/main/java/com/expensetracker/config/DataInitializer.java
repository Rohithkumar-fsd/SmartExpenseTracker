package com.expensetracker.config;

import com.expensetracker.model.*;
import com.expensetracker.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(CategoryRepository categoryRepository,
                                          BudgetRepository budgetRepository,
                                          ExpenseRepository expenseRepository,
                                          AlertLogRepository alertLogRepository,
                                          UserProfileRepository userProfileRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed Categories if not present
            if (categoryRepository.count() == 0) {
                System.out.println("🌱 Seeding default categories...");
                categoryRepository.save(new Category("Food & Dining", "🍔", "#f59e0b"));
                categoryRepository.save(new Category("Transport", "🚗", "#3b82f6"));
                categoryRepository.save(new Category("Shopping", "🛍️", "#ec4899"));
                categoryRepository.save(new Category("Entertainment", "🍿", "#8b5cf6"));
                categoryRepository.save(new Category("Bills & Utilities", "💡", "#10b981"));
                categoryRepository.save(new Category("Healthcare", "💊", "#ef4444"));
                categoryRepository.save(new Category("Income & Salary", "💵", "#059669"));
                categoryRepository.save(new Category("Other", "📦", "#6b7280"));
            }

            // Seed Demo User if not present
            if (userProfileRepository.count() == 0) {
                System.out.println("🌱 Seeding initial demo student account...");
                UserProfile demoUser = new UserProfile(
                        "demo",
                        "demo@college.edu",
                        passwordEncoder.encode("password123"),
                        "Rohit (Demo)",
                        "₹"
                );
                demoUser.setTelegramLinkCode("123456");
                demoUser = userProfileRepository.save(demoUser);

                LocalDate now = LocalDate.now();
                int month = now.getMonthValue();
                int year = now.getYear();

                Category food = categoryRepository.findByNameIgnoreCase("Food & Dining").orElse(null);
                Category transport = categoryRepository.findByNameIgnoreCase("Transport").orElse(null);
                Category shopping = categoryRepository.findByNameIgnoreCase("Shopping").orElse(null);
                Category entertainment = categoryRepository.findByNameIgnoreCase("Entertainment").orElse(null);
                Category utilities = categoryRepository.findByNameIgnoreCase("Bills & Utilities").orElse(null);

                if (food != null) budgetRepository.save(new Budget(food, demoUser, 3500.0, month, year));
                if (transport != null) budgetRepository.save(new Budget(transport, demoUser, 1200.0, month, year));
                if (shopping != null) budgetRepository.save(new Budget(shopping, demoUser, 2000.0, month, year));
                if (entertainment != null) budgetRepository.save(new Budget(entertainment, demoUser, 800.0, month, year));
                if (utilities != null) budgetRepository.save(new Budget(utilities, demoUser, 1500.0, month, year));

                // Sample Expenses
                if (food != null) {
                    expenseRepository.save(new Expense(140.0, "College Canteen Lunch", food, demoUser, now.minusDays(3), "TELEGRAM", "Canteen 140"));
                    expenseRepository.save(new Expense(450.0, "Hostel Grocery Store", food, demoUser, now.minusDays(1), "WEB", "Groceries 450"));
                }
                if (transport != null) {
                    expenseRepository.save(new Expense(80.0, "Metro Card Recharge", transport, demoUser, now.minusDays(2), "TELEGRAM", "Metro 80"));
                }
                if (shopping != null) {
                    expenseRepository.save(new Expense(899.0, "Campus Hoodie", shopping, demoUser, now, "WEB", "Hoodie 899"));
                }

                // Sample Velocity Alert
                if (food != null) {
                    alertLogRepository.save(new AlertLog(
                            "VELOCITY_WARNING",
                            "WARNING",
                            "🏎️ Burn-Rate Alert: Food & Dining",
                            "You are spending ₹196.67/day on Food. At this pace, your budget will run out before mid-month!",
                            "Suggested speed limit: Cap food spending to ₹115.00/day for remaining days.",
                            food,
                            demoUser
                    ));
                }

                System.out.println("✅ Demo account created! (Username: demo | Password: password123 | Link Code: 123456)");
            }
        };
    }
}
