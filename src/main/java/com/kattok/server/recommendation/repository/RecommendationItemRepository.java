package com.kattok.server.recommendation.repository;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.recommendation.entity.RecommendationItem;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {

	List<RecommendationItem> findByRecommendationIdIn(Collection<Long> recommendationIds);

	List<RecommendationItem> findByRecommendationIdOrderByIdAsc(Long recommendationId);
}
