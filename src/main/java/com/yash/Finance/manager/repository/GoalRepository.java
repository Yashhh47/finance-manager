package com.yash.Finance.manager.repository;

import com.yash.Finance.manager.entity.SavingsGoal;
import com.yash.Finance.manager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUser(User user);
    Optional<SavingsGoal> findByIdAndUser(Long id, User user);
}
