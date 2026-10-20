package com.expensetracker.bot;

import com.expensetracker.model.*;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserProfileRepository;
import com.expensetracker.service.AlertEngineService;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ExpenseParserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Managed explicitly as a Bean in BotConfig to ensure exactly 1 bot instance is registered
public class SmartExpenseBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username:SmartExpenseTrackerBot}")
    private String botUsername;

    private final ExpenseParserService parserService;
    private final AlertEngineService alertEngineService;
    private final BudgetService budgetService;
    private final AuthService authService;
    private final ExpenseRepository expenseRepository;
    private final UserProfileRepository userProfileRepository;
    private final CategoryRepository categoryRepository;

    public SmartExpenseBot(@Value("${telegram.bot.token:YOUR_BOT_TOKEN_HERE}") String botToken,
                           ExpenseParserService parserService,
                           AlertEngineService alertEngineService,
                           BudgetService budgetService,
                           AuthService authService,
                           ExpenseRepository expenseRepository,
                           UserProfileRepository userProfileRepository,
                           CategoryRepository categoryRepository) {
        super(botToken);
        this.parserService = parserService;
        this.alertEngineService = alertEngineService;
        this.budgetService = budgetService;
        this.authService = authService;
        this.expenseRepository = expenseRepository;
        this.userProfileRepository = userProfileRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        String firstName = update.getMessage().getFrom().getFirstName();

        System.out.println("📩 Received Telegram message from " + firstName + " (Chat ID " + chatId + "): " + text);

        try {
            // 1. Handle Link Command: /link <6-digit-code>
            if (text.startsWith("/link")) {
                handleLinkCommand(chatId, text, firstName);
                return;
            }

            // 2. Find user linked to this Telegram Chat
            Optional<UserProfile> profileOpt = userProfileRepository.findByTelegramChatId(chatId);

            if (profileOpt.isEmpty()) {
                sendResponse(chatId,
                        "👋 Hello " + firstName + "!\n\n" +
                        "To link this bot to your personal dashboard:\n" +
                        "1. Open the website at http://localhost:8080 and log in.\n" +
                        "2. Copy your **6-Digit Telegram Link Code** at the top right.\n" +
                        "3. Send me: `/link <your-code>`\n\n" +
                        "Example: `/link 482910`"
                );
                return;
            }

            UserProfile user = profileOpt.get();

            if (text.startsWith("/start")) {
                handleStartCommand(chatId, user);
            } else if (text.startsWith("/help")) {
                handleHelpCommand(chatId);
            } else if (text.startsWith("/today")) {
                handleTodayCommand(chatId, user);
            } else if (text.startsWith("/summary")) {
                handleSummaryCommand(chatId, user);
            } else if (text.startsWith("/budget")) {
                handleBudgetCommand(chatId, text, user);
            } else if (text.startsWith("/afford")) {
                handleAffordCommand(chatId, text, user);
            } else {
                handleExpenseLogging(chatId, text, user);
            }
        } catch (Exception e) {
            System.err.println("Error processing update: " + e.getMessage());
            e.printStackTrace();
            sendResponse(chatId, "⚠️ An error occurred while processing your message. Please try again.");
        }
    }

    private void handleLinkCommand(Long chatId, String text, String firstName) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendResponse(chatId, "⚠️ Usage: `/link <6-digit-code>`\nExample: `/link 482910`\n(Find this code on your web dashboard!)");
            return;
        }

        String code = parts[1].trim();
        Optional<UserProfile> linkedUser = authService.linkTelegramAccount(code, chatId);

        if (linkedUser.isPresent()) {
            UserProfile user = linkedUser.get();
            sendResponse(chatId,
                    "🎉 **Account Successfully Linked!**\n\n" +
                    "Welcome, **" + user.getName() + "**! Any expenses you send here will now automatically save to your personal dashboard.\n\n" +
                    "Try texting: `Coffee 20` or `/today`!"
            );
        } else {
            sendResponse(chatId, "❌ Invalid or expired Link Code. Please check the code on your web dashboard and try again.");
        }
    }

    private void handleStartCommand(Long chatId, UserProfile user) {
        String cur = user.getCurrency() != null ? user.getCurrency() : "₹";
        String welcome = "👋 Hello, " + user.getName() + "!\n\n" +
                "Your account is connected. You can log expenses anytime:\n\n" +
                "💡 **Quick Expense Logging:**\n" +
                "• `Coffee 30`\n" +
                "• `Lunch 120`\n" +
                "• `Auto rickshaw 60`\n\n" +
                "⚡ **Smart Commands:**\n" +
                "• `/today` - Safe-to-spend allowance for today\n" +
                "• `/afford 500 Shoes` - Pre-purchase check\n" +
                "• `/budget Food 2500` - Set category budget\n" +
                "• `/summary` - Monthly category breakdown";

        sendResponse(chatId, welcome);
    }

    private void handleHelpCommand(Long chatId) {
        String help = "🛠️ **Commands:**\n\n" +
                "• Just type what you spent: e.g. `Pizza 250` or `Petrol 100`\n" +
                "• `/today` - Daily safe financial allowance\n" +
                "• `/afford <Amount> <Item>` - Check if you can afford it\n" +
                "• `/budget <Category> <Amount>` - Set monthly category limit\n" +
                "• `/summary` - Monthly overview";

        sendResponse(chatId, help);
    }

    private void handleTodayCommand(Long chatId, UserProfile user) {
        BudgetService.DashboardSummary summary = budgetService.getDashboardSummary(user, LocalDate.now());
        String cur = summary.getCurrency();
        String msg = String.format(
                "☀️ **Daily Financial Allowance**\n\n" +
                "• **Safe to spend today:** %s%.2f\n" +
                "• **Remaining budget this month:** %s%.2f\n" +
                "• **Total spent this month:** %s%.2f / %s%.2f\n" +
                "• **Days remaining in month:** %d days\n\n" +
                "Keep daily spending under %s%.2f to stay on track!",
                cur, summary.getSafeToSpendToday(),
                cur, summary.getRemainingBudget(),
                cur, summary.getTotalSpent(), cur, summary.getTotalBudget(),
                summary.getDaysRemainingInMonth(),
                cur, summary.getSafeToSpendToday()
        );
        sendResponse(chatId, msg);
    }

    private void handleSummaryCommand(Long chatId, UserProfile user) {
        BudgetService.DashboardSummary summary = budgetService.getDashboardSummary(user, LocalDate.now());
        String cur = summary.getCurrency();
        StringBuilder sb = new StringBuilder("📊 **Monthly Budget Summary**\n\n");
        sb.append(String.format("Total Budget: %s%.2f | Spent: %s%.2f (%d%%)\n\n",
                cur, summary.getTotalBudget(), cur, summary.getTotalSpent(), summary.getPercentBudgetUsed()));

        for (BudgetService.CategoryBudgetStatus s : summary.getCategoryStatuses()) {
            String badge = s.getStatus().equals("EXCEEDED") ? "🚨" :
                           s.getStatus().equals("WARNING") ? "⚠️" : "✅";
            sb.append(String.format("%s %s %s: %s%.2f / %s%.2f (%d%%)\n",
                    badge, s.getIcon(), s.getCategoryName(), cur, s.getSpent(), cur, s.getLimit(), s.getPercentUsed()));
        }

        sendResponse(chatId, sb.toString());
    }

    private void handleBudgetCommand(Long chatId, String text, UserProfile user) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendResponse(chatId, "⚠️ Usage: `/budget <Category> <Amount>`\nExample: `/budget Food 2500`");
            return;
        }

        String catName = parts[1];
        try {
            double limit = Double.parseDouble(parts[2]);
            budgetService.setBudget(user, catName, limit, LocalDate.now());
            String cur = user.getCurrency() != null ? user.getCurrency() : "₹";
            sendResponse(chatId, String.format("✅ Budget updated! Set monthly limit for **%s** to **%s%.2f**.", catName, cur, limit));
        } catch (NumberFormatException e) {
            sendResponse(chatId, "⚠️ Please provide a valid numeric amount. Example: `/budget Transport 1000`");
        }
    }

    private void handleAffordCommand(Long chatId, String text, UserProfile user) {
        String[] parts = text.split("\\s+", 3);
        if (parts.length < 2) {
            sendResponse(chatId, "⚠️ Usage: `/afford <Amount> <Item>`\nExample: `/afford 1500 Shoes`");
            return;
        }

        try {
            double amount = Double.parseDouble(parts[1]);
            String item = parts.length > 2 ? parts[2] : "this item";

            BudgetService.AffordabilityResult result = budgetService.checkAffordability(user, amount, item);
            String cur = user.getCurrency() != null ? user.getCurrency() : "₹";

            String reply = String.format(
                    "🤔 **Pre-Purchase Simulator**\n\n" +
                    "%s\n\n" +
                    "📝 **Analysis:**\n%s\n\n" +
                    "• **Current Daily Allowance:** %s%.2f/day\n" +
                    "• **New Daily Allowance (if bought):** %s%.2f/day",
                    result.getHeadline(),
                    result.getDetails(),
                    cur, result.getCurrentSafeDaily(),
                    cur, result.getNewSafeDaily()
            );

            sendResponse(chatId, reply);
        } catch (NumberFormatException e) {
            sendResponse(chatId, "⚠️ Invalid amount. Example: `/afford 1200 Watch`");
        }
    }

    private void handleExpenseLogging(Long chatId, String text, UserProfile user) {
        ExpenseParserService.ParsedResult parsed = parserService.parse(text);

        if (parsed == null) {
            sendResponse(chatId, "❓ I didn't recognize that message.\nTry sending: `Coffee 25`, `add 5000 to my budget as salary`, or `/today`");
            return;
        }

        String cur = user.getCurrency() != null ? user.getCurrency() : "₹";

        // 1. Handle SET_BUDGET Intent (e.g. "add 5000 to my budget", "add 5000 to my remaining budget", "set budget food 3000")
        if (parsed.getIntent() == ExpenseParserService.Intent.SET_BUDGET) {
            if (parsed.getAmount() == null) {
                sendResponse(chatId, "⚠️ Please specify an amount for your budget. Example: `add 5000 to my budget` or `/budget Food 3000`");
                return;
            }
            if ("General".equalsIgnoreCase(parsed.getCategoryName()) ||
                    text.toLowerCase().contains("remaining") ||
                    text.toLowerCase().contains("ramaining") ||
                    text.toLowerCase().contains("add")) {
                budgetService.boostBudget(user, parsed.getAmount(), LocalDate.now());
            } else {
                budgetService.setBudget(user, parsed.getCategoryName(), parsed.getAmount(), LocalDate.now());
            }
            BudgetService.DashboardSummary summary = budgetService.getDashboardSummary(user, LocalDate.now());
            sendResponse(chatId, String.format(
                    "🎯 **Monthly Budget Updated!**\n\n" +
                    "• **Category:** %s\n" +
                    "• **Amount:** %s%.2f\n" +
                    "• **Total Monthly Budget:** %s%.2f\n" +
                    "• **Safe to Spend Today:** %s%.2f/day\n\n" +
                    "✅ *Your budget limit has been raised! This is NOT recorded as an expense.*",
                    parsed.getCategoryName(), cur, parsed.getAmount(), cur, summary.getTotalBudget(), cur, summary.getSafeToSpendToday()
            ));
            return;
        }

        // 2. Handle INCOME Intent (e.g. "add 5000 to my budget as salary", "salary 50000", "received 5000 as salary", "credited 2000")
        if (parsed.getIntent() == ExpenseParserService.Intent.INCOME) {
            if (parsed.getAmount() == null) {
                sendResponse(chatId, "⚠️ Please specify the income amount. Example: `Salary 50000`");
                return;
            }
            Expense income = new Expense(
                    parsed.getAmount(),
                    parsed.getDescription(),
                    parsed.getCategory(),
                    user,
                    LocalDate.now(),
                    "TELEGRAM",
                    "INCOME",
                    text
            );
            expenseRepository.save(income);

            // If user mentioned adding to budget, also boost their budget limit
            boolean alsoBoostBudget = text.toLowerCase().contains("budget") ||
                                      text.toLowerCase().contains("ramaining") ||
                                      text.toLowerCase().contains("remaining");
            if (alsoBoostBudget) {
                budgetService.boostBudget(user, parsed.getAmount(), LocalDate.now());
            }

            BudgetService.DashboardSummary summary = budgetService.getDashboardSummary(user, LocalDate.now());

            sendResponse(chatId, String.format(
                    "💵 **Income Credited: +%s%.2f**\n\n" +
                    "• **Source:** %s\n" +
                    "• **Category:** 💵 %s\n" +
                    (alsoBoostBudget ? String.format("• **Total Monthly Budget:** %s%.2f\n", cur, summary.getTotalBudget()) : "") +
                    "• **Safe to Spend Today:** %s%.2f/day\n\n" +
                    "🎉 *Recorded as Income! (This does NOT count towards your spending).* ",
                    cur, parsed.getAmount(), parsed.getDescription(),
                    (parsed.getCategory() != null ? parsed.getCategory().getName() : "Income & Salary"),
                    cur, summary.getSafeToSpendToday()
            ));
            return;
        }

        // 3. Handle AFFORD_CHECK Intent (e.g. "can i afford 500 for shoes?")
        if (parsed.getIntent() == ExpenseParserService.Intent.AFFORD_CHECK) {
            if (parsed.getAmount() == null) {
                sendResponse(chatId, "⚠️ Please specify an amount. Example: `can i afford 500 for shoes?`");
                return;
            }
            BudgetService.AffordabilityResult result = budgetService.checkAffordability(user, parsed.getAmount(), parsed.getDescription());
            String reply = String.format(
                    "🤔 **Pre-Purchase Simulator**\n\n" +
                    "%s\n\n" +
                    "📝 **Analysis:**\n%s\n\n" +
                    "• **Current Daily Allowance:** %s%.2f/day\n" +
                    "• **New Daily Allowance (if bought):** %s%.2f/day",
                    result.getHeadline(), result.getDetails(), cur, result.getCurrentSafeDaily(), cur, result.getNewSafeDaily()
            );
            sendResponse(chatId, reply);
            return;
        }

        // 4. Handle TODAY Intent
        if (parsed.getIntent() == ExpenseParserService.Intent.TODAY) {
            handleTodayCommand(chatId, user);
            return;
        }

        // 5. Handle SUMMARY Intent
        if (parsed.getIntent() == ExpenseParserService.Intent.SUMMARY) {
            handleSummaryCommand(chatId, user);
            return;
        }

        // 6. Handle EXPENSE Intent (Normal Spending)
        if (parsed.getAmount() == null) {
            sendResponse(chatId, "❓ I didn't recognize an expense amount in that message.\nTry sending: `Coffee 25` or `Dinner 150`");
            return;
        }

        Expense expense = new Expense(
                parsed.getAmount(),
                parsed.getDescription(),
                parsed.getCategory(),
                user,
                LocalDate.now(),
                "TELEGRAM",
                "EXPENSE",
                text
        );
        expenseRepository.save(expense);

        List<AlertLog> alerts = alertEngineService.evaluateExpense(expense);

        StringBuilder reply = new StringBuilder();
        String catIcon = expense.getCategory() != null ? expense.getCategory().getIcon() : "💰";
        String catName = expense.getCategory() != null ? expense.getCategory().getName() : "General";

        reply.append(String.format("✅ **Logged %s%.2f** for **%s** (%s %s)\n",
                cur, expense.getAmount(), expense.getDescription(), catIcon, catName));

        if (!alerts.isEmpty()) {
            reply.append("\n");
            for (AlertLog alert : alerts) {
                reply.append(alert.getTitle()).append("\n");
                reply.append(alert.getMessage()).append("\n");
                if (alert.getRemedySuggestion() != null) {
                    reply.append("👉 *Advice:* ").append(alert.getRemedySuggestion()).append("\n\n");
                }
            }
        }

        sendResponse(chatId, reply.toString());
    }

    private void sendResponse(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.enableMarkdown(true);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            // Fallback to plain text if Markdown parsing failed
            try {
                SendMessage plain = new SendMessage();
                plain.setChatId(chatId.toString());
                plain.setText(text.replaceAll("[*_`\\[\\]]", ""));
                plain.enableMarkdown(false);
                execute(plain);
            } catch (TelegramApiException ex) {
                System.err.println("Failed to send Telegram message: " + ex.getMessage());
            }
        }
    }
}
