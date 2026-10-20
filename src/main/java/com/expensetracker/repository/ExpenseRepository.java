package com.expensetracker.repository;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserProfileOrderByDateDescCreatedAtDesc(UserProfile userProfile);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"category"})
    List<Expense> findTop15ByUserProfileOrderByDateDescCreatedAtDesc(UserProfile userProfile);

    List<Expense> findByUserProfileAndCategoryAndDateBetween(UserProfile userProfile, Category category, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(e.amount), 0.0) FROM Expense e WHERE e.userProfile = :userProfile AND e.type = 'EXPENSE' AND e.date BETWEEN :startDate AND :endDate")
    Double sumAmountByUserProfileAndDateBetween(@Param("userProfile") UserProfile userProfile, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(e.amount), 0.0) FROM Expense e WHERE e.userProfile = :userProfile AND e.category = :category AND e.type = 'EXPENSE' AND e.date BETWEEN :startDate AND :endDate")
    Double sumAmountByUserProfileAndCategoryAndDateBetween(@Param("userProfile") UserProfile userProfile, @Param("category") Category category, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(e.amount), 0.0) FROM Expense e WHERE e.userProfile = :userProfile AND e.type = 'INCOME' AND e.date BETWEEN :startDate AND :endDate")
    Double sumIncomeByUserProfileAndDateBetween(@Param("userProfile") UserProfile userProfile, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT e.category.id, COALESCE(SUM(e.amount), 0.0) FROM Expense e WHERE e.userProfile = :userProfile AND e.type = 'EXPENSE' AND e.date BETWEEN :startDate AND :endDate GROUP BY e.category.id")
    List<Object[]> sumAmountByUserProfileGroupedByCategoryAndDateBetween(@Param("userProfile") UserProfile userProfile, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
