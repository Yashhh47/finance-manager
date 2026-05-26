package com.yash.Finance.manager.service;

import com.yash.Finance.manager.entity.CategoryType;
import com.yash.Finance.manager.entity.Transaction;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final AuthService authService;

    public ReportService(TransactionRepository transactionRepository,
                         AuthService authService) {
        this.transactionRepository = transactionRepository;
        this.authService = authService;
    }

    public Map<String, Object> getMonthly(int year, int month) {
        User user = authService.getCurrentUser();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Transaction> transactions = transactionRepository.findByUserAndDateBetween(user, start, end);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        Map<String, BigDecimal> incomeByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();

        for (Transaction t : transactions) {
            String catName = t.getCategory().getName();
            BigDecimal amount = t.getAmount();
            if (t.getCategory().getType() == CategoryType.INCOME) {
                totalIncome = totalIncome.add(amount);
                incomeByCategory.merge(catName, amount, BigDecimal::add);
            } else {
                totalExpenses = totalExpenses.add(amount);
                expenseByCategory.merge(catName, amount, BigDecimal::add);
            }
        }

        BigDecimal netSavings = totalIncome.subtract(totalExpenses);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("totalIncome", totalIncome);
        result.put("totalExpenses", totalExpenses);
        result.put("netSavings", netSavings);
        result.put("incomeByCategory", incomeByCategory);
        result.put("expenseByCategory", expenseByCategory);
        return result;
    }

    public Map<String, Object> getYearly(int year) {
        User user = authService.getCurrentUser();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        Map<String, BigDecimal> incomeByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        List<Map<String, Object>> monthlyBreakdown = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();

            List<Transaction> transactions = transactionRepository.findByUserAndDateBetween(user, start, end);

            BigDecimal monthIncome = BigDecimal.ZERO;
            BigDecimal monthExpenses = BigDecimal.ZERO;

            for (Transaction t : transactions) {
                String catName = t.getCategory().getName();
                BigDecimal amount = t.getAmount();
                if (t.getCategory().getType() == CategoryType.INCOME) {
                    monthIncome = monthIncome.add(amount);
                    incomeByCategory.merge(catName, amount, BigDecimal::add);
                } else {
                    monthExpenses = monthExpenses.add(amount);
                    expenseByCategory.merge(catName, amount, BigDecimal::add);
                }
            }

            totalIncome = totalIncome.add(monthIncome);
            totalExpenses = totalExpenses.add(monthExpenses);

            Map<String, Object> monthData = new LinkedHashMap<>();
            monthData.put("month", month);
            monthData.put("totalIncome", monthIncome);
            monthData.put("totalExpenses", monthExpenses);
            monthData.put("netSavings", monthIncome.subtract(monthExpenses));
            monthlyBreakdown.add(monthData);
        }

        BigDecimal netSavings = totalIncome.subtract(totalExpenses);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("totalIncome", totalIncome);
        result.put("totalExpenses", totalExpenses);
        result.put("netSavings", netSavings);
        result.put("incomeByCategory", incomeByCategory);
        result.put("expenseByCategory", expenseByCategory);
        result.put("monthlyBreakdown", monthlyBreakdown);
        return result;
    }
}
