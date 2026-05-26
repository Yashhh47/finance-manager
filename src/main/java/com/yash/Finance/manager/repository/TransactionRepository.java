package com.yash.Finance.manager.repository;

import com.yash.Finance.manager.entity.Category;
import com.yash.Finance.manager.entity.Transaction;
import com.yash.Finance.manager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.user = :user " +
           "AND (:start IS NULL OR t.date >= :start) " +
           "AND (:end IS NULL OR t.date <= :end) " +
           "AND (:category IS NULL OR t.category = :category) " +
           "ORDER BY t.date DESC")
    List<Transaction> findFiltered(@Param("user") User user,
                                   @Param("start") LocalDate start,
                                   @Param("end") LocalDate end,
                                   @Param("category") Category category);

    @Query("SELECT COALESCE(SUM(CASE WHEN t.category.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) " +
           "FROM Transaction t WHERE t.user = :user AND t.date >= :startDate")
    BigDecimal calculateNetSince(@Param("user") User user, @Param("startDate") LocalDate startDate);

    List<Transaction> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);

    boolean existsByCategory(Category category);
}
