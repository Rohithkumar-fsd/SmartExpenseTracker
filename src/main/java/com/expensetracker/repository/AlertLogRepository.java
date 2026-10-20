package com.expensetracker.repository;

import com.expensetracker.model.AlertLog;
import com.expensetracker.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {
    List<AlertLog> findTop15ByUserProfileOrderByCreatedAtDesc(UserProfile userProfile);
    List<AlertLog> findByUserProfileAndIsReadFalseOrderByCreatedAtDesc(UserProfile userProfile);
}
