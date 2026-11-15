package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kattok.server.benefit.engine.BenefitDetails.AssumptionType;
import com.kattok.server.benefit.engine.CardBenefitResult.LimitSource;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

class BenefitProjectionTest {
	private final Rule rule = new Rule(1L, null, 1L, 7L, 10L, BenefitType.DISCOUNT_RATE,
			new BigDecimal("0.10"), null, null, null, null, RuleConditions.NONE, "카페 할인");

	@Test
	@DisplayName("상위 한도까지 금액·횟수와 출처를 독립적으로 예측하고 저장된 상태는 바꾸지 않는다")
	void projectsEachCap() {
		ProductRules product = product(Map.of(
				10L, new Cap(10L, 5000L, 3, 20L, "카페", CapPeriod.MONTHLY),
				20L, new Cap(20L, 10000L, null, null, "통합", CapPeriod.MONTHLY)));
		UserCardSnapshot card = card(Map.of(10L, new CapState(2000L, null, ConfirmationSource.USER_CONFIRMED),
				20L, new CapState(8000L, null, ConfirmationSource.SYSTEM_ESTIMATED)));
		var projected = BenefitDetails.remainingLimitsAfter(rule, product, card, 500);
		assertThat(BenefitDetails.capGroupIds(rule, product)).containsExactly(10L, 20L);
		assertThat(projected).hasSize(2);
		var leaf = projected.getFirst();
		assertThat(leaf.remainingLimitBefore()).isEqualTo(2000L);
		assertThat(leaf.remainingLimitAmount()).isEqualTo(1500L);
		assertThat(leaf.amountSource()).isEqualTo(LimitSource.USER_INPUT);
		assertThat(leaf.remainingCountBefore()).isEqualTo(3);
		assertThat(leaf.remainingCount()).isEqualTo(2);
		assertThat(leaf.countSource()).isEqualTo(LimitSource.ASSUMED);
		assertThat(projected.get(1).remainingLimitAmount()).isEqualTo(7500L);
		assertThat(projected.get(1).amountSource()).isEqualTo(LimitSource.ESTIMATED);
		assertThat(projected.get(1).remainingCount()).isNull();
		assertThat(projected.get(1).countSource()).isNull();
		assertThat(card.caps().get(10L).remainingAmount()).isEqualTo(2000L);
		assertThat(card.caps().get(10L).remainingCount()).isNull();
	}

	@Test
	@DisplayName("모르는 금액·횟수 가정은 각각 대상 한도 ID를 포함한다")
	void targetsEachUnknownCap() {
		ProductRules product = product(Map.of(10L, new Cap(10L, 5000L, 3, 20L),
				20L, new Cap(20L, 10000L, null, null)));
		UserCardSnapshot card = card(Map.of());
		var result = new BenefitCalculator().calculate(product, card,
				PaymentContext.inCategory(1L, 5000, LocalDateTime.of(2026, 11, 15, 12, 0)));
		var targets = BenefitDetails.assumptionTargets(result, rule, product, card);
		assertThat(targets).extracting(t -> t.type()).containsExactly(
				AssumptionType.PERFORMANCE_TIER, AssumptionType.REMAINING_LIMIT,
				AssumptionType.REMAINING_LIMIT, AssumptionType.REMAINING_COUNT);
		assertThat(targets.getFirst().capGroupId()).isNull();
		assertThat(targets.subList(1, 4)).extracting(t -> t.capGroupId()).containsExactly(10L, 20L, 10L);
	}

	@Test
	@DisplayName("횟수 전용 한도는 금액이 없고 무제한 그룹은 잔여 예측에서 제외한다")
	void countOnlyAndUnlimited() {
		ProductRules product = product(Map.of(10L, new Cap(10L, null, 2, 20L),
				20L, new Cap(20L, null, null, null)));
		var projected = BenefitDetails.remainingLimitsAfter(rule, product, card(Map.of()), 500);
		assertThat(projected).hasSize(1);
		assertThat(projected.getFirst().remainingLimitAmount()).isNull();
		assertThat(projected.getFirst().amountSource()).isNull();
		assertThat(projected.getFirst().remainingCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("한도 순환은 중복 없이 종료하고 잔여 값은 음수가 되지 않는다")
	void cyclicCapsAndFloor() {
		ProductRules product = product(Map.of(10L, new Cap(10L, 100L, 0, 20L),
				20L, new Cap(20L, 200L, null, 10L)));
		var projected = BenefitDetails.remainingLimitsAfter(rule, product, card(Map.of()), 500);
		assertThat(projected).hasSize(2);
		assertThat(projected.getFirst().remainingLimitAmount()).isZero();
		assertThat(projected.getFirst().remainingCount()).isZero();
	}

	private ProductRules product(Map<Long, Cap> caps) {
		return new ProductRules(1L, true, null, List.of(rule), Map.of(7L, new Tier(7L, 1, 300000)), caps);
	}
	private UserCardSnapshot card(Map<Long, CapState> caps) {
		return new UserCardSnapshot(1L, 1L, 1, PerformanceState.unknown(), caps);
	}
}
