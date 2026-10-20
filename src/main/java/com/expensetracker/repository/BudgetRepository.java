package com.expensetracker.repository;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByCategoryAndUserProfileAndMonthAndYear(Category category, UserProfile userProfile, Integer month, Integer year);
    List<Budget> findByUserProfileAndMonthAndYear(UserProfile userProfile, Integer month, Integer year);
}
