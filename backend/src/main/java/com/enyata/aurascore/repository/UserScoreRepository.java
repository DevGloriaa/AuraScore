package com.enyata.aurascore.repository;

import java.util.Optional;

import com.enyata.aurascore.model.UserScore;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserScoreRepository extends MongoRepository<UserScore, String> {
    Optional<UserScore> findByPhoneNumber(String phoneNumber);
}

