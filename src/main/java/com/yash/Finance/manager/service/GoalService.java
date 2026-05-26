package com.yash.Finance.manager.service;

import com.yash.Finance.manager.dto.request.GoalRequest;
import com.yash.Finance.manager.dto.response.GoalResponse;
import com.yash.Finance.manager.entity.SavingsGoal;
import com.yash.Finance.manager.entity.User;
import com.yash.Finance.manager.exception.BadRequestException;
import com.yash.Finance.manager.exception.NotFoundException;
import com.yash.Finance.manager.repository.GoalRepository;
import com.yash.Finance.manager.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final TransactionRepository transactionRepository;
    private final AuthService authService;

    public GoalService(GoalRepository goalRepository,
                       TransactionRepository transactionRepository,
                       AuthService authService) {
        this.goalRepository = goalRepository;
        this.transactionRepository = transactionRepository;
        this.authService = authService;
    }

    @Transactional
    public GoalResponse create(GoalRequest request) {
        User user = authService.getCurrentUser();

        if (!request.getTargetDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Target date must be in the future");
        }

        SavingsGoal goal = new SavingsGoal();
        goal.setGoalName(request.getGoalName());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setTargetDate(request.getTargetDate());
        goal.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        goal.setUser(user);

        SavingsGoal saved = goalRepository.save(goal);
        return toResponse(saved, user);
    }

    public List<GoalResponse> getAll() {
        User user = authService.getCurrentUser();
        return goalRepository.findByUser(user).stream()
                .map(g -> toResponse(g, user))
                .collect(Collectors.toList());
    }

    public GoalResponse getById(Long id) {
        User user = authService.getCurrentUser();
        SavingsGoal goal = goalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Goal not found"));
        return toResponse(goal, user);
    }

    @Transactional
    public GoalResponse update(Long id, GoalRequest request) {
        User user = authService.getCurrentUser();

        SavingsGoal goal = goalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Goal not found"));

        if (request.getGoalName() != null) {
            goal.setGoalName(request.getGoalName());
        }
        if (request.getTargetAmount() != null) {
            goal.setTargetAmount(request.getTargetAmount());
        }
        if (request.getTargetDate() != null) {
            if (!request.getTargetDate().isAfter(LocalDate.now())) {
                throw new BadRequestException("Target date must be in the future");
            }
            goal.setTargetDate(request.getTargetDate());
        }
        if (request.getStartDate() != null) {
            goal.setStartDate(request.getStartDate());
        }

        SavingsGoal saved = goalRepository.save(goal);
        return toResponse(saved, user);
    }

    @Transactional
    public void delete(Long id) {
        User user = authService.getCurrentUser();

        SavingsGoal goal = goalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NotFoundException("Goal not found"));

        goalRepository.delete(goal);
    }

    private GoalResponse toResponse(SavingsGoal goal, User user) {
        BigDecimal net = transactionRepository.calculateNetSince(user, goal.getStartDate());
        if (net == null) {
            net = BigDecimal.ZERO;
        }

        BigDecimal target = goal.getTargetAmount();
        BigDecimal remaining = target.subtract(net);

        double progressPercentage = 0.0;
        if (target.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal pct = net.divide(target, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            progressPercentage = Math.round(pct.doubleValue() * 100.0) / 100.0;
        }

        GoalResponse response = new GoalResponse();
        response.setId(goal.getId());
        response.setGoalName(goal.getGoalName());
        response.setTargetAmount(target);
        response.setTargetDate(goal.getTargetDate());
        response.setStartDate(goal.getStartDate());
        response.setCurrentProgress(net);
        response.setRemainingAmount(remaining);
        response.setProgressPercentage(progressPercentage);
        return response;
    }
}
