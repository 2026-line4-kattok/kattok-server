package com.kattok.server.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.kattok.server.benefit.entity.CapPeriod;

/** 추천 생성·조회 응답(명세 1-1, 1-2). */
public record RecommendationResponse(
		Long id,
		Basis basis,
		IdName merchant,
		IdName category,
		long amount,
		String overallStatus,
		Long recommendedUserCardId,
		List<Long> tiedUserCardIds,
		Long differenceFromSecond,
		Long confirmedOnlyTopUserCardId,
		List<Item> items,
		List<ExcludedCard> excludedCards,
		Instant createdAt) {

	public enum Basis {
		MERCHANT,
		CATEGORY
	}

	public record IdName(Long id, String name) {
	}

	/** 비교한 카드 한 장. 순위 순서로 담는다. */
	public record Item(
			Long userCardId,
			String productName,
			String issuerName,
			String status,
			int rank,
			long expectedBenefit,
			Calculation calculation,
			List<AppliedRule> appliedRules,
			List<Assumption> assumptions,
			RemainingLimit remainingLimitAfter,
			String noBenefitReason,
			List<RemainingLimitProjection> remainingLimitsAfter) {
	}

	/** @param rate 할인·적립률(%). 예: 5% 할인이면 5 */
	public record Calculation(Long baseAmount, BigDecimal rate, Long fixedAmount, boolean capApplied) {
	}

	public record AppliedRule(
			Long ruleId, String description, long benefitAmount, Calculation calculation, List<Long> capGroupIds) {
		public AppliedRule {
			// 이전 스냅샷에는 연결 한도 ID가 없다. 현재 상품 규칙으로 추정해서 채우지 않는다.
			capGroupIds = capGroupIds == null ? List.of() : List.copyOf(capGroupIds);
		}
	}

	public record Assumption(String type, String message, boolean affectsRank, Long capGroupId) {
	}

	public record RemainingLimit(long amount, String source) {
	}

	/** 추천 시점의 예측이며 실제 카드 상태를 변경한 값이 아니다. */
	public record RemainingLimitProjection(
			Long capGroupId, String description, CapPeriod capPeriod, Long capAmount, Integer capCount,
			Long remainingLimitBefore, Long remainingLimitAmount, Integer remainingCountBefore, Integer remainingCount,
			String amountSource, String countSource) {
	}

	public record ExcludedCard(Long userCardId, String productName, Reason reason) {
	}

	public record Reason(String type, String message) {
	}
}
