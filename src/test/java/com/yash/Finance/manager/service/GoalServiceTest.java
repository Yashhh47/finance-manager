package com.yash.Finance.manager.service;

import com.yash.Finance.manager.dto.request.GoalRequest;
import com.yash.Finance.manager.dto.response.GoalResponse;
import com.yash.Finance.manager.entity.SavingsGoal;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.exception.BadRequestException;
import com.yash.Finance.manager.exception.NotFoundException;
import com.yash.Finance.manager.repository.GoalRepository;
import com.yash.Finance.manager.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private GoalService goalService;

    private User testUser;
    private SavingsGoal testGoal;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alice@test.com");

        testGoal = new SavingsGoal();
        testGoal.setId(100L);
        testGoal.setGoalName("New Car");
        testGoal.setTargetAmount(BigDecimal.valueOf(10000.00));
        testGoal.setStartDate(LocalDate.now().minusMonths(1));
        testGoal.setTargetDate(LocalDate.now().plusYears(1));
        testGoal.setUser(testUser);
    }

    @Test
    void create_Success() {
        GoalRequest request = new GoalRequest();
        request.setGoalName("New Car");
        request.setTargetAmount(BigDecimal.valueOf(10000.00));
        request.setTargetDate(LocalDate.now().plusYears(1));
        request.setStartDate(LocalDate.now().minusMonths(1));

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.calculateNetSince(testUser, request.getStartDate()))
                .thenReturn(BigDecimal.valueOf(4000.00));
        when(goalRepository.save(any(SavingsGoal.class))).thenReturn(testGoal);

        GoalResponse response = goalService.create(request);

        assertNotNull(response);
        assertEquals("New Car", response.getGoalName());
        assertEquals(BigDecimal.valueOf(10000.00), response.getTargetAmount());
        assertEquals(BigDecimal.valueOf(4000.00), response.getCurrentProgress());
        assertEquals(BigDecimal.valueOf(6000.00), response.getRemainingAmount());
        assertEquals(40.0, response.getProgressPercentage());
        verify(goalRepository, times(1)).save(any(SavingsGoal.class));
    }

    @Test
    void create_PastTargetDate_ThrowsBadRequest() {
        GoalRequest request = new GoalRequest();
        request.setTargetDate(LocalDate.now().minusDays(1));

        when(authService.getCurrentUser()).thenReturn(testUser);

        assertThrows(BadRequestException.class, () -> {
            goalService.create(request);
        });
        verify(goalRepository, never()).save(any(SavingsGoal.class));
    }

    @Test
    void getAll_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByUser(testUser)).thenReturn(List.of(testGoal));
        when(transactionRepository.calculateNetSince(testUser, testGoal.getStartDate()))
                .thenReturn(BigDecimal.valueOf(2500.00));

        List<GoalResponse> result = goalService.getAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("New Car", result.get(0).getGoalName());
        assertEquals(25.0, result.get(0).getProgressPercentage());
        verify(goalRepository, times(1)).findByUser(testUser);
    }

    @Test
    void getById_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(100L, testUser)).thenReturn(Optional.of(testGoal));
        when(transactionRepository.calculateNetSince(testUser, testGoal.getStartDate()))
                .thenReturn(BigDecimal.valueOf(2500.00));

        GoalResponse response = goalService.getById(100L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        verify(goalRepository, times(1)).findByIdAndUser(100L, testUser);
    }

    @Test
    void getById_NotFound_ThrowsNotFound() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(999L, testUser)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            goalService.getById(999L);
        });
    }

    @Test
    void update_Success() {
        GoalRequest request = new GoalRequest();
        request.setGoalName("New Electric Car");
        request.setTargetAmount(BigDecimal.valueOf(12000.00));

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(100L, testUser)).thenReturn(Optional.of(testGoal));
        when(transactionRepository.calculateNetSince(testUser, testGoal.getStartDate()))
                .thenReturn(BigDecimal.valueOf(3000.00));

        SavingsGoal updatedGoal = new SavingsGoal();
        updatedGoal.setId(100L);
        updatedGoal.setGoalName("New Electric Car");
        updatedGoal.setTargetAmount(BigDecimal.valueOf(12000.00));
        updatedGoal.setStartDate(testGoal.getStartDate());
        updatedGoal.setTargetDate(testGoal.getTargetDate());
        updatedGoal.setUser(testUser);

        when(goalRepository.save(any(SavingsGoal.class))).thenReturn(updatedGoal);

        GoalResponse response = goalService.update(100L, request);

        assertNotNull(response);
        assertEquals("New Electric Car", response.getGoalName());
        assertEquals(BigDecimal.valueOf(12000.00), response.getTargetAmount());
        assertEquals(25.0, response.getProgressPercentage());
        verify(goalRepository, times(1)).save(any(SavingsGoal.class));
    }

    @Test
    void update_PastTargetDate_ThrowsBadRequest() {
        GoalRequest request = new GoalRequest();
        request.setTargetDate(LocalDate.now().minusDays(1));

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(100L, testUser)).thenReturn(Optional.of(testGoal));

        assertThrows(BadRequestException.class, () -> {
            goalService.update(100L, request);
        });
        verify(goalRepository, never()).save(any(SavingsGoal.class));
    }

    @Test
    void update_NotFound_ThrowsNotFound() {
        GoalRequest request = new GoalRequest();

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(999L, testUser)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            goalService.update(999L, request);
        });
    }

    @Test
    void delete_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(100L, testUser)).thenReturn(Optional.of(testGoal));

        goalService.delete(100L);

        verify(goalRepository, times(1)).delete(testGoal);
    }

    @Test
    void delete_NotFound_ThrowsNotFound() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(999L, testUser)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            goalService.delete(999L);
        });
        verify(goalRepository, never()).delete(any(SavingsGoal.class));
    }

    @Test
    void progressCalculation_NoTransactions_ReturnsZero() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(goalRepository.findByIdAndUser(100L, testUser)).thenReturn(Optional.of(testGoal));
        when(transactionRepository.calculateNetSince(testUser, testGoal.getStartDate())).thenReturn(null);

        GoalResponse response = goalService.getById(100L);

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getCurrentProgress());
        assertEquals(BigDecimal.valueOf(10000.00), response.getRemainingAmount());
        assertEquals(0.0, response.getProgressPercentage());
    }
}
