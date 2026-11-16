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
	 * @param usedInRecommendation 지금은 항상 {@code true}다. 검증된 혜택만 데이터에 있어서(미검증 규칙은
	 *     아예 행이 없음) verification_status로는 구분이 안 생긴다. 나중에 실적 구간처럼 "지금 적용 대상인지"
	 *     기준이 정해지면 그때 바꾼다.
	 */
	public record Benefit(Long ruleId, String description, boolean usedInRecommendation, Long capGroupId) {
	}
}
