package com.yash.Finance.manager.controller;

import com.yash.Finance.manager.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/monthly/{year}/{month}")
    public ResponseEntity<Map<String, Object>> getMonthly(@PathVariable int year,
                                                           @PathVariable int month) {
        Map<String, Object> report = reportService.getMonthly(year, month);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/yearly/{year}")
    public ResponseEntity<Map<String, Object>> getYearly(@PathVariable int year) {
        Map<String, Object> report = reportService.getYearly(year);
        return ResponseEntity.ok(report);
    }
}
