package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String name;

    @Column(unique = true)
    private Long telegramChatId;

    @Column(unique = true)
    private String telegramLinkCode;

    private String currency = "₹"; // Default currency

    private Double monthlySavingsGoal = 0.0;

    private LocalDateTime createdAt = LocalDateTime.now();

    public UserProfile() {}

    public UserProfile(String username, String email, String password, String name, String currency) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.name = name != null ? name : username;
        this.currency = currency != null ? currency : "₹";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(Long telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getTelegramLinkCode() {
        return telegramLinkCode;
    }

    public void setTelegramLinkCode(String telegramLinkCode) {
        this.telegramLinkCode = telegramLinkCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getMonthlySavingsGoal() {
        return monthlySavingsGoal;
    }

    public void setMonthlySavingsGoal(Double monthlySavingsGoal) {
        this.monthlySavingsGoal = monthlySavingsGoal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
