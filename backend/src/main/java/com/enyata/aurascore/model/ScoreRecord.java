package com.enyata.aurascore.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "score_records")
public class ScoreRecord {

    @Id
    private String id;

    private String customerReference;
    private JsonNode aiAnalysis;
    private LocalDateTime createdAt;
}

