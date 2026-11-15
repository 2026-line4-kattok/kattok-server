package com.kattok.server.recommendation.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.recommendation.entity.RecommendationItem;
import com.kattok.server.recommendation.repository.RecommendationItemRepository;
import com.kattok.server.recommendation.repository.RecommendationRepository;

import lombok.RequiredArgsConstructor;

/** 결제 도메인은 현재 혜택 규칙 대신 저장한 추천 결과만 읽는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRecommendationReader {
	private final RecommendationRepository recommendations;
	private final RecommendationItemRepository items;
	private final RecommendationResponseMapper mapper;

	public Map<Long, RecommendationResponse> read(Long userId, Collection<Long> ids) {
		if (ids.isEmpty()) return Map.of();
		var owned = recommendations.findByIdInAndUserId(ids, userId);
		var byRecommendation = items.findByRecommendationIdIn(owned.stream().map(r -> r.getId()).toList())
				.stream().collect(Collectors.groupingBy(item -> item.getRecommendation().getId()));
		return owned.stream().collect(Collectors.toMap(r -> r.getId(), r -> mapper.toResponse(
				r, byRecommendation.getOrDefault(r.getId(), List.<RecommendationItem>of()))));
	}
}
