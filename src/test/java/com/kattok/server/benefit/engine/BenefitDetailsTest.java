package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kattok.server.benefit.engine.BenefitDetails.AssumptionType;
import com.kattok.server.benefit.engine.BenefitDetails.Calculation;
import com.kattok.server.benefit.engine.BenefitDetails.RemainingLimit;
import com.kattok.server.benefit.engine.CardBenefitResult.LimitSource;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

class BenefitDetailsTest {

	private static final long CAFE = 1L;
	private static final LocalDateTime WEDNESDAY_2PM = LocalDateTime.of(2026, 11, 18, 14, 0);

	private final BenefitCalculator calculator = new BenefitCalculator();

	@Test
	@DisplayName("정률 혜택의 계산식은 계산 금액과 %로 나오고, 한도에 걸리면 capApplied가 true다")
	void rateCalculation() {
		Rule rule = rateRule(10L, null, 20000L);

		Calculation notCapped = BenefitDetails.calculation(rule, 30000, 2000);
		Calculation capped = BenefitDetails.calculation(rule, 30000, 1500);

		assertThat(notCapped.baseAmount()).isEqualTo(20000);
		assertThat(notCapped.ratePercent()).isEqualByComparingTo("10");
		assertThat(notCapped.fixedAmount()).isNull();
		assertThat(notCapped.capApplied()).isFalse();
		assertThat(capped.capApplied()).isTrue();
	}

	@Test
	@DisplayName("정액 혜택의 계산식은 정액만 나온다")
	void fixedCalculation() {
		Rule rule = new Rule(1L, null, CAFE, null, 10L, BenefitType.DISCOUNT_FIXED, null, 1000L, null, null, null,
				RuleConditions.NONE, "카페 1천원 할인");

		Calculation calculation = BenefitDetails.calculation(rule, 5000, 1000);

		assertThat(calculation.baseAmount()).isNull();
		assertThat(calculation.ratePercent()).isNull();
		assertThat(calculation.fixedAmount()).isEqualTo(1000);
	}

	@Test
	@DisplayName("결제 후 남은 한도는 하위·상위 한도 중 가장 작은 값에서 혜택을 뺀 값이고, 모르는 한도가 있으면 ASSUMED다")
	void remainingLimitAfter() {
		ProductRules product = product(
				List.of(rateRule(10L, null, null)),
				Map.of(10L, new Cap(10L, 5000L, null, 20L), 20L, new Cap(20L, 10000L, null, null)),
				Map.of());
		UserCardSnapshot known = snapshot(PerformanceState.unknown(), Map.of(
				10L, new CapState(3000L, null, ConfirmationSource.USER_CONFIRMED),
				20L, new CapState(8000L, null, ConfirmationSource.SYSTEM_ESTIMATED)));
		UserCardSnapshot partlyUnknown = snapshot(PerformanceState.unknown(), Map.of(
				10L, new CapState(3000L, null, ConfirmationSource.USER_CONFIRMED)));

		RemainingLimit fromKnown = BenefitDetails.remainingLimitAfter(product.rules().getFirst(), product, known, 1000);
		RemainingLimit fromPartly = BenefitDetails.remainingLimitAfter(product.rules().getFirst(), product, partlyUnknown, 1000);

		assertThat(fromKnown).isEqualTo(new RemainingLimit(2000, LimitSource.ESTIMATED));
		assertThat(fromPartly).isEqualTo(new RemainingLimit(2000, LimitSource.ASSUMED));
	}

	@Test
	@DisplayName("금액 한도가 없는 혜택이면 결제 후 남은 한도는 null이다")
	void noAmountCap() {
		ProductRules product = product(List.of(rateRule(10L, null, null)),
				Map.of(10L, new Cap(10L, null, null, null)), Map.of());

		assertThat(BenefitDetails.remainingLimitAfter(
				product.rules().getFirst(), product, snapshot(PerformanceState.unknown(), Map.of()), 500)).isNull();
	}

	@Test
	@DisplayName("실적과 남은 한도·횟수를 모르면 가정 종류가 셋 다 나온다")
	void assumptionTypes() {
		Rule rule = new Rule(1L, null, CAFE, 7L, 10L, BenefitType.DISCOUNT_RATE, new BigDecimal("0.10"), null, null,
				null, null, RuleConditions.NONE, "카페 10% 할인");
		ProductRules product = product(List.of(rule),
				Map.of(10L, new Cap(10L, 5000L, 3, null)), Map.of(7L, new Tier(7L, 1, 300000)));
		UserCardSnapshot card = snapshot(PerformanceState.unknown(), Map.of());

		CardBenefitResult result = calculator.calculate(product, card, PaymentContext.inCategory(CAFE, 10000, WEDNESDAY_2PM));

		assertThat(BenefitDetails.assumptionTypes(result, rule, product, card)).containsExactlyInAnyOrder(
				AssumptionType.PERFORMANCE_TIER, AssumptionType.REMAINING_LIMIT, AssumptionType.REMAINING_COUNT);
	}

	private static Rule rateRule(long capGroupId, Long requiredTierId, Long maxEligible) {
		return new Rule(1L, null, CAFE, requiredTierId, capGroupId, BenefitType.DISCOUNT_RATE, new BigDecimal("0.10"),
				null, null, null, maxEligible, RuleConditions.NONE, "카페 10% 할인");
	}

	private static ProductRules product(List<Rule> rules, Map<Long, Cap> caps, Map<Long, Tier> tiers) {
		return new ProductRules(1L, true, 10000L, rules, tiers, caps);
	}

	private static UserCardSnapshot snapshot(PerformanceState performance, Map<Long, CapState> caps) {
		return new UserCardSnapshot(1L, 1L, 1, performance, caps);
	}
}
