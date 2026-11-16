package com.kattok.server.benefit.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.benefit.entity.CapPeriod;

/**
 * 카드 상품 한 장의 계산용 정보. 사용자와 상관없는 데이터라 사용자 카드 상태와 따로 읽는다.
 *
 * @param productId 카드 상품 id
 * @param calculable 데이터 상태가 '계산 가능'인지. 아니면 이 카드는 계산하지 않는다.
 * @param annualFee 연회비(원). 모르면 {@code null}이고, 동점 비교에서 가장 뒤로 둔다.
 * @param rules 검증된 혜택 규칙
 * @param tiers 실적 구간 id별 정의. 규칙이 요구하는 구간만 들어 있어도 된다.
 * @param caps 한도 그룹 id별 정의. 상위 한도도 포함한다.
 */
public record ProductRules(
		Long productId,
		boolean calculable,
		Long annualFee,
		List<Rule> rules,
		Map<Long, Tier> tiers,
		Map<Long, Cap> caps) {

	public ProductRules {
		rules = List.copyOf(rules);
		tiers = Map.copyOf(tiers);
		caps = Map.copyOf(caps);
	}

	/**
	 * 혜택 규칙 하나. 적용 대상은 결제처({@code merchantId}), 업종({@code categoryId}),
	 * 전 가맹점({@code conditions.allMerchants}) 중 하나다.
	 *
	 * @param requiredTierId 이 규칙이 적용되는 실적 구간. 구간마다 규칙을 따로 입력하므로 정확히 그 구간일 때만 적용한다.
	 *     실적 조건이 없으면 {@code null}
	 * @param fixedAmount 정액 혜택 금액(원)
	 * @param pointKrwRatio 포인트 1점의 원화 가치. 없으면 1점 = 1원
	 * @param minPaymentAmount 금액 구간 하한(원, 포함)
	 * @param maxEligibleTxnAmount 한 번 결제에서 혜택을 계산하는 최대 금액(원)
	 */
	public record Rule(
			Long id,
			Long merchantId,
			Long categoryId,
			Long requiredTierId,
			Long capGroupId,
			BenefitType type,
			BigDecimal rate,
			Long fixedAmount,
			BigDecimal pointKrwRatio,
			Long minPaymentAmount,
			Long maxEligibleTxnAmount,
			RuleConditions conditions,
			String displayText,
			boolean stackable) {

		/** 표시되지 않은 기존 규칙은 단독 적용한다. */
		public Rule(Long id, Long merchantId, Long categoryId, Long requiredTierId, Long capGroupId,
				BenefitType type, BigDecimal rate, Long fixedAmount, BigDecimal pointKrwRatio,
				Long minPaymentAmount, Long maxEligibleTxnAmount, RuleConditions conditions, String displayText) {
			this(id, merchantId, categoryId, requiredTierId, capGroupId, type, rate, fixedAmount, pointKrwRatio,
					minPaymentAmount, maxEligibleTxnAmount, conditions, displayText, false);
		}
	}

	/** 실적 구간. {@code level}이 낮을수록 낮은 구간이다. */
	public record Tier(Long id, int level, long minAmount) {
	}

	/**
	 * 한도 그룹. 금액 한도와 횟수 한도가 모두 없으면 한도가 없는 그룹이다.
	 *
	 * @param parentId 이 한도를 포함하는 상위 한도 그룹 id. 없으면 {@code null}
	 * @param description 화면에 보여줄 한도 설명 (카드 상태 조회 API에서 쓴다. 계산 엔진은 읽지 않는다)
	 * @param capPeriod 한도 갱신 주기 (카드 상태 조회 API에서 쓴다. 계산 엔진은 읽지 않는다)
	 */
	public record Cap(Long id, Long capAmount, Integer capCount, Long parentId, String description, CapPeriod capPeriod) {

		/** 화면 표시용 필드 없이 쓰던 기존 호출부(계산 엔진 테스트 등)를 위한 생성자. */
		public Cap(Long id, Long capAmount, Integer capCount, Long parentId) {
			this(id, capAmount, capCount, parentId, null, null);
		}

		boolean limitsAmount() {
			return capAmount != null;
		}

		boolean limitsCount() {
			return capCount != null;
		}
	}
}
