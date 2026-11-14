package com.kattok.server.benefit.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.kattok.server.benefit.engine.RecommendationResult.OverallStatus;
import com.kattok.server.card.dto.UserCardSnapshot;

/**
 * 보유 카드 전체를 계산해 순위를 매기고 추천 카드 한 장을 고른다. Spring과 JPA에 의존하지 않는다.
 *
 * <p>순위는 예상 혜택이 큰 순서이고, 같으면 필요 실적이 낮은 카드, 연회비가 낮은 카드(모르면 뒤로),
 * 먼저 등록한 카드 순서로 정한다.
 */
public class BenefitEngine {

	private static final Comparator<CardBenefitResult> RANKING = Comparator
			.comparingLong(CardBenefitResult::expectedBenefit).reversed()
			.thenComparingLong(CardBenefitResult::requiredPerformance)
			.thenComparing(CardBenefitResult::annualFee, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparingInt(CardBenefitResult::registrationOrder);

	private final BenefitCalculator calculator;

	public BenefitEngine() {
		this(new BenefitCalculator());
	}

	public BenefitEngine(BenefitCalculator calculator) {
		this.calculator = calculator;
	}

	public RecommendationResult recommend(List<CardInput> cards, PaymentContext payment) {
		List<CardBenefitResult> results = cards.stream()
				.map(card -> calculator.calculate(card.product(), card.snapshot(), payment))
				.toList();

		List<CardBenefitResult> ranked = results.stream()
				.filter(CardBenefitResult::calculable)
				.sorted(RANKING)
				.toList();
		List<CardBenefitResult> notCalculable = results.stream()
				.filter(result -> !result.calculable())
				.sorted(Comparator.comparingInt(CardBenefitResult::registrationOrder))
				.toList();

		List<CardBenefitResult> ordered = new ArrayList<>(ranked);
		ordered.addAll(notCalculable);

		if (ranked.isEmpty()) {
			return new RecommendationResult(OverallStatus.NOT_CALCULABLE, ordered, null, List.of(), 0);
		}
		CardBenefitResult top = ranked.getFirst();
		if (top.expectedBenefit() == 0) {
			return new RecommendationResult(OverallStatus.NO_BENEFIT, ordered, null, List.of(), 0);
		}

		List<Long> tied = ranked.stream()
				.skip(1)
				.filter(result -> result.expectedBenefit() == top.expectedBenefit())
				.map(CardBenefitResult::userCardId)
				.toList();
		long second = ranked.size() > 1 ? ranked.get(1).expectedBenefit() : 0;
		OverallStatus overall = top.status() == CardBenefitResult.Status.CONDITIONAL
				? OverallStatus.CONDITIONAL
				: OverallStatus.CONFIRMED;
		return new RecommendationResult(overall, ordered, top.userCardId(), tied, top.expectedBenefit() - second);
	}

	/** 계산할 보유 카드 한 장: 사용자 카드 상태와 카드 상품 정보. */
	public record CardInput(UserCardSnapshot snapshot, ProductRules product) {
	}
}
