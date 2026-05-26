package com.yash.Finance.manager.service;

import com.yash.Finance.manager.dto.request.TransactionRequest;
import com.yash.Finance.manager.dto.response.TransactionResponse;
import com.yash.Finance.manager.entity.Category;
import com.yash.Finance.manager.entity.Transaction;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.exception.BadRequestException;
import com.yash.Finance.manager.exception.ForbiddenException;
import com.yash.Finance.manager.exception.NotFoundException;
import com.yash.Finance.manager.repository.CategoryRepository;
import com.yash.Finance.manager.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AuthService authService;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              AuthService authService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.authService = authService;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        User user = authService.getCurrentUser();

        if (request.getDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Transaction date cannot be in the future");
        }

        Category category = resolveCategory(request.getCategoryName(), user);

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setDate(request.getDate());
        transaction.setCategory(category);
        transaction.setDescription(request.getDescription());
        transaction.setUser(user);

        Transaction saved = transactionRepository.save(transaction);
        return toResponse(saved);
    }

    public List<TransactionResponse> getAll(LocalDate startDate, LocalDate endDate, Long categoryId, String categoryName) {
        User user = authService.getCurrentUser();

        Category category = null;
        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        } else if (categoryName != null && !categoryName.isBlank()) {
            category = resolveCategory(categoryName, user);
        }

        List<Transaction> transactions = transactionRepository.findFiltered(user, startDate, endDate, category);
        return transactions.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        User user = authService.getCurrentUser();

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Access denied to this transaction");
        }

        // date is immutable - never updated
        if (request.getAmount() != null) {
            transaction.setAmount(request.getAmount());
        }
        if (request.getCategoryName() != null) {
            Category category = resolveCategory(request.getCategoryName(), user);
            transaction.setCategory(category);
        }
        if (request.getDescription() != null) {
            transaction.setDescription(request.getDescription());
        }

        Transaction saved = transactionRepository.save(transaction);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        User user = authService.getCurrentUser();

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Access denied to this transaction");
        }

        transactionRepository.delete(transaction);
    }

    private Category resolveCategory(String categoryName, User user) {
        // First look in user custom categories
        return categoryRepository.findByNameAndUser(categoryName, user)
                // Then look in system categories
                .or(() -> categoryRepository.findByNameAndIsCustomFalse(categoryName))
                .orElseThrow(() -> new BadRequestException("Category not found: " + categoryName));
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAmount(),
                t.getDate(),
                t.getCategory().getName(),
                t.getCategory().getType().name(),
                t.getDescription()
        );
    }
}
