package com.yash.Finance.manager.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionResponse {
    private Long id;
    private BigDecimal amount;
    private LocalDate date;
    private String categoryName;
    private String categoryType;
    private String description;

    public TransactionResponse() {}

    public TransactionResponse(Long id, BigDecimal amount, LocalDate date,
                               String categoryName, String categoryType, String description) {
        this.id = id;
        this.amount = amount;
        this.date = date;
        this.categoryName = categoryName;
        this.categoryType = categoryType;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategory() { return categoryName; }

    public String getCategoryType() { return categoryType; }
    public void setCategoryType(String categoryType) { this.categoryType = categoryType; }

    public String getType() { return categoryType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
