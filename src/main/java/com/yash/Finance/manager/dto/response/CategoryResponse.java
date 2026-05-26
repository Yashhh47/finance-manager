package com.yash.Finance.manager.dto.response;

public class CategoryResponse {
    private Long id;
    private String name;
    private String type;
    private boolean isCustom;

    public CategoryResponse() {}

    public CategoryResponse(Long id, String name, String type, boolean isCustom) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.isCustom = isCustom;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isCustom() { return isCustom; }
    public void setCustom(boolean custom) { isCustom = custom; }
}
