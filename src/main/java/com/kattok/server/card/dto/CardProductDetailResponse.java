package com.kattok.server.card.dto;

import java.util.List;

import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.PerformanceType;

/**
 * 카드 상품 상세. 실적 구간을 고를 때 "이 구간을 고르면 뭘 받는지" 바로 보이도록, 한도 목록을
 * 평평하게 나열하지 않고 혜택을 구간 안에 중첩한다. 실적 조건이 없는 혜택은 {@code alwaysOnBenefits}에 둔다.
 */
public record CardProductDetailResponse(
		Long id,
		String name,
		CardProductResponse.IssuerSummary issuer,
		CardType cardType,
		Long annualFee,
		DataStatus dataStatus,
		PerformanceType performanceType,
		boolean owned,
		List<BenefitSummary> alwaysOnBenefits,
		List<PerformanceTierDetail> performanceTiers) {

	public record PerformanceTierDetail(
			Long id, int level, long minAmount, String label, List<BenefitSummary> benefits) {
	}

	public record BenefitSummary(String displayText, Long capAmount, Integer capCount) {
	}
}
