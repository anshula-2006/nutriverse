package com.nutriverse.backend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

public class ChatRequest {
    @NotBlank
    @Size(max = 4000)
    private String message;
    @Pattern(regexp = "USDA FoodData Central|Open Food Facts|ICMR-NIN IFCT 2017")
    private String source;
    @Pattern(regexp = "[A-Za-z0-9._-]{1,32}")
    private String sourceId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    @AssertTrue(message = "Source and sourceId must be supplied together")
    public boolean isSourceSelectionValid() { return (source == null) == (sourceId == null); }
}
