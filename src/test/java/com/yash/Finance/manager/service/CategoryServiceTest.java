package com.yash.Finance.manager.service;

import com.yash.Finance.manager.dto.request.CategoryRequest;
import com.yash.Finance.manager.dto.response.CategoryResponse;
import com.yash.Finance.manager.entity.Category;
import com.yash.Finance.manager.entity.CategoryType;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.exception.BadRequestException;
import com.yash.Finance.manager.exception.ConflictException;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private CategoryService categoryService;

    private User testUser;
    private Category systemCategory;
    private Category customCategory;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alice@test.com");

        systemCategory = new Category();
        systemCategory.setId(10L);
        systemCategory.setName("Food");
        systemCategory.setType(CategoryType.EXPENSE);
        systemCategory.setCustom(false);

        customCategory = new Category();
        customCategory.setId(20L);
        customCategory.setName("Freelance");
        customCategory.setType(CategoryType.INCOME);
        customCategory.setCustom(true);
        customCategory.setUser(testUser);
    }

    @Test
    void seedDefaultCategories_Empty_SeedsDefaults() {
        when(categoryRepository.findByIsCustomFalse()).thenReturn(Collections.emptyList());

        categoryService.seedDefaultCategories();

        verify(categoryRepository, atLeast(7)).save(any(Category.class));
    }

    @Test
    void seedDefaultCategories_NotEmpty_DoesNotSeed() {
        when(categoryRepository.findByIsCustomFalse()).thenReturn(List.of(systemCategory));

        categoryService.seedDefaultCategories();

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void getAll_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByIsCustomFalse()).thenReturn(List.of(systemCategory));
        when(categoryRepository.findByUserAndIsCustomTrue(testUser)).thenReturn(List.of(customCategory));

        List<CategoryResponse> result = categoryService.getAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Food", result.get(0).getName());
        assertFalse(result.get(0).isCustom());
        assertEquals("Freelance", result.get(1).getName());
        assertTrue(result.get(1).isCustom());
        verify(categoryRepository, times(1)).findByIsCustomFalse();
        verify(categoryRepository, times(1)).findByUserAndIsCustomTrue(testUser);
    }

    @Test
    void createCustom_Success() {
        CategoryRequest request = new CategoryRequest();
        request.setName("SideJob");
        request.setType("INCOME");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.existsByNameAndUser("SideJob", testUser)).thenReturn(false);
        when(categoryRepository.findByNameAndIsCustomFalse("SideJob")).thenReturn(Optional.empty());

        Category saved = new Category();
        saved.setId(30L);
        saved.setName("SideJob");
        saved.setType(CategoryType.INCOME);
        saved.setCustom(true);
        saved.setUser(testUser);

        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = categoryService.createCustom(request);

        assertNotNull(response);
        assertEquals("SideJob", response.getName());
        assertTrue(response.isCustom());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void createCustom_DuplicateCustom_ThrowsConflict() {
        CategoryRequest request = new CategoryRequest();
        request.setName("Freelance");
        request.setType("INCOME");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.existsByNameAndUser("Freelance", testUser)).thenReturn(true);

        assertThrows(ConflictException.class, () -> {
            categoryService.createCustom(request);
        });
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCustom_DuplicateSystem_ThrowsConflict() {
        CategoryRequest request = new CategoryRequest();
        request.setName("Food");
        request.setType("EXPENSE");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.existsByNameAndUser("Food", testUser)).thenReturn(false);
        when(categoryRepository.findByNameAndIsCustomFalse("Food")).thenReturn(Optional.of(systemCategory));

        assertThrows(ConflictException.class, () -> {
            categoryService.createCustom(request);
        });
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCustom_InvalidType_ThrowsBadRequest() {
        CategoryRequest request = new CategoryRequest();
        request.setName("SideJob");
        request.setType("INVALID_TYPE");

        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.existsByNameAndUser("SideJob", testUser)).thenReturn(false);
        when(categoryRepository.findByNameAndIsCustomFalse("SideJob")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> {
            categoryService.createCustom(request);
        });
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deleteCustom_Success() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByNameAndIsCustomFalse("Freelance")).thenReturn(Optional.empty());
        when(categoryRepository.findByNameAndUser("Freelance", testUser)).thenReturn(Optional.of(customCategory));
        when(transactionRepository.existsByCategory(customCategory)).thenReturn(false);

        categoryService.deleteCustom("Freelance");

        verify(categoryRepository, times(1)).delete(customCategory);
    }

    @Test
    void deleteCustom_SystemCategoryName_ThrowsForbidden() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByNameAndIsCustomFalse("Food")).thenReturn(Optional.of(systemCategory));

        assertThrows(ForbiddenException.class, () -> {
            categoryService.deleteCustom("Food");
        });
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteCustom_NotFound_ThrowsNotFound() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByNameAndIsCustomFalse("NonExistent")).thenReturn(Optional.empty());
        when(categoryRepository.findByNameAndUser("NonExistent", testUser)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            categoryService.deleteCustom("NonExistent");
        });
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteCustom_InUse_ThrowsBadRequest() {
        when(authService.getCurrentUser()).thenReturn(testUser);
        when(categoryRepository.findByNameAndIsCustomFalse("Freelance")).thenReturn(Optional.empty());
        when(categoryRepository.findByNameAndUser("Freelance", testUser)).thenReturn(Optional.of(customCategory));
        when(transactionRepository.existsByCategory(customCategory)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> {
            categoryService.deleteCustom("Freelance");
        });
        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
