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

	/**
	 * @param ruleId 혜택 규칙 ID
	 * @param capGroupId 이 혜택에 직접 걸린 한도 ID. 상위 한도는 3-3의 구간별 {@code caps}에서 함께 준다.
	 */
	public record BenefitSummary(Long ruleId, String displayText, Long capGroupId, Long capAmount, Integer capCount) {
	}
}
