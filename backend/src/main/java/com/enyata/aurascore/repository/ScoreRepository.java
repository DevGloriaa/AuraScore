package com.enyata.aurascore.repository;

import com.enyata.aurascore.model.ScoreRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ScoreRepository extends MongoRepository<ScoreRecord, String> {
    Optional<ScoreRecord> findByTransactionReference(String transactionReference);
    Optional<ScoreRecord> findTopByCustomerReferenceOrderByCreatedAtDesc(String customerReference);
}

