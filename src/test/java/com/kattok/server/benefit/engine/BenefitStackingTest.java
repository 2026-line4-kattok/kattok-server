package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

class BenefitStackingTest {
	private final BenefitCalculator calculator = new BenefitCalculator();
	private static final PaymentContext PAYMENT = PaymentContext.inCategory(1, 10000,
			LocalDateTime.of(2026, 11, 16, 12, 0));

	@Test void legacyRulesStillChooseOne() {
		var result = calculate(List.of(rule(1, "0.01", 10, false), rule(2, "0.02", 10, false)), unlimited(), Map.of());
		assertThat(result.expectedBenefit()).isEqualTo(200);
		assertThat(result.appliedBenefits()).extracting(CardBenefitResult.AppliedBenefit::ruleId).containsExactly(2L);
	}

	@Test void onlyExplicitRulesAreCombinedAndLegacyAlternativeStaysSeparate() {
		var rules = List.of(rule(1, "0.01", 10, true), rule(2, "0.02", 10, true), rule(3, "0.025", 10, false));
		var result = calculate(rules, unlimited(), Map.of());
		assertThat(result.expectedBenefit()).isEqualTo(300);
		assertThat(result.appliedBenefits()).extracting(CardBenefitResult.AppliedBenefit::benefitAmount).containsExactly(200L, 100L);
		assertThat(result.appliedRuleId()).isEqualTo(2L);
		assertThat(result.status()).isEqualTo(CardBenefitResult.Status.CONFIRMED);
		assertThat(calculate(List.of(rules.get(0), rule(4, "0.04", 10, false)), unlimited(), Map.of()).expectedBenefit()).isEqualTo(400);
	}

	@Test void sharedAmountIsConsumedOnceAndSharedCountOncePerTransaction() {
		var rules = List.of(rule(1, "0.01", 10, true), rule(2, "0.02", 10, true));
		var caps = Map.of(10L, cap(10, 1000L, 2, null));
		var card = card(Map.of(10L, new CapState(250L, 2, ConfirmationSource.USER_CONFIRMED)));
		var product = product(rules, caps);
		var result = calculator.calculate(product, card, PAYMENT);
		assertThat(result.expectedBenefit()).isEqualTo(250);
		assertThat(result.appliedBenefits()).extracting(CardBenefitResult.AppliedBenefit::benefitAmount).containsExactly(200L, 50L);
		var p = BenefitDetails.remainingLimitsAfter(result, product, card).getFirst();
		assertThat(p.remainingLimitBefore()).isEqualTo(250);
		assertThat(p.remainingLimitAmount()).isZero();
		assertThat(p.remainingCount()).isEqualTo(1);
	}

	@Test void childCapsConsumeOnlyTheirOwnBenefitAndParentConsumesTotal() {
		var caps = Map.of(10L, cap(10, 100L, null, 30L), 20L, cap(20, 200L, null, 30L),
				30L, cap(30, 250L, 2, null));
		var card = card(Map.of(10L, new CapState(100L, null, ConfirmationSource.USER_CONFIRMED),
				20L, new CapState(200L, null, ConfirmationSource.USER_CONFIRMED),
				30L, new CapState(250L, 2, ConfirmationSource.USER_CONFIRMED)));
		var product = product(List.of(rule(1, "0.02", 10, true), rule(2, "0.02", 20, true)), caps);
		var result = calculator.calculate(product, card, PAYMENT);
		var projected = BenefitDetails.remainingLimitsAfter(result, product, card).stream()
				.collect(java.util.stream.Collectors.toMap(BenefitDetails.CapProjection::capGroupId, x -> x));
		assertThat(result.expectedBenefit()).isEqualTo(250);
		assertThat(projected.get(10L).remainingLimitAmount()).isEqualTo(50);
		assertThat(projected.get(20L).remainingLimitAmount()).isZero();
		assertThat(projected.get(30L).remainingLimitAmount()).isZero();
		assertThat(projected.get(30L).remainingCount()).isEqualTo(1);
	}

	@Test void unknownSharedCapKeepsAssumptionAndDeduplicatesQuestion() {
		var product = product(List.of(rule(1, "0.01", 10, true), rule(2, "0.02", 10, true)),
				Map.of(10L, cap(10, 1000L, 2, null)));
		var card = card(Map.of());
		var result = calculator.calculate(product, card, PAYMENT);
		assertThat(result.expectedBenefit()).isEqualTo(300);
		assertThat(result.status()).isEqualTo(CardBenefitResult.Status.CONDITIONAL);
		var p = BenefitDetails.remainingLimitsAfter(result, product, card).getFirst();
		assertThat(p.remainingLimitAmount()).isEqualTo(700);
		assertThat(p.remainingCount()).isEqualTo(1);
		assertThat(p.amountSource()).isEqualTo(CardBenefitResult.LimitSource.ASSUMED);
		assertThat(BenefitDetails.remainingLimitAfter(result, product, card).amount()).isEqualTo(700);
		assertThat(BenefitDetails.assumptionTargets(result, product, card)).containsExactly(
				new BenefitDetails.AssumptionTarget(BenefitDetails.AssumptionType.REMAINING_LIMIT, 10L),
				new BenefitDetails.AssumptionTarget(BenefitDetails.AssumptionType.REMAINING_COUNT, 10L));
	}

	@Test void fullCountOfZeroCannotGiveBenefitEvenWhenRemainingIsUnknown() {
		var result = calculate(List.of(rule(1, "0.01", 10, true), rule(2, "0.02", 10, true)),
				Map.of(10L, cap(10, null, 0, null)), Map.of());
		assertThat(result.expectedBenefit()).isZero();
		assertThat(result.noBenefitReason()).isEqualTo(CardBenefitResult.NoBenefitReason.LIMIT_EXHAUSTED);
	}

	@Test void stackedCapAssumptionCanChangeRecommendationAndConfirmedOnlyTop() {
		var first = product(List.of(rule(1, "0.01", 10, true), rule(2, "0.02", 10, true)),
				Map.of(10L, cap(10, 1000L, null, null)));
		var second = product(List.of(rule(3, "0.025", 10, false)), unlimited());
		var cards = List.of(new BenefitEngine.CardInput(card(Map.of()), first),
				new BenefitEngine.CardInput(new UserCardSnapshot(2L, 1L, 2, PerformanceState.unknown(), Map.of()), second));
		var engine = new BenefitEngine();
		var analyzer = new AssumptionAnalyzer(engine);
		assertThat(engine.recommend(cards, PAYMENT).recommendedUserCardId()).isEqualTo(1L);
		assertThat(analyzer.affectsRank(cards, PAYMENT, 1L, BenefitDetails.AssumptionType.REMAINING_LIMIT, 10L, 1L)).isTrue();
		assertThat(analyzer.confirmedOnlyTop(cards, PAYMENT)).isEqualTo(2L);
	}

	@Test void zeroBenefitDoesNotPollutePositiveResultOrConsumeCount() {
		var rules = List.of(rule(1, "0.01", 10, true), rule(2, "0.02", 20, true));
		var result = calculate(rules, Map.of(10L, cap(10, null, null, null), 20L, cap(20, 0L, 2, null)), Map.of());
		assertThat(result.expectedBenefit()).isEqualTo(100);
		assertThat(result.status()).isEqualTo(CardBenefitResult.Status.CONFIRMED);
		assertThat(result.assumptions()).isEmpty();
		assertThat(result.appliedBenefits()).extracting(CardBenefitResult.AppliedBenefit::ruleId).containsExactly(1L);
	}

	@Test void truncatesEachBenefitAndResultsAreIndependentOfInputOrder() {
		var a = rule(1, "0.00019", 10, true);
		var b = rule(2, "0.00019", 10, true);
		var first = calculate(List.of(a, b), unlimited(), Map.of());
		var second = calculate(List.of(b, a), unlimited(), Map.of());
		assertThat(first.expectedBenefit()).isEqualTo(2);
		assertThat(first.appliedBenefits()).isEqualTo(second.appliedBenefits());
		assertThat(first.appliedRuleId()).isEqualTo(1L);
	}

	private CardBenefitResult calculate(List<ProductRules.Rule> rules, Map<Long, ProductRules.Cap> caps, Map<Long, CapState> states) {
		return calculator.calculate(product(rules, caps), card(states), PAYMENT);
	}
	private static ProductRules product(List<ProductRules.Rule> rules, Map<Long, ProductRules.Cap> caps) {
		return new ProductRules(1L, true, 0L, rules, Map.of(), caps);
	}
	private static UserCardSnapshot card(Map<Long, CapState> states) {
		return new UserCardSnapshot(1L, 1L, 1, PerformanceState.unknown(), states);
	}
	private static Map<Long, ProductRules.Cap> unlimited() { return Map.of(10L, cap(10, null, null, null)); }
	private static ProductRules.Cap cap(long id, Long amount, Integer count, Long parent) {
		return new ProductRules.Cap(id, amount, count, parent, "한도", CapPeriod.MONTHLY);
	}
	private static ProductRules.Rule rule(long id, String rate, long capId, boolean stackable) {
		return new ProductRules.Rule(id, null, 1L, null, capId, BenefitType.DISCOUNT_RATE,
				new BigDecimal(rate), null, null, null, null, RuleConditions.NONE, "혜택 " + id, stackable);
	}
}
