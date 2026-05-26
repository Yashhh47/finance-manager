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
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AuthService authService;

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository,
                           AuthService authService) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.authService = authService;
    }

    @PostConstruct
    public void seedDefaultCategories() {
        if (categoryRepository.findByIsCustomFalse().isEmpty()) {
            seedCategory("Salary", CategoryType.INCOME);
            seedCategory("Food", CategoryType.EXPENSE);
            seedCategory("Rent", CategoryType.EXPENSE);
            seedCategory("Transportation", CategoryType.EXPENSE);
            seedCategory("Entertainment", CategoryType.EXPENSE);
            seedCategory("Healthcare", CategoryType.EXPENSE);
            seedCategory("Utilities", CategoryType.EXPENSE);
        }
    }

    private void seedCategory(String name, CategoryType type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setCustom(false);
        category.setUser(null);
        categoryRepository.save(category);
    }

    public List<CategoryResponse> getAll() {
        User user = authService.getCurrentUser();
        List<Category> categories = new ArrayList<>();
        categories.addAll(categoryRepository.findByIsCustomFalse());
        categories.addAll(categoryRepository.findByUserAndIsCustomTrue(user));
        return categories.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCustom(CategoryRequest request) {
        User user = authService.getCurrentUser();

        boolean existsForUser = categoryRepository.existsByNameAndUser(request.getName(), user);
        boolean existsAsSystem = categoryRepository.findByNameAndIsCustomFalse(request.getName()).isPresent();

        if (existsForUser || existsAsSystem) {
            throw new ConflictException("Category with this name already exists");
        }

        CategoryType type;
        try {
            type = CategoryType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid category type. Must be INCOME or EXPENSE");
        }

        Category category = new Category();
        category.setName(request.getName());
        category.setType(type);
        category.setCustom(true);
        category.setUser(user);
        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    public void deleteCustom(String name) {
        User user = authService.getCurrentUser();

        // Check system category first
        categoryRepository.findByNameAndIsCustomFalse(name).ifPresent(c -> {
            throw new ForbiddenException("Cannot delete a system default category");
        });

        Category category = categoryRepository.findByNameAndUser(name, user)
                .orElseThrow(() -> new NotFoundException("Category not found: " + name));

        if (!category.isCustom()) {
            throw new ForbiddenException("Cannot delete a system default category");
        }

        if (transactionRepository.existsByCategory(category)) {
            throw new BadRequestException("Cannot delete category: it is linked to existing transactions");
        }

        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType().name(),
                category.isCustom()
        );
    }
}
