package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kattok.server.benefit.engine.CardBenefitResult.Assumption;
import com.kattok.server.benefit.engine.CardBenefitResult.LimitSource;
import com.kattok.server.benefit.engine.CardBenefitResult.NoBenefitReason;
import com.kattok.server.benefit.engine.CardBenefitResult.Status;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

/** 정답 케이스 밖의 계산 규칙을 DB 없이 확인한다. */
class BenefitCalculatorTest {

	private static final long CAFE = 1L;
	private static final long CONVENIENCE = 2L;
	private static final long TAXI = 3L;
	private static final long STARBUCKS = 100L;
	private static final LocalDateTime WEDNESDAY_2PM = LocalDateTime.of(2026, 11, 18, 14, 0);

	private final BenefitCalculator calculator = new BenefitCalculator();

	@Test
	@DisplayName("상위 한도가 하위 한도보다 적게 남으면 상위 한도까지만 적용한다")
	void parentCapLimitsBenefit() {
		// 알파벳카드S PDF: 커피전문점 영역 월 5천원(하위) + 커피·편의점·대중교통 통합 월 1만원(상위)
		Map<Long, Cap> caps = Map.of(
				10L, new Cap(10L, 5000L, null, 20L),
				20L, new Cap(20L, 10000L, null, null));
		ProductRules product = product(List.of(categoryRule(1L, CAFE, 10L, "0.10")), caps);

		UserCardSnapshot card = noTier(snapshot(null, Map.of(
				10L, new CapState(5000L, null, ConfirmationSource.USER_CONFIRMED),
				20L, new CapState(700L, null, ConfirmationSource.SYSTEM_ESTIMATED))));

		CardBenefitResult result = calculator.calculate(product, card, PaymentContext.inCategory(CAFE, 20000, WEDNESDAY_2PM));

		assertThat(result.expectedBenefit()).isEqualTo(700);
		assertThat(result.status()).isEqualTo(Status.CONFIRMED);
		assertThat(result.limitSource()).isEqualTo(LimitSource.ESTIMATED);
	}

	@Test
	@DisplayName("하위 한도가 상위 한도보다 적게 남으면 하위 한도까지만 적용한다")
	void childCapLimitsBenefit() {
		Map<Long, Cap> caps = Map.of(
				10L, new Cap(10L, 5000L, null, 20L),
				20L, new Cap(20L, 10000L, null, null));
		ProductRules product = product(List.of(categoryRule(1L, CAFE, 10L, "0.10")), caps);
		UserCardSnapshot card = noTier(snapshot(null, Map.of(
				10L, new CapState(400L, null, ConfirmationSource.USER_CONFIRMED),
				20L, new CapState(10000L, null, ConfirmationSource.USER_CONFIRMED))));

		CardBenefitResult result = calculator.calculate(product, card, PaymentContext.inCategory(CAFE, 20000, WEDNESDAY_2PM));

		assertThat(result.expectedBenefit()).isEqualTo(400);
		assertThat(result.limitSource()).isEqualTo(LimitSource.USER_INPUT);
	}

	@Test
	@DisplayName("남은 한도를 모르면 전체 한도로 가정하고 조건부로 표시한다")
	void unknownLimitIsConditional() {
		ProductRules product = product(List.of(categoryRule(1L, CAFE, 10L, "0.10")),
				Map.of(10L, new Cap(10L, 5000L, null, null)));

		CardBenefitResult result = calculator.calculate(product, noTier(snapshot(null, Map.of())),
				PaymentContext.inCategory(CAFE, 10000, WEDNESDAY_2PM));

		assertThat(result.expectedBenefit()).isEqualTo(1000);
		assertThat(result.status()).isEqualTo(Status.CONDITIONAL);
		assertThat(result.assumptions()).containsExactly(Assumption.LIMIT_ASSUMED_FULL);
		assertThat(result.limitSource()).isEqualTo(LimitSource.ASSUMED);
	}

	@Test
	@DisplayName("남은 횟수가 0이면 혜택이 없다")
	void exhaustedCountGivesNoBenefit() {
		ProductRules product = product(List.of(categoryRule(1L, CONVENIENCE, 10L, "0.10")),
				Map.of(10L, new Cap(10L, null, 5, null)));
		UserCardSnapshot card = noTier(snapshot(null,
				Map.of(10L, new CapState(null, 0, ConfirmationSource.USER_CONFIRMED))));

		CardBenefitResult result = calculator.calculate(product, card,
				PaymentContext.inCategory(CONVENIENCE, 10000, WEDNESDAY_2PM));

		assertThat(result.status()).isEqualTo(Status.NO_BENEFIT);
		assertThat(result.noBenefitReason()).isEqualTo(NoBenefitReason.LIMIT_EXHAUSTED);
	}

	@Test
	@DisplayName("실적을 못 채웠으면 실적 조건이 있는 혜택은 받지 못한다")
	void belowPerformanceGetsNoTierBenefit() {
		Rule tierRule = new Rule(1L, null, CAFE, 7L, 10L, BenefitType.DISCOUNT_RATE, new BigDecimal("0.10"),
				null, null, null, null, RuleConditions.NONE, "커피 10%");
		ProductRules product = new ProductRules(1L, true, 10000L, List.of(tierRule),
				Map.of(7L, new Tier(7L, 1, 300000)), Map.of(10L, new Cap(10L, null, null, null)));

		CardBenefitResult result = calculator.calculate(product, snapshot(PerformanceState.below(), Map.of()),
				PaymentContext.inCategory(CAFE, 10000, WEDNESDAY_2PM));

		assertThat(result.status()).isEqualTo(Status.NO_BENEFIT);
		assertThat(result.noBenefitReason()).isEqualTo(NoBenefitReason.PERFORMANCE_NOT_MET);
	}

	@Test
	@DisplayName("자정을 넘는 시간대는 시작 시각 이후와 끝 시각 이전에 적용한다")
	void timeRangeCrossingMidnight() {
		RuleConditions night = RuleConditions.from(Map.of("timeRange", Map.of("from", "21:00", "to", "09:00")));
		Rule rule = new Rule(1L, null, TAXI, null, 10L, BenefitType.DISCOUNT_RATE, new BigDecimal("0.10"),
				null, null, null, 10000L, night, "택시 10%(Night)");
		ProductRules product = product(List.of(rule), Map.of(10L, new Cap(10L, null, null, null)));
		UserCardSnapshot card = noTier(snapshot(null, Map.of()));

		assertThat(benefitAt(product, card, LocalDateTime.of(2026, 11, 18, 21, 0))).isEqualTo(1000);
		assertThat(benefitAt(product, card, LocalDateTime.of(2026, 11, 19, 8, 59))).isEqualTo(1000);
		assertThat(benefitAt(product, card, LocalDateTime.of(2026, 11, 19, 9, 0))).isZero();
		assertThat(benefitAt(product, card, LocalDateTime.of(2026, 11, 18, 20, 59))).isZero();
	}

	@Test
	@DisplayName("혜택은 중복되지 않아 카드 안에서 가장 큰 혜택 하나만 적용한다")
	void picksLargestRuleOnly() {
		RuleConditions all = new RuleConditions(true, Set.of(), Set.of(), Set.of(), null, null);
		Rule allMerchants = new Rule(1L, null, null, null, 10L, BenefitType.POINT_RATE, new BigDecimal("0.005"),
				null, BigDecimal.ONE, null, null, all, "전 가맹점 0.5%");
		Rule cafe = categoryRule(2L, CAFE, 10L, "0.02");
		ProductRules product = product(List.of(allMerchants, cafe), Map.of(10L, new Cap(10L, null, null, null)));

		CardBenefitResult result = calculator.calculate(product, noTier(snapshot(null, Map.of())),
				PaymentContext.inCategory(CAFE, 10000, WEDNESDAY_2PM));

		assertThat(result.expectedBenefit()).isEqualTo(200);
		assertThat(result.appliedRuleId()).isEqualTo(2L);
	}

	@Test
	@DisplayName("1원 미만은 버린다")
	void truncatesBelowOneWon() {
		RuleConditions all = new RuleConditions(true, Set.of(), Set.of(), Set.of(), null, null);
		Rule rule = new Rule(1L, null, null, null, 10L, BenefitType.DISCOUNT_RATE, new BigDecimal("0.007"),
				null, null, null, null, all, "0.7%");
		ProductRules product = product(List.of(rule), Map.of(10L, new Cap(10L, null, null, null)));

		assertThat(benefitAt(product, noTier(snapshot(null, Map.of())), WEDNESDAY_2PM, 1999)).isEqualTo(13);
	}

	@Test
	@DisplayName("업종만 고르면 결제처 한정 혜택은 적용하지 않는다")
	void categoryOnlyIgnoresMerchantRule() {
		Rule starbucks = new Rule(1L, STARBUCKS, null, null, 10L, BenefitType.DISCOUNT_RATE,
				new BigDecimal("0.05"), null, null, null, null, RuleConditions.NONE, "스타벅스 5%");
		ProductRules product = product(List.of(starbucks), Map.of(10L, new Cap(10L, null, null, null)));
		UserCardSnapshot card = noTier(snapshot(null, Map.of()));

		CardBenefitResult byCategory = calculator.calculate(product, card,
				PaymentContext.inCategory(CAFE, 10000, WEDNESDAY_2PM));
		CardBenefitResult byMerchant = calculator.calculate(product, card,
				PaymentContext.atMerchant(STARBUCKS, CAFE, 10000, WEDNESDAY_2PM));

		assertThat(byCategory.noBenefitReason()).isEqualTo(NoBenefitReason.NO_MATCHING_RULE);
		assertThat(byMerchant.expectedBenefit()).isEqualTo(500);
	}

	@Test
	@DisplayName("데이터가 계산 가능 상태가 아니면 계산하지 않는다")
	void notCalculableProduct() {
		ProductRules product = new ProductRules(1L, false, null, List.of(), Map.of(), Map.of());

		CardBenefitResult result = calculator.calculate(product, noTier(snapshot(null, Map.of())),
				PaymentContext.inCategory(CAFE, 10000, WEDNESDAY_2PM));

		assertThat(result.status()).isEqualTo(Status.NOT_CALCULABLE);
	}

	private long benefitAt(ProductRules product, UserCardSnapshot card, LocalDateTime at) {
		return benefitAt(product, card, at, 10000);
	}

	private long benefitAt(ProductRules product, UserCardSnapshot card, LocalDateTime at, long amount) {
		Long categoryId = product.rules().getFirst().categoryId();
		return calculator.calculate(product, card,
				PaymentContext.inCategory(categoryId == null ? CAFE : categoryId, amount, at)).expectedBenefit();
	}

	private static Rule categoryRule(long id, long categoryId, long capGroupId, String rate) {
		return new Rule(id, null, categoryId, null, capGroupId, BenefitType.DISCOUNT_RATE, new BigDecimal(rate),
				null, null, null, null, RuleConditions.NONE, "업종 할인");
	}

	private static ProductRules product(List<Rule> rules, Map<Long, Cap> caps) {
		return new ProductRules(1L, true, 10000L, rules, Map.of(), caps);
	}

	private static UserCardSnapshot snapshot(PerformanceState performance, Map<Long, CapState> caps) {
		return new UserCardSnapshot(1L, 1L, 1, performance == null ? PerformanceState.unknown() : performance,
				new HashMap<>(caps));
	}

	/** 실적 조건이 없는 카드: 실적 상태는 의미가 없으므로 UNKNOWN으로 둔다. */
	private static UserCardSnapshot noTier(UserCardSnapshot card) {
		return new UserCardSnapshot(card.userCardId(), card.productId(), card.registrationOrder(),
				PerformanceState.unknown(), card.caps());
	}
}
