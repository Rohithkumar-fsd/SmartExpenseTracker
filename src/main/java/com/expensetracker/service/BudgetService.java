package com.expensetracker.service;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.UserProfile;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         CategoryRepository categoryRepository,
                         ExpenseRepository expenseRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }

    public static class CategoryBudgetStatus {
        private String categoryName;
        private String icon;
        private String colorHex;
        private double limit;
        private double spent;
        private double remaining;
        private int percentUsed;
        private String status; // "HEALTHY", "WARNING", "EXCEEDED"

        public CategoryBudgetStatus(String categoryName, String icon, String colorHex, double limit, double spent) {
            this.categoryName = categoryName;
            this.icon = icon;
            this.colorHex = colorHex;
            this.limit = limit;
            this.spent = spent;
            this.remaining = Math.max(0, limit - spent);
            this.percentUsed = limit > 0 ? (int) Math.round((spent / limit) * 100) : 0;
            if (spent >= limit && limit > 0) {
                this.status = "EXCEEDED";
            } else if (this.percentUsed >= 80) {
                this.status = "WARNING";
            } else {
                this.status = "HEALTHY";
            }
        }

        public String getCategoryName() { return categoryName; }
        public String getIcon() { return icon; }
        public String getColorHex() { return colorHex; }
        public double getLimit() { return limit; }
        public double getSpent() { return spent; }
        public double getRemaining() { return remaining; }
        public int getPercentUsed() { return percentUsed; }
        public String getStatus() { return status; }
    }

    public static class DashboardSummary {
        private double totalBudget;
        private double totalSpent;
        private double totalIncome;
        private double remainingBudget;
        private double safeToSpendToday;
        private int percentBudgetUsed;
        private int daysRemainingInMonth;
        private String currency;
        private List<CategoryBudgetStatus> categoryStatuses;

        public DashboardSummary(double totalBudget, double totalSpent, double totalIncome, double safeToSpendToday,
                                int daysRemainingInMonth, String currency, List<CategoryBudgetStatus> categoryStatuses) {
            this.totalBudget = totalBudget;
            this.totalSpent = totalSpent;
            this.totalIncome = totalIncome;
            this.remainingBudget = Math.max(0, totalBudget - totalSpent);
            this.safeToSpendToday = safeToSpendToday;
            this.percentBudgetUsed = totalBudget > 0 ? (int) Math.round((totalSpent / totalBudget) * 100) : 0;
            this.daysRemainingInMonth = daysRemainingInMonth;
            this.currency = currency != null ? currency : "₹";
            this.categoryStatuses = categoryStatuses;
        }

        public double getTotalBudget() { return totalBudget; }
        public double getTotalSpent() { return totalSpent; }
        public double getTotalIncome() { return totalIncome; }
        public double getRemainingBudget() { return remainingBudget; }
        public double getSafeToSpendToday() { return safeToSpendToday; }
        public int getPercentBudgetUsed() { return percentBudgetUsed; }
        public int getDaysRemainingInMonth() { return daysRemainingInMonth; }
        public String getCurrency() { return currency; }
        public List<CategoryBudgetStatus> getCategoryStatuses() { return categoryStatuses; }
    }

    public DashboardSummary getDashboardSummary(UserProfile user, LocalDate date) {
        LocalDate startOfMonth = date.withDayOfMonth(1);
        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());
        int currentDay = Math.max(1, date.getDayOfMonth());
        int totalDays = date.lengthOfMonth();
        int daysRemaining = Math.max(1, totalDays - currentDay + 1);
        String currency = user != null && user.getCurrency() != null ? user.getCurrency() : "₹";

        List<Budget> budgets = user != null ?
                budgetRepository.findByUserProfileAndMonthAndYear(user, date.getMonthValue(), date.getYear()) :
                Collections.emptyList();

        double totalBudget = 0.0;
        for (Budget b : budgets) {
            totalBudget += b.getMonthlyLimit();
        }

        Double totalSpent = user != null ?
                expenseRepository.sumAmountByUserProfileAndDateBetween(user, startOfMonth, endOfMonth) : 0.0;
        if (totalSpent == null) totalSpent = 0.0;

        Double totalIncome = user != null ?
                expenseRepository.sumIncomeByUserProfileAndDateBetween(user, startOfMonth, endOfMonth) : 0.0;
        if (totalIncome == null) totalIncome = 0.0;

        // If the user has income but has not configured explicit budget limits,
        // their effective budget is their total income, or totalBudget + totalIncome if both exist
        double effectiveBudget = totalBudget;
        if (effectiveBudget == 0.0 && totalIncome > 0.0) {
            effectiveBudget = totalIncome;
        }

        double remainingBudget = Math.max(0, effectiveBudget - totalSpent);
        double safeToSpendToday = remainingBudget / daysRemaining;

        List<CategoryBudgetStatus> categoryStatuses = new ArrayList<>();
        List<Category> allCategories = categoryRepository.findAll();

        for (Category cat : allCategories) {
            Optional<Budget> bOpt = user != null ?
                    budgetRepository.findByCategoryAndUserProfileAndMonthAndYear(cat, user, date.getMonthValue(), date.getYear()) :
                    Optional.empty();
            double limit = bOpt.map(Budget::getMonthlyLimit).orElse(0.0);
            Double spent = user != null ?
                    expenseRepository.sumAmountByUserProfileAndCategoryAndDateBetween(user, cat, startOfMonth, endOfMonth) : 0.0;
            categoryStatuses.add(new CategoryBudgetStatus(
                    cat.getName(), cat.getIcon(), cat.getColorHex(), limit, spent != null ? spent : 0.0));
        }

        return new DashboardSummary(effectiveBudget, totalSpent, totalIncome, safeToSpendToday, daysRemaining, currency, categoryStatuses);
    }

    public static class AffordabilityResult {
        private String verdict; // "SAFE", "CAUTION", "RISKY"
        private String headline;
        private String details;
        private double currentSafeDaily;
        private double newSafeDaily;

        public AffordabilityResult(String verdict, String headline, String details, double currentSafeDaily, double newSafeDaily) {
            this.verdict = verdict;
            this.headline = headline;
            this.details = details;
            this.currentSafeDaily = currentSafeDaily;
            this.newSafeDaily = newSafeDaily;
        }

        public String getVerdict() { return verdict; }
        public String getHeadline() { return headline; }
        public String getDetails() { return details; }
        public double getCurrentSafeDaily() { return currentSafeDaily; }
        public double getNewSafeDaily() { return newSafeDaily; }
    }

    public AffordabilityResult checkAffordability(UserProfile user, double amount, String item) {
        LocalDate today = LocalDate.now();
        DashboardSummary summary = getDashboardSummary(user, today);
        String cur = summary.getCurrency();

        double remaining = summary.getRemainingBudget();
        int daysRemaining = summary.getDaysRemainingInMonth();
        double currentDaily = summary.getSafeToSpendToday();

        if (summary.getTotalBudget() == 0 && summary.getTotalIncome() == 0) {
            return new AffordabilityResult("CAUTION", "No monthly budget or income set yet!",
                    "Set your monthly budgets or record income first to get accurate pre-purchase checks.", 0.0, 0.0);
        }

        if (amount > remaining) {
            double deficit = amount - remaining;
            return new AffordabilityResult("RISKY",
                    String.format("🛑 Risky: Buying %s (%s%.2f) exceeds your remaining budget!", item, cur, amount),
                    String.format("You only have %s%.2f left for the month. This purchase puts you in a %s%.2f deficit.", cur, remaining, cur, deficit),
                    currentDaily, 0.0);
        }

        double newRemaining = remaining - amount;
        double newDaily = newRemaining / daysRemaining;

        if (newDaily < currentDaily * 0.40) {
            return new AffordabilityResult("CAUTION",
                    String.format("⚠️ Caution: You can technically afford %s, but it tightens your belt.", item),
                    String.format("Your daily safe allowance will drop from %s%.2f/day down to only %s%.2f/day for the next %d days.",
                            cur, currentDaily, cur, newDaily, daysRemaining),
                    currentDaily, newDaily);
        }

        return new AffordabilityResult("SAFE",
                String.format("✅ Safe to buy! You can afford %s (%s%.2f).", item, cur, amount),
                String.format("After this purchase, you will still have a comfortable %s%.2f/day safe allowance for the remaining %d days.",
                        cur, newDaily, daysRemaining),
                currentDaily, newDaily);
    }

    public Budget setBudget(UserProfile user, String categoryName, double limit, LocalDate date) {
        Category category = categoryRepository.findByNameIgnoreCase(categoryName)
                .orElseGet(() -> categoryRepository.save(new Category(categoryName, "💰", "#6366f1")));

        Budget budget = budgetRepository.findByCategoryAndUserProfileAndMonthAndYear(category, user, date.getMonthValue(), date.getYear())
                .orElseGet(() -> new Budget(category, user, limit, date.getMonthValue(), date.getYear()));

        budget.setMonthlyLimit(limit);
        return budgetRepository.save(budget);
    }

    public Budget boostBudget(UserProfile user, double additionalAmount, LocalDate date) {
        Category category = categoryRepository.findByNameIgnoreCase("Other")
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst()
                        .orElseGet(() -> categoryRepository.save(new Category("General", "💰", "#6366f1"))));

        final Category targetCategory = category;
        Budget budget = budgetRepository.findByCategoryAndUserProfileAndMonthAndYear(targetCategory, user, date.getMonthValue(), date.getYear())
                .orElseGet(() -> new Budget(targetCategory, user, 0.0, date.getMonthValue(), date.getYear()));

        budget.setMonthlyLimit(budget.getMonthlyLimit() + additionalAmount);
        return budgetRepository.save(budget);
    }
}
