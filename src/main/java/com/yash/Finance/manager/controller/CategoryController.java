package com.yash.Finance.manager.controller;

import com.yash.Finance.manager.dto.request.CategoryRequest;
import com.yash.Finance.manager.dto.response.CategoryResponse;
import com.yash.Finance.manager.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, List<CategoryResponse>>> getAll() {
        List<CategoryResponse> categories = categoryService.getAll();
        return ResponseEntity.ok(Map.of("categories", categories));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCustom(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCustom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> deleteCustom(@PathVariable String name) {
        categoryService.deleteCustom(name);
        return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
    }
}
