package com.enyata.aurascore.repository;

import com.enyata.aurascore.model.ScoreRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScoreRepository extends MongoRepository<ScoreRecord, String> {
}

