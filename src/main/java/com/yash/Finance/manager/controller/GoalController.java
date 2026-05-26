package com.yash.Finance.manager.controller;

import com.yash.Finance.manager.dto.request.GoalRequest;
import com.yash.Finance.manager.dto.response.GoalResponse;
import com.yash.Finance.manager.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        GoalResponse response = goalService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, List<GoalResponse>>> getAll() {
        List<GoalResponse> goals = goalService.getAll();
        return ResponseEntity.ok(Map.of("goals", goals));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getById(@PathVariable Long id) {
        GoalResponse response = goalService.getById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@PathVariable Long id,
                                                @RequestBody GoalRequest request) {
        GoalResponse response = goalService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        goalService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Goal deleted successfully"));
    }
}
