package com.expensetracker.service;

import com.expensetracker.model.Category;
import com.expensetracker.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExpenseParserService {

    private final CategoryRepository categoryRepository;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:\\$|₹|€|£|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:\\$|₹|€|£|rs\\.?|inr|bucks)?");

    private final Map<String, List<String>> keywordMap = new HashMap<>();

    public enum Intent {
        EXPENSE,
        INCOME,
        SET_BUDGET,
        AFFORD_CHECK,
        TODAY,
        SUMMARY
    }

    public static class ParsedResult {
        private final Intent intent;
        private final Double amount;
        private final String description;
        private final Category category;
        private final String categoryName;

        public ParsedResult(Intent intent, Double amount, String description, Category category, String categoryName) {
            this.intent = intent;
            this.amount = amount;
            this.description = description;
            this.category = category;
            this.categoryName = categoryName;
        }

        public Intent getIntent() { return intent; }
        public Double getAmount() { return amount; }
        public String getDescription() { return description; }
        public Category getCategory() { return category; }
        public String getCategoryName() { return categoryName; }
    }

    public ExpenseParserService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
        initializeKeywords();
    }

    private void initializeKeywords() {
        keywordMap.put("Food & Dining", Arrays.asList(
            "coffee", "starbucks", "lunch", "dinner", "breakfast", "cafe", "burger", 
            "pizza", "subway", "mcdonalds", "chipotle", "restaurant", "food", "tea", 
            "snack", "beer", "drinks", "grocery", "groceries", "supermarket", "chai", "canteen", "biryani"
        ));
        keywordMap.put("Transport", Arrays.asList(
            "uber", "lyft", "taxi", "cab", "metro", "bus", "train", "fuel", 
            "petrol", "gas", "diesel", "parking", "toll", "flight", "auto", "rickshaw"
        ));
        keywordMap.put("Shopping", Arrays.asList(
            "amazon", "flipkart", "clothes", "shoes", "zara", "h&m", "mall", 
            "electronics", "gadget", "shirt", "pants", "dress", "shopping",
            "headphones", "headset", "earphones", "earbuds", "airpods", "watch", "laptop"
        ));
        keywordMap.put("Entertainment", Arrays.asList(
            "netflix", "spotify", "movie", "cinema", "concert", "game", "steam", 
            "theatre", "disney", "prime", "youtube"
        ));
        keywordMap.put("Bills & Utilities", Arrays.asList(
            "rent", "electricity", "water", "internet", "wifi", "bill", "recharge", 
            "phone", "mobile", "gas bill"
        ));
        keywordMap.put("Healthcare", Arrays.asList(
            "doctor", "medicine", "pharmacy", "hospital", "gym", "workout", "clinic"
        ));
        keywordMap.put("Income & Salary", Arrays.asList(
            "salary", "stipend", "income", "freelance", "allowance", "pocket money", "cashback", "bonus"
        ));
    }

    public ParsedResult parse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return null;
        }

        String text = rawText.trim();
        String lower = text.toLowerCase();

        // 1. Detect Commands / System Intents
        if (lower.equals("/today") || lower.contains("safe to spend") || lower.contains("spend today") || lower.contains("daily allowance")) {
            return new ParsedResult(Intent.TODAY, null, text, null, null);
        }

        if (lower.equals("/summary") || lower.contains("monthly summary") || lower.contains("spending summary") || lower.contains("how much did i spend")) {
            return new ParsedResult(Intent.SUMMARY, null, text, null, null);
        }

        // 2. Extract numeric amount
        Double extractedAmount = null;
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        String amountStr = null;
        while (matcher.find()) {
            amountStr = matcher.group(1);
        }

        if (amountStr != null) {
            try {
                extractedAmount = Double.parseDouble(amountStr);
            } catch (NumberFormatException ignored) {}
        }

        // 3. Detect "Can I Afford This?" Pre-Purchase Simulator Intent
        boolean isAffordCheck = lower.startsWith("/afford") ||
                lower.contains("can i afford") || lower.contains("can i buy") ||
                lower.contains("should i buy") || lower.contains("i want to buy") ||
                lower.contains("want to buy") || lower.contains("planning to buy") ||
                lower.contains("thinking of buying") || lower.contains("am i able to buy");

        if (isAffordCheck) {
            String item = text.replaceAll("(?i)^/afford\\s*", "")
                    .replaceAll("(?i)\\b(?:can i afford|can i buy|should i buy|i want to buy|want to buy|planning to buy|thinking of buying|am i able to buy|buy|bought|costs?|costing|priced at|price|for|this|a|an|rs\\.?|inr|bucks)\\b", "")
                    .replaceAll("(?i)(?:\\$|₹|€|£|rs\\.?|inr)?\\s*" + (amountStr != null ? Pattern.quote(amountStr) : "") + "\\s*(?:\\$|₹|€|£|rs\\.?|inr|bucks)?", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (item.isEmpty()) item = "this item";
            item = item.substring(0, 1).toUpperCase() + (item.length() > 1 ? item.substring(1) : "");
            return new ParsedResult(Intent.AFFORD_CHECK, extractedAmount, item, null, null);
        }

        if (extractedAmount == null) {
            return null;
        }

        // 4. Match Category
        Category matchedCategory = null;
        String matchedCategoryName = "Other";

        for (Map.Entry<String, List<String>> entry : keywordMap.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    matchedCategoryName = entry.getKey();
                    matchedCategory = categoryRepository.findByNameIgnoreCase(entry.getKey()).orElse(null);
                    if (matchedCategory != null) break;
                }
            }
            if (matchedCategory != null) break;
        }

        if (matchedCategory == null) {
            matchedCategory = categoryRepository.findByNameIgnoreCase("Other")
                    .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));
        }

        // 5. Detect Income Intent
        // Matches: "add 5000 to my budget as salary", "salary 50000", "received 5000 as salary", "pocket money 1000", "stipend 8000"
        boolean isIncomeWord = lower.contains("salary") || lower.contains("stipend") || lower.contains("income") ||
                               lower.contains("pocket money") || lower.contains("cashback") || lower.contains("bonus") ||
                               lower.contains("freelance") || lower.contains("allowance") || lower.contains("credited");

        if (isIncomeWord) {
            String desc = text.replaceAll("(?i)(?:\\$|₹|€|£|rs\\.?|inr)?\\s*" + Pattern.quote(amountStr) + "\\s*(?:\\$|₹|€|£|rs\\.?|inr|bucks)?", "")
                    .replaceAll("(?i)\\b(?:add|to my budget as|to my budget|to my remaining budget|to my account as|to my account|credited|received|got|as|for|my|in|a|an)\\b", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (desc.isEmpty()) desc = "Salary";
            desc = desc.substring(0, 1).toUpperCase() + (desc.length() > 1 ? desc.substring(1) : "");

            Category incomeCat = categoryRepository.findByNameIgnoreCase("Income & Salary")
                    .orElseGet(() -> categoryRepository.save(new Category("Income & Salary", "💵", "#059669")));

            return new ParsedResult(Intent.INCOME, extractedAmount, desc, incomeCat, "Income & Salary");
        }

        // 6. Detect Budget Intent (e.g. "add 5000 to my budget", "add 5000 to my ramaining budget", "set budget for food 3000", "/budget Food 3500")
        boolean isBudgetWord = lower.contains("budget") || lower.contains("remaining budget") || lower.contains("ramaining budget") || lower.startsWith("/budget");
        if (isBudgetWord) {
            String targetCategoryName = matchedCategoryName;
            if ("Other".equals(targetCategoryName) && !lower.contains("other")) {
                targetCategoryName = "General";
            }
            return new ParsedResult(Intent.SET_BUDGET, extractedAmount, text, matchedCategory, targetCategoryName);
        }

        // 7. Default: Expense Intent (Normal Spending)
        String description = text.replaceAll("(?i)(?:\\$|₹|€|£|rs\\.?|inr)?\\s*" + Pattern.quote(amountStr) + "\\s*(?:\\$|₹|€|£|rs\\.?|inr|bucks)?", "")
                .replaceAll("(?i)\\b(?:bought|buy|order|ordered|add|spent|paid|to my account as|to my account|as|for|a|an)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (description.isEmpty()) {
            description = matchedCategoryName.equals("Other") ? "Quick Expense" : matchedCategoryName;
        } else {
            description = description.substring(0, 1).toUpperCase() + (description.length() > 1 ? description.substring(1) : "");
        }

        return new ParsedResult(Intent.EXPENSE, extractedAmount, description, matchedCategory, matchedCategoryName);
    }
}
