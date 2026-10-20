package com.expensetracker.config;

import com.expensetracker.bot.SmartExpenseBot;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserProfileRepository;
import com.expensetracker.service.AlertEngineService;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ExpenseParserService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfig {

    private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);

    @Value("${telegram.bot.token:YOUR_BOT_TOKEN_HERE}")
    private String botToken;

    private BotSession botSession;

    @Bean
    public SmartExpenseBot smartExpenseBot(ExpenseParserService parserService,
                                           AlertEngineService alertEngineService,
                                           BudgetService budgetService,
                                           AuthService authService,
                                           ExpenseRepository expenseRepository,
                                           UserProfileRepository userProfileRepository,
                                           CategoryRepository categoryRepository) {
        return new SmartExpenseBot(botToken, parserService, alertEngineService,
                budgetService, authService, expenseRepository, userProfileRepository, categoryRepository);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(SmartExpenseBot smartExpenseBot) {
        if (botToken == null || botToken.trim().isEmpty() || "YOUR_BOT_TOKEN_HERE".equals(botToken)) {
            logger.warn("⚠️ Telegram Bot Token not set in application.properties!");
            return null;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            this.botSession = botsApi.registerBot(smartExpenseBot);
            logger.info("🤖 Telegram Bot successfully registered and listening for updates!");
            return botsApi;
        } catch (TelegramApiException e) {
            logger.error("Error registering Telegram Bot: {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (botSession != null && botSession.isRunning()) {
            logger.info("Stopping Telegram Bot session...");
            try {
                botSession.stop();
            } catch (Exception e) {
                logger.warn("Error stopping Telegram Bot session: {}", e.getMessage());
            }
        }
    }
}
