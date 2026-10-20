package com.expensetracker.controller;

import com.expensetracker.model.*;
import com.expensetracker.repository.AlertLogRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserProfileRepository;
import com.expensetracker.service.AlertEngineService;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ExpenseParserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ExpenseApiController {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final AlertLogRepository alertLogRepository;
    private final UserProfileRepository userProfileRepository;
    private final BudgetService budgetService;
    private final AlertEngineService alertEngineService;
    private final ExpenseParserService parserService;

    @org.springframework.beans.factory.annotation.Value("${telegram.bot.username:SmartExpenseTrackerBot}")
    private String botUsername;

    public ExpenseApiController(ExpenseRepository expenseRepository,
                                CategoryRepository categoryRepository,
                                AlertLogRepository alertLogRepository,
                                UserProfileRepository userProfileRepository,
                                BudgetService budgetService,
                                AlertEngineService alertEngineService,
                                ExpenseParserService parserService) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.alertLogRepository = alertLogRepository;
        this.userProfileRepository = userProfileRepository;
        this.budgetService = budgetService;
        this.alertEngineService = alertEngineService;
        this.parserService = parserService;
    }

    @GetMapping("/bot-info")
    public ResponseEntity<Map<String, String>> getBotInfo() {
        return ResponseEntity.ok(Map.of(
            "username", botUsername,
            "url", "https://t.me/" + botUsername
        ));
    }

    private UserProfile getSessionUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId != null) {
            return userProfileRepository.findById(userId).orElse(null);
        }
        return null;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        return ResponseEntity.ok(budgetService.getDashboardSummary(user, LocalDate.now()));
    }

    @GetMapping("/expenses")
    public ResponseEntity<?> getExpenses(HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        return ResponseEntity.ok(expenseRepository.findTop15ByUserProfileOrderByDateDescCreatedAtDesc(user));
    }

    public static class CreateExpenseRequest {
        public String rawText;
        public Double amount;
        public String description;
        public Long categoryId;
        public String date;
    }

    @PostMapping("/expenses")
    public ResponseEntity<?> addExpense(@RequestBody CreateExpenseRequest request, HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }

        Double amount = request.amount;
        String desc = request.description;
        Category category = null;
        String type = "EXPENSE";

        if (request.rawText != null && !request.rawText.trim().isEmpty()) {
            ExpenseParserService.ParsedResult parsed = parserService.parse(request.rawText);
            if (parsed != null) {
                if (parsed.getIntent() == ExpenseParserService.Intent.SET_BUDGET) {
                    Budget budget;
                    if ("General".equalsIgnoreCase(parsed.getCategoryName()) || request.rawText.toLowerCase().contains("add")) {
                        budget = budgetService.boostBudget(user, parsed.getAmount(), LocalDate.now());
                    } else {
                        budget = budgetService.setBudget(user, parsed.getCategoryName(), parsed.getAmount(), LocalDate.now());
                    }
                    return ResponseEntity.ok(Map.of(
                            "budget", budget,
                            "summary", budgetService.getDashboardSummary(user, LocalDate.now()),
                            "message", "Budget updated for " + parsed.getCategoryName()
                    ));
                }
                amount = parsed.getAmount();
                desc = parsed.getDescription();
                category = parsed.getCategory();
                if (parsed.getIntent() == ExpenseParserService.Intent.INCOME) {
                    type = "INCOME";
                }
            }
        }

        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount must be greater than zero"));
        }

        if (category == null && request.categoryId != null) {
            category = categoryRepository.findById(request.categoryId).orElse(null);
        }

        if (category == null) {
            category = categoryRepository.findByNameIgnoreCase("Other")
                    .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));
        }

        LocalDate expDate = LocalDate.now();
        if (request.date != null && !request.date.trim().isEmpty()) {
            try {
                expDate = LocalDate.parse(request.date);
            } catch (Exception ignored) {}
        }

        Expense expense = new Expense(amount, (desc != null && !desc.trim().isEmpty()) ? desc : (type.equals("INCOME") ? "Income" : "Expense"),
                category, user, expDate, "WEB", type, request.rawText);
        expenseRepository.save(expense);

        // Evaluate smart alerts only for expenses
        List<AlertLog> alerts = type.equals("EXPENSE") ? alertEngineService.evaluateExpense(expense) : Collections.emptyList();

        Map<String, Object> response = new HashMap<>();
        response.put("expense", expense);
        response.put("alerts", alerts);
        response.put("summary", budgetService.getDashboardSummary(user, LocalDate.now()));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<?> deleteExpense(@PathVariable Long id, HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }

        expenseRepository.findById(id).ifPresent(expense -> {
            if (expense.getUserProfile() != null && expense.getUserProfile().getId().equals(user.getId())) {
                expenseRepository.delete(expense);
            }
        });
        return ResponseEntity.ok(Map.of("message", "Expense deleted successfully"));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    public static class SetBudgetRequest {
        public String categoryName;
        public Double limit;
    }

    @PostMapping("/budgets")
    public ResponseEntity<?> setBudget(@RequestBody SetBudgetRequest req, HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        if (req.categoryName == null || req.limit == null || req.limit < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name and positive limit required"));
        }

        Budget budget = budgetService.setBudget(user, req.categoryName, req.limit, LocalDate.now());
        return ResponseEntity.ok(Map.of("budget", budget, "summary", budgetService.getDashboardSummary(user, LocalDate.now())));
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts(HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        return ResponseEntity.ok(alertLogRepository.findTop15ByUserProfileOrderByCreatedAtDesc(user));
    }

    @GetMapping("/afford-check")
    public ResponseEntity<?> checkAffordability(
            @RequestParam double amount,
            @RequestParam(defaultValue = "this item") String item,
            HttpSession session) {
        UserProfile user = getSessionUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        return ResponseEntity.ok(budgetService.checkAffordability(user, amount, item));
    }
}
