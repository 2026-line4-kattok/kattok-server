package com.kattok.server.benefit.engine;

import java.util.List;

/**
 * 보유 카드 한 장의 계산 결과.
 *
 * @param userCardId 보유 카드 id
 * @param status 결과 상태
 * @param expectedBenefit 예상 혜택(원, 1원 미만 절사). 혜택이 없거나 계산하지 못하면 0
 * @param appliedRuleId 적용한 혜택 규칙 id. 없으면 {@code null}
 * @param appliedRuleText 적용한 혜택의 화면 문구
 * @param assumptions 결과를 내려고 가정한 것. 하나라도 있으면 조건부다.
 * @param limitSource 결과를 제한한 남은 한도의 출처
 * @param noBenefitReason 혜택이 없는 이유. 혜택이 있으면 {@code null}
 * @param requiredPerformance 적용한 혜택에 필요한 실적(원). 실적 조건이 없으면 0. 동점 비교에 쓴다.
 * @param annualFee 연회비(원). 모르면 {@code null}. 동점 비교에 쓴다.
 * @param registrationOrder 등록 순서. 동점 비교의 마지막 기준이다.
 */
public record CardBenefitResult(
		Long userCardId,
		Status status,
		long expectedBenefit,
		Long appliedRuleId,
		String appliedRuleText,
		List<Assumption> assumptions,
		LimitSource limitSource,
		NoBenefitReason noBenefitReason,
		long requiredPerformance,
		Long annualFee,
		int registrationOrder) {

	public CardBenefitResult {
		assumptions = List.copyOf(assumptions);
	}

	public boolean calculable() {
		return status != Status.NOT_CALCULABLE;
	}

	public enum Status {
		CONFIRMED,
		CONDITIONAL,
		NO_BENEFIT,
		NOT_CALCULABLE
	}

	public enum Assumption {
		/** 실적을 몰라서 혜택을 받는 가장 낮은 구간으로 가정함 */
		PERFORMANCE_ASSUMED_LOWEST_TIER,
		/** 남은 한도·횟수를 몰라서 해당 기간 전체 한도로 가정함 */
		LIMIT_ASSUMED_FULL
	}

	public enum LimitSource {
		/** 결과에 영향을 준 한도가 없음 */
		NONE,
		USER_INPUT,
		ESTIMATED,
		ASSUMED
	}

	public enum NoBenefitReason {
		/** 이 결제처·업종에 해당하는 혜택이 없음 */
		NO_MATCHING_RULE,
		/** 금액·요일·시간대 조건이 맞지 않음 */
		CONDITION_NOT_MET,
		/** 필요한 실적 구간이 아님 */
		PERFORMANCE_NOT_MET,
		/** 남은 한도나 횟수가 없음 */
		LIMIT_EXHAUSTED
	}
}
