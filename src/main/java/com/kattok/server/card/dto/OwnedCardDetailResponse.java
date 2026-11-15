package com.kattok.server.card.dto;

import java.util.List;

import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.payment.dto.PaymentResponses;

/** 보유 카드 상세(3-2). */
public record OwnedCardDetailResponse(
		Long id,
		ProductDetail product,
		PerformanceDetail performance,
		List<Benefit> benefits,
		List<UserCardStateResponse.CapStatus> caps,
		PaymentResponses.SavedBenefit savedBenefit) {

	public record ProductDetail(
			Long id, String name, String issuerName, CardType cardType, Long annualFee,
			PerformanceType performanceType) {
	}

	/**
	 * 지난달 실적(이번 달 혜택 구간을 정하는 기준).
	 *
	 * @param baseMonth 실적 기준 월(지난달, 예: {@code 2026-10})
	 */
	public record PerformanceDetail(
			PerformanceStatus status,
			UserCardStateResponse.PerformanceTierOption tier,
			List<CardProductDetailResponse.PerformanceTierDetail> options,
			String baseMonth) {
	}

	/**
	 * 오늘 유효한 혜택 규칙 전체. 미검증 규칙도 포함한다.
	 *
	 * @param usedInRecommendation 추천 계산에 실제로 쓰이는 규칙인지(검증된 규칙이면서 카드가 비교 대상). {@code false}면 화면에서
	 *     회색 "추천 미반영"으로 보여 준다. 사용자 실적·한도로 이번 결제에 적용되는지와는 별개다.
	 */
	public record Benefit(Long ruleId, String description, boolean usedInRecommendation, Long capGroupId) {
	}
}
