package com.nutriverse.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "water_logs")
public class WaterLog {

    @Id
    private String id;
    private String userId;
    private Double amountLiters;
    private LocalDateTime loggedAt;

    public WaterLog() {
    }
    public WaterLog(String userId, Double amountLiters) {
        this.userId = userId;
        this.amountLiters = amountLiters;
        this.loggedAt = LocalDateTime.now();
    }

    public String getId() {return id;}
    public String getUserId() {return userId;}
    public void setUserId(String userId){this.userId = userId;}
    public Double getAmountLiters() {return amountLiters;}
    public void getAmountLiters(Double amountLiters){this.amountLiters = amountLiters;}
    public LocalDateTime getLoggedAt() {return loggedAt;}
    public void setLoggedAt(LocalDateTime loggedAt) {this.loggedAt = loggedAt;}
    

}
