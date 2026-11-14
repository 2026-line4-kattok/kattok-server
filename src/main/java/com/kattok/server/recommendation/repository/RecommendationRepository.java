package com.kattok.server.recommendation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.recommendation.entity.Recommendation;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	/** 다른 사용자의 추천은 없는 것으로 본다. */
	Optional<Recommendation> findByIdAndUserId(Long id, Long userId);
}
