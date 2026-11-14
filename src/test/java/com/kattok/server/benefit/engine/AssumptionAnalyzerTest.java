package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kattok.server.benefit.engine.BenefitDetails.AssumptionType;
import com.kattok.server.benefit.engine.BenefitEngine.CardInput;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

class AssumptionAnalyzerTest {

	private static final long CAFE = 1L;
	private static final PaymentContext PAYMENT =
			PaymentContext.inCategory(CAFE, 10000, LocalDateTime.of(2026, 11, 18, 14, 0));

	private final BenefitEngine engine = new BenefitEngine();
	private final AssumptionAnalyzer analyzer = new AssumptionAnalyzer(engine);

	/** 카드 A: 실적 1구간(30만원)부터 카페 10%(월 5천원). 실적 미확인이면 조건부 1,000원. */
	private final CardInput tierCardUnknown = new CardInput(
			new UserCardSnapshot(1L, 1L, 1, PerformanceState.unknown(),
					Map.of(10L, new CapState(5000L, null, ConfirmationSource.USER_CONFIRMED))),
			new ProductRules(1L, true, 10000L,
					List.of(rule(1L, 7L, 10L, "0.10")),
					Map.of(7L, new Tier(7L, 1, 300000)),
					Map.of(10L, new Cap(10L, 5000L, null, null))));

	/** 카드 B: 실적 조건 없이 카페 5%, 한도 없음. 항상 확정 500원. */
	private final CardInput flatCard = new CardInput(
			new UserCardSnapshot(2L, 2L, 2, PerformanceState.unknown(), Map.of()),
			new ProductRules(2L, true, 0L,
					List.of(rule(2L, null, 20L, "0.05")),
					Map.of(),
					Map.of(20L, new Cap(20L, null, null, null))));

	@Test
	@DisplayName("모르는 실적을 '못 받음'으로 두면 확정 혜택이 있는 다른 카드가 1위가 된다")
	void confirmedOnlyTop() {
		List<CardInput> cards = List.of(tierCardUnknown, flatCard);

		assertThat(engine.recommend(cards, PAYMENT).recommendedUserCardId()).isEqualTo(1L);
		assertThat(analyzer.confirmedOnlyTop(cards, PAYMENT)).isEqualTo(2L);
	}

	@Test
	@DisplayName("추천 카드의 실적 가정이 틀리면 추천이 바뀌므로 affectsRank는 true다")
	void topCardPerformanceAffectsRank() {
		List<CardInput> cards = List.of(tierCardUnknown, flatCard);

		assertThat(analyzer.affectsRank(cards, PAYMENT, 1L, AssumptionType.PERFORMANCE_TIER, 1L)).isTrue();
	}

	@Test
	@DisplayName("가정이 바뀌어도 다른 카드를 넘지 못하면 affectsRank는 false다")
	void assumptionNotAffectingRank() {
		// 카드 C: 실적 조건 없이 카페 20%(한도 없음) → 항상 2,000원으로 1위
		CardInput strongCard = new CardInput(
				new UserCardSnapshot(3L, 3L, 3, PerformanceState.unknown(), Map.of()),
				new ProductRules(3L, true, 0L, List.of(rule(3L, null, 30L, "0.20")), Map.of(),
						Map.of(30L, new Cap(30L, null, null, null))));
		List<CardInput> cards = List.of(tierCardUnknown, strongCard);

		assertThat(engine.recommend(cards, PAYMENT).recommendedUserCardId()).isEqualTo(3L);
		assertThat(analyzer.affectsRank(cards, PAYMENT, 1L, AssumptionType.PERFORMANCE_TIER, 3L)).isFalse();
	}

	@Test
	@DisplayName("모르는 남은 한도를 0으로 두면 추천이 바뀌는 경우 affectsRank는 true다")
	void unknownLimitAffectsRank() {
		CardInput limitUnknown = new CardInput(
				new UserCardSnapshot(1L, 1L, 1, PerformanceState.unknown(), Map.of()),
				new ProductRules(1L, true, 10000L, List.of(rule(1L, null, 10L, "0.10")), Map.of(),
						Map.of(10L, new Cap(10L, 5000L, null, null))));
		List<CardInput> cards = List.of(limitUnknown, flatCard);

		assertThat(analyzer.affectsRank(cards, PAYMENT, 1L, AssumptionType.REMAINING_LIMIT, 1L)).isTrue();
		assertThat(analyzer.confirmedOnlyTop(cards, PAYMENT)).isEqualTo(2L);
	}

	private static Rule rule(long id, Long requiredTierId, long capGroupId, String rate) {
		return new Rule(id, null, CAFE, requiredTierId, capGroupId, BenefitType.DISCOUNT_RATE, new BigDecimal(rate),
				null, null, null, null, RuleConditions.NONE, "카페 할인");
	}
}
