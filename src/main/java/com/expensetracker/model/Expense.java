package com.expensetracker.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double amount;

    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id")
    private UserProfile userProfile;

    @Column(name = "expense_date", nullable = false)
    private LocalDate date = LocalDate.now();

    private String source = "WEB"; // "TELEGRAM" or "WEB"

    @Column(nullable = false)
    private String type = "EXPENSE"; // "EXPENSE" or "INCOME"

    @Column(columnDefinition = "TEXT")
    private String rawText;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Expense() {}

    public Expense(Double amount, String description, Category category, UserProfile userProfile, LocalDate date, String source, String type, String rawText) {
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.userProfile = userProfile;
        this.date = date != null ? date : LocalDate.now();
        this.source = source != null ? source : "WEB";
        this.type = type != null ? type : "EXPENSE";
        this.rawText = rawText;
        this.createdAt = LocalDateTime.now();
    }

    public Expense(Double amount, String description, Category category, UserProfile userProfile, LocalDate date, String source, String rawText) {
        this(amount, description, category, userProfile, date, source, "EXPENSE", rawText);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type : "EXPENSE";
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
