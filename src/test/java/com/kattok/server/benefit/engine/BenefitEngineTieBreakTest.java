package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kattok.server.benefit.engine.BenefitEngine.CardInput;
import com.kattok.server.card.dto.UserCardSnapshot;

/**
 * 동률 순서: 필요 실적이 낮은 카드 → 연회비가 낮은 카드(같이 묶인 카드 중 연회비를 모르는 카드가 있으면 이 단계를 건너뜀) → 먼저 등록한 카드.
 * 기획의 추천 계산 공통 정책 7절 기준이다.
 */
class BenefitEngineTieBreakTest {

	private static final PaymentContext PAYMENT = PaymentContext.inCategory(1, 10000,
			LocalDateTime.of(2026, 11, 16, 12, 0));

	@Test
	@DisplayName("동률 카드의 연회비를 모두 알면 연회비가 낮은 카드가 앞선다")
	void lowerAnnualFeeFirstWhenAllKnown() {
		RecommendationResult result = recommend(
				card(1, 1, 500, 0, 20000L),
				card(2, 2, 500, 0, 10000L));

		assertThat(order(result)).containsExactly(2L, 1L);
		assertThat(result.recommendedUserCardId()).isEqualTo(2L);
		assertThat(result.tiedUserCardIds()).containsExactly(1L);
	}

	@Test
	@DisplayName("동률 카드 중 연회비를 모르는 카드가 있으면 연회비 비교를 건너뛰고 등록 순으로 정한다")
	void skipAnnualFeeWhenAnyUnknown() {
		RecommendationResult result = recommend(
				card(1, 1, 500, 0, null),
				card(2, 2, 500, 0, 10000L),
				card(3, 3, 500, 0, 5000L));

		assertThat(order(result)).containsExactly(1L, 2L, 3L);
		assertThat(result.recommendedUserCardId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("연회비 비교는 필요 실적까지 같은 카드끼리만 한다")
	void annualFeeComparedOnlyWithinSameRequiredPerformance() {
		RecommendationResult result = recommend(
				card(1, 1, 500, 300000, 0L),
				card(2, 2, 500, 0, null),
				card(3, 3, 500, 0, 5000L));

		assertThat(order(result)).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("다른 금액의 카드가 연회비를 몰라도 1위 동률 묶음의 연회비 비교에는 영향이 없다")
	void unknownFeeInOtherBenefitGroupDoesNotMatter() {
		RecommendationResult result = recommend(
				card(1, 1, 1000, 0, 20000L),
				card(2, 2, 1000, 0, 10000L),
				card(3, 3, 500, 0, null));

		assertThat(order(result)).containsExactly(2L, 1L, 3L);
		assertThat(result.differenceFromSecond()).isZero();
	}

	private static RecommendationResult recommend(CardBenefitResult... results) {
		Map<Long, CardBenefitResult> byCard = new java.util.HashMap<>();
		List<CardInput> inputs = new java.util.ArrayList<>();
		for (CardBenefitResult result : results) {
			byCard.put(result.userCardId(), result);
			inputs.add(new CardInput(
					new UserCardSnapshot(result.userCardId(), result.userCardId(), result.registrationOrder(), null,
							Map.of()),
					null));
		}
		BenefitCalculator stub = new BenefitCalculator() {
			@Override
			public CardBenefitResult calculate(ProductRules product, UserCardSnapshot card, PaymentContext payment) {
				return byCard.get(card.userCardId());
			}
		};
		return new BenefitEngine(stub).recommend(inputs, PAYMENT);
	}

	private static CardBenefitResult card(long userCardId, int registrationOrder, long benefit,
			long requiredPerformance, Long annualFee) {
		return new CardBenefitResult(userCardId, CardBenefitResult.Status.CONFIRMED, benefit, 100 + userCardId,
				"혜택", List.of(), CardBenefitResult.LimitSource.NONE, null, requiredPerformance, annualFee,
				registrationOrder);
	}

	private static List<Long> order(RecommendationResult result) {
		return result.cards().stream().map(CardBenefitResult::userCardId).toList();
	}
}
