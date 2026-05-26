package com.yash.Finance.manager.service;

import com.yash.Finance.manager.entity.Category;
import com.yash.Finance.manager.entity.CategoryType;
import com.yash.Finance.manager.entity.Transaction;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ReportService reportService;

    private User testUser;
    private Category incomeCategory;
    private Category expenseCategory;
    private Transaction incomeTx;
    private Transaction expenseTx;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alice@test.com");

        incomeCategory = new Category();
        incomeCategory.setName("Salary");
        incomeCategory.setType(CategoryType.INCOME);
        incomeCategory.setCustom(false);

        expenseCategory = new Category();
        expenseCategory.setName("Rent");
        expenseCategory.setType(CategoryType.EXPENSE);
        expenseCategory.setCustom(false);

        incomeTx = new Transaction();
        incomeTx.setId(100L);
        incomeTx.setAmount(BigDecimal.valueOf(5000.00));
        incomeTx.setDate(LocalDate.of(2026, 5, 1));
        incomeTx.setCategory(incomeCategory);
        incomeTx.setUser(testUser);

        expenseTx = new Transaction();
        expenseTx.setId(101L);
        expenseTx.setAmount(BigDecimal.valueOf(1200.00));
        expenseTx.setDate(LocalDate.of(2026, 5, 15));
        expenseTx.setCategory(expenseCategory);
        expenseTx.setUser(testUser);
    }

    @Test
    void getMonthly_SuccessWithTransactions() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findByUserAndDateBetween(eq(testUser), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(incomeTx, expenseTx));

        Map<String, Object> report = reportService.getMonthly(2026, 5);

        assertNotNull(report);
        assertEquals(2026, report.get("year"));
        assertEquals(5, report.get("month"));
        assertEquals(BigDecimal.valueOf(5000.00), report.get("totalIncome"));
        assertEquals(BigDecimal.valueOf(1200.00), report.get("totalExpenses"));
        assertEquals(BigDecimal.valueOf(3800.00), report.get("netSavings"));

        Map<String, BigDecimal> incomeMap = (Map<String, BigDecimal>) report.get("incomeByCategory");
        assertEquals(BigDecimal.valueOf(5000.00), incomeMap.get("Salary"));

        Map<String, BigDecimal> expenseMap = (Map<String, BigDecimal>) report.get("expenseByCategory");
        assertEquals(BigDecimal.valueOf(1200.00), expenseMap.get("Rent"));
    }

    @Test
    void getMonthly_EmptyMonth_ReturnsZeros() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findByUserAndDateBetween(eq(testUser), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> report = reportService.getMonthly(2026, 1);

        assertNotNull(report);
        assertEquals(BigDecimal.ZERO, report.get("totalIncome"));
        assertEquals(BigDecimal.ZERO, report.get("totalExpenses"));
        assertEquals(BigDecimal.ZERO, report.get("netSavings"));
        assertTrue(((Map<?, ?>) report.get("incomeByCategory")).isEmpty());
        assertTrue(((Map<?, ?>) report.get("expenseByCategory")).isEmpty());
    }

    @Test
    void getYearly_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        
        // Mock transaction repository to return transactions only in May (month 5), empty elsewhere
        for (int m = 1; m <= 12; m++) {
            if (m == 5) {
                when(transactionRepository.findByUserAndDateBetween(eq(testUser), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 5, 31))))
                        .thenReturn(List.of(incomeTx, expenseTx));
            } else {
                LocalDate start = LocalDate.of(2026, m, 1);
                LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
                when(transactionRepository.findByUserAndDateBetween(eq(testUser), eq(start), eq(end)))
                        .thenReturn(Collections.emptyList());
            }
        }

        Map<String, Object> report = reportService.getYearly(2026);

        assertNotNull(report);
        assertEquals(2026, report.get("year"));
        assertEquals(BigDecimal.valueOf(5000.00), report.get("totalIncome"));
        assertEquals(BigDecimal.valueOf(1200.00), report.get("totalExpenses"));
        assertEquals(BigDecimal.valueOf(3800.00), report.get("netSavings"));

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) report.get("monthlyBreakdown");
        assertEquals(12, breakdown.size());
        
        // Check May breakdown
        Map<String, Object> mayData = breakdown.get(4); // 0-indexed, so May is index 4
        assertEquals(5, mayData.get("month"));
        assertEquals(BigDecimal.valueOf(5000.00), mayData.get("totalIncome"));
        assertEquals(BigDecimal.valueOf(1200.00), mayData.get("totalExpenses"));
        assertEquals(BigDecimal.valueOf(3800.00), mayData.get("netSavings"));
    }
}
