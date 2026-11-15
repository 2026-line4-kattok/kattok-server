package com.kattok.server.recommendation.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.global.ApiException;
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

	public RecommendationResponse lockForPayment(Long userId, Long id) {
		var rec = recommendations.findWithLockByIdAndUserId(id, userId)
				.orElseThrow(() -> ApiException.notFound("RECOMMENDATION_NOT_FOUND", "존재하지 않는 추천입니다."));
		return mapper.toResponse(rec, items.findByRecommendationIdOrderByIdAsc(id));
	}

	/** 오래된 스냅샷의 누락과 실제 무한도([])를 원본 JSON 키로 구분한다. */
	public boolean accountingComplete(Long recommendationId, Long cardId) {
		var item = items.findByRecommendationIdOrderByIdAsc(recommendationId).stream()
				.filter(i -> i.getUserCard().getId().equals(cardId)).findFirst().orElseThrow();
		var raw = item.getAppliedRules();
		if (!(raw.get("remainingLimitsAfter") instanceof List<?>) || !(raw.get("rules") instanceof List<?> rules) || rules.isEmpty()) return false;
		return rules.stream().allMatch(rule -> rule instanceof Map<?,?> map && map.get("calculation") != null
				&& map.get("capGroupIds") instanceof List<?>);
	}

	public Set<Long> uncappedCapGroupIds(Long recommendationId, Long cardId) {
		var item = items.findByRecommendationIdOrderByIdAsc(recommendationId).stream()
				.filter(i -> i.getUserCard().getId().equals(cardId)).findFirst().orElseThrow();
		Object raw = item.getAppliedRules().get("uncappedCapGroupIds");
		if (raw == null) return Set.of();
		if (!(raw instanceof List<?> list)) throw ApiException.conflict("PAYMENT_SNAPSHOT_INCOMPLETE", "차감 정보가 올바르지 않습니다.");
		Set<Long> ids = new HashSet<>();
		for (Object id : list) {
			if (!(id instanceof Number n) || !ids.add(n.longValue()))
				throw ApiException.conflict("PAYMENT_SNAPSHOT_INCOMPLETE", "차감 정보가 올바르지 않습니다.");
		}
		return Set.copyOf(ids);
	}

	public Map<Long, RecommendationResponse> read(Long userId, Collection<Long> ids) {
		if (ids.isEmpty()) return Map.of();
		var owned = recommendations.findByIdInAndUserId(ids, userId);
		var byRecommendation = items.findByRecommendationIdIn(owned.stream().map(r -> r.getId()).toList())
				.stream().collect(Collectors.groupingBy(item -> item.getRecommendation().getId()));
		return owned.stream().collect(Collectors.toMap(r -> r.getId(), r -> mapper.toResponse(
				r, byRecommendation.getOrDefault(r.getId(), List.<RecommendationItem>of()))));
	}
}
