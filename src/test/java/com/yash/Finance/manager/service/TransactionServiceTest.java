package com.yash.Finance.manager.service;

import com.yash.Finance.manager.dto.request.TransactionRequest;
import com.yash.Finance.manager.dto.response.TransactionResponse;
import com.yash.Finance.manager.entity.Category;
import com.yash.Finance.manager.entity.CategoryType;
import com.yash.Finance.manager.entity.Transaction;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.exception.BadRequestException;
import com.yash.Finance.manager.exception.ForbiddenException;
import com.yash.Finance.manager.exception.NotFoundException;
import com.yash.Finance.manager.repository.CategoryRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Category testCategory;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alice@test.com");

        testCategory = new Category();
        testCategory.setId(10L);
        testCategory.setName("Food");
        testCategory.setType(CategoryType.EXPENSE);
        testCategory.setCustom(false);

        testTransaction = new Transaction();
        testTransaction.setId(100L);
        testTransaction.setAmount(BigDecimal.valueOf(150.00));
        testTransaction.setDate(LocalDate.now());
        testTransaction.setCategory(testCategory);
        testTransaction.setUser(testUser);
        testTransaction.setDescription("Lunch");
    }

    @Test
    void create_Success() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(BigDecimal.valueOf(150.00));
        request.setDate(LocalDate.now());
        request.setCategoryName("Food");
        request.setDescription("Lunch");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByNameAndUser("Food", testUser)).thenReturn(Optional.empty());
        when(categoryRepository.findByNameAndIsCustomFalse("Food")).thenReturn(Optional.of(testCategory));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TransactionResponse response = transactionService.create(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Food", response.getCategoryName());
        assertEquals("EXPENSE", response.getCategoryType());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void create_FutureDate_ThrowsBadRequest() {
        TransactionRequest request = new TransactionRequest();
        request.setDate(LocalDate.now().plusDays(1));

        when(authService.getCurrentUser()).thenReturn(testUser);

        assertThrows(BadRequestException.class, () -> {
            transactionService.create(request);
        });
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void create_InvalidCategory_ThrowsBadRequest() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(BigDecimal.valueOf(100.0));
        request.setDate(LocalDate.now());
        request.setCategoryName("NonExistent");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByNameAndUser("NonExistent", testUser)).thenReturn(Optional.empty());
        when(categoryRepository.findByNameAndIsCustomFalse("NonExistent")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> {
            transactionService.create(request);
        });
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void getAll_NoFilters_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findFiltered(testUser, null, null, null))
                .thenReturn(List.of(testTransaction));

        List<TransactionResponse> result = transactionService.getAll(null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
        verify(transactionRepository, times(1)).findFiltered(testUser, null, null, null);
    }

    @Test
    void getAll_WithCategoryFilter_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(testCategory));
        when(transactionRepository.findFiltered(testUser, null, null, testCategory))
                .thenReturn(List.of(testTransaction));

        List<TransactionResponse> result = transactionService.getAll(null, null, 10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(categoryRepository, times(1)).findById(10L);
    }

    @Test
    void getAll_InvalidCategoryFilter_ThrowsNotFound() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            transactionService.getAll(null, null, 99L);
        });
        verify(transactionRepository, never()).findFiltered(any(), any(), any(), any());
    }

    @Test
    void update_Success() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(BigDecimal.valueOf(200.00));
        request.setDescription("Dinner");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(testTransaction));
        
        Transaction updatedTransaction = new Transaction();
        updatedTransaction.setId(100L);
        updatedTransaction.setAmount(BigDecimal.valueOf(200.00));
        updatedTransaction.setDescription("Dinner");
        updatedTransaction.setCategory(testCategory);
        updatedTransaction.setDate(testTransaction.getDate());
        
        when(transactionRepository.save(any(Transaction.class))).thenReturn(updatedTransaction);

        TransactionResponse response = transactionService.update(100L, request);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(200.00), response.getAmount());
        assertEquals("Dinner", response.getDescription());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void update_NotFound_ThrowsNotFound() {
        TransactionRequest request = new TransactionRequest();

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            transactionService.update(999L, request);
        });
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void update_WrongUser_ThrowsForbidden() {
        TransactionRequest request = new TransactionRequest();
        User anotherUser = new User();
        anotherUser.setId(2L);
        
        testTransaction.setUser(anotherUser); // Transaction belongs to someone else

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(testTransaction));

        assertThrows(ForbiddenException.class, () -> {
            transactionService.update(100L, request);
        });
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void delete_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(testTransaction));

        transactionService.delete(100L);

        verify(transactionRepository, times(1)).delete(testTransaction);
    }

    @Test
    void delete_NotFound_ThrowsNotFound() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            transactionService.delete(999L);
        });
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void delete_WrongUser_ThrowsForbidden() {
        User anotherUser = new User();
        anotherUser.setId(2L);
        testTransaction.setUser(anotherUser);

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(testTransaction));

        assertThrows(ForbiddenException.class, () -> {
            transactionService.delete(100L);
        });
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }
}
