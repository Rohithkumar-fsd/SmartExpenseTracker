package com.expensetracker.service;

import com.expensetracker.model.*;
import com.expensetracker.repository.AlertLogRepository;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AlertEngineService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final AlertLogRepository alertLogRepository;

    public AlertEngineService(BudgetRepository budgetRepository,
                              ExpenseRepository expenseRepository,
                              AlertLogRepository alertLogRepository) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.alertLogRepository = alertLogRepository;
    }

    public List<AlertLog> evaluateExpense(Expense newExpense) {
        List<AlertLog> triggeredAlerts = new ArrayList<>();
        if (newExpense.getCategory() == null || newExpense.getUserProfile() == null) {
            return triggeredAlerts;
        }

        UserProfile user = newExpense.getUserProfile();
        LocalDate today = newExpense.getDate();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        String cur = user.getCurrency() != null ? user.getCurrency() : "₹";

        // 1. Get Monthly Budget for this User and Category
        Optional<Budget> budgetOpt = budgetRepository.findByCategoryAndUserProfileAndMonthAndYear(
                newExpense.getCategory(), user, today.getMonthValue(), today.getYear());

        Double categorySpentSoFar = expenseRepository.sumAmountByUserProfileAndCategoryAndDateBetween(
                user, newExpense.getCategory(), startOfMonth, endOfMonth);
        if (categorySpentSoFar == null) categorySpentSoFar = 0.0;

        if (budgetOpt.isPresent()) {
            Budget budget = budgetOpt.get();
            double limit = budget.getMonthlyLimit();
            int currentDay = Math.max(1, today.getDayOfMonth());
            int totalDaysInMonth = today.lengthOfMonth();
            int daysRemaining = Math.max(1, totalDaysInMonth - currentDay);

            // A. Speedometer / Velocity Burn-Rate Check
            double currentDailyPace = categorySpentSoFar / currentDay;
            double projectedTotal = currentDailyPace * totalDaysInMonth;

            if (projectedTotal > limit && categorySpentSoFar < limit) {
                int estimatedExhaustDay = (int) Math.ceil(limit / currentDailyPace);
                double remainingBudget = Math.max(0, limit - categorySpentSoFar);
                double suggestedCap = remainingBudget / daysRemaining;

                String title = "🏎️ Burn-Rate Alert: " + newExpense.getCategory().getName();
                String message = String.format(
                        "You're spending %s%.2f/day on %s. At this pace, your budget will run out on Day %d of %d! (Projected: %s%.2f / Limit: %s%.2f)",
                        cur, currentDailyPace, newExpense.getCategory().getName(), estimatedExhaustDay, totalDaysInMonth, cur, projectedTotal, cur, limit);
                String remedy = String.format("Suggested speed limit: Cap %s spending to %s%.2f/day for the remaining %d days.",
                        newExpense.getCategory().getName(), cur, suggestedCap, daysRemaining);

                AlertLog alert = new AlertLog("VELOCITY_WARNING", "WARNING", title, message, remedy,
                        newExpense.getCategory(), user);
                alertLogRepository.save(alert);
                triggeredAlerts.add(alert);
            }

            // B. Direct Threshold Breach (80% and 100%)
            if (categorySpentSoFar >= limit) {
                double overage = categorySpentSoFar - limit;
                String title = "🚨 Budget Exceeded: " + newExpense.getCategory().getName();
                String message = String.format("You have exceeded your %s budget by %s%.2f! Total spent: %s%.2f (Limit: %s%.2f)",
                        newExpense.getCategory().getName(), cur, overage, cur, categorySpentSoFar, cur, limit);
                String remedy = "Pause all non-essential purchases in this category for the rest of the month.";

                AlertLog alert = new AlertLog("BUDGET_EXCEEDED", "CRITICAL", title, message, remedy,
                        newExpense.getCategory(), user);
                alertLogRepository.save(alert);
                triggeredAlerts.add(alert);
            } else if (categorySpentSoFar >= limit * 0.85) {
                double remaining = limit - categorySpentSoFar;
                String title = "⚠️ 85% Budget Warning: " + newExpense.getCategory().getName();
                String message = String.format("You have reached 85%% of your %s budget. Only %s%.2f left for the month.",
                        newExpense.getCategory().getName(), cur, remaining);
                String remedy = "Carefully review remaining planned purchases.";

                AlertLog alert = new AlertLog("BUDGET_WARNING", "WARNING", title, message, remedy,
                        newExpense.getCategory(), user);
                alertLogRepository.save(alert);
                triggeredAlerts.add(alert);
            }
        }

        // C. Anomaly Spike Detector
        List<Expense> recentExpenses = expenseRepository.findByUserProfileAndCategoryAndDateBetween(
                user, newExpense.getCategory(), today.minusDays(30), today);

        if (recentExpenses.size() >= 3) {
            double sum = 0;
            for (Expense exp : recentExpenses) {
                sum += exp.getAmount();
            }
            double average = sum / recentExpenses.size();

            if (newExpense.getAmount() > average * 2.5 && newExpense.getAmount() > 40.0) {
                String title = "⚡ Unusual Expense Spike Detected";
                String message = String.format("This %s%.2f spend on %s is %.1fx higher than your average (%s%.2f).",
                        cur, newExpense.getAmount(), newExpense.getDescription(), (newExpense.getAmount() / average), cur, average);
                String remedy = "Confirm this was an intentional purchase and not a billing error or impulse buy.";

                AlertLog alert = new AlertLog("ANOMALY_SPIKE", "INFO", title, message, remedy,
                        newExpense.getCategory(), user);
                alertLogRepository.save(alert);
                triggeredAlerts.add(alert);
            }
        }

        return triggeredAlerts;
    }
}
