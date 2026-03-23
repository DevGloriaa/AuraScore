package com.enyata.aurascore.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_scores")
public class UserScore {

    @Id
    private String id;

    private String phoneNumber;
    private int creditScore;
    private String aiInsights;
    private String walletAddress;
    private LocalDateTime createdAt;
}

