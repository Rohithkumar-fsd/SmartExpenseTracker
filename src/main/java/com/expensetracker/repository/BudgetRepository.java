package com.expensetracker.repository;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByCategoryAndUserProfileAndMonthAndYear(Category category, UserProfile userProfile, Integer month, Integer year);

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.category WHERE b.userProfile = :userProfile AND b.month = :month AND b.year = :year")
    List<Budget> findByUserProfileAndMonthAndYear(@Param("userProfile") UserProfile userProfile, @Param("month") Integer month, @Param("year") Integer year);
}
