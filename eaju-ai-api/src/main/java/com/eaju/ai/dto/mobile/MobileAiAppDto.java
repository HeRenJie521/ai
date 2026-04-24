package com.eaju.ai.dto.mobile;

public class MobileAiAppDto {

    private Long id;
    private String name;
    private String welcomeText;
    private String suggestions;
    private String modelDisplayName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWelcomeText() { return welcomeText; }
    public void setWelcomeText(String welcomeText) { this.welcomeText = welcomeText; }

    public String getSuggestions() { return suggestions; }
    public void setSuggestions(String suggestions) { this.suggestions = suggestions; }

    public String getModelDisplayName() { return modelDisplayName; }
    public void setModelDisplayName(String modelDisplayName) { this.modelDisplayName = modelDisplayName; }
}
