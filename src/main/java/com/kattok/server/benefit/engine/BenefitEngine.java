package com.kattok.server.benefit.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.kattok.server.benefit.engine.RecommendationResult.OverallStatus;
import com.kattok.server.card.dto.UserCardSnapshot;

/**
 * 보유 카드 전체를 계산해 순위를 매기고 추천 카드 한 장을 고른다. Spring과 JPA에 의존하지 않는다.
 *
 * <p>순위는 예상 혜택이 큰 순서이고, 같으면 필요 실적이 낮은 카드, 연회비가 낮은 카드, 먼저 등록한 카드 순서로 정한다.
 * 연회비 단계는 혜택과 필요 실적이 같은 카드끼리 비교하며, 그중 연회비를 모르는 카드가 하나라도 있으면 건너뛴다
 * (기획의 추천 계산 공통 정책 7절). 이 규칙은 두 카드만 보고는 정할 수 없어서 묶음 단위로 정렬한다.
 */
public class BenefitEngine {

	/** 연회비 단계를 뺀 순서. 혜택·필요 실적이 같은 묶음 안은 등록 순이다. */
	private static final Comparator<CardBenefitResult> WITHOUT_ANNUAL_FEE = Comparator
			.comparingLong(CardBenefitResult::expectedBenefit).reversed()
			.thenComparingLong(CardBenefitResult::requiredPerformance)
			.thenComparingInt(CardBenefitResult::registrationOrder);

	private static final Comparator<CardBenefitResult> BY_ANNUAL_FEE = Comparator
			.comparing(CardBenefitResult::annualFee)
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

		List<CardBenefitResult> ranked = rank(results.stream().filter(CardBenefitResult::calculable).toList());
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

	static List<CardBenefitResult> rank(List<CardBenefitResult> calculable) {
		List<CardBenefitResult> sorted = calculable.stream().sorted(WITHOUT_ANNUAL_FEE).toList();
		List<CardBenefitResult> ranked = new ArrayList<>(sorted.size());
		int start = 0;
		while (start < sorted.size()) {
			int end = start + 1;
			while (end < sorted.size() && sameTieGroup(sorted.get(start), sorted.get(end))) {
				end++;
			}
			List<CardBenefitResult> group = sorted.subList(start, end);
			boolean allFeesKnown = group.stream().allMatch(result -> result.annualFee() != null);
			ranked.addAll(allFeesKnown ? group.stream().sorted(BY_ANNUAL_FEE).toList() : group);
			start = end;
		}
		return ranked;
	}

	private static boolean sameTieGroup(CardBenefitResult a, CardBenefitResult b) {
		return a.expectedBenefit() == b.expectedBenefit() && a.requiredPerformance() == b.requiredPerformance();
	}

	/** 계산할 보유 카드 한 장: 사용자 카드 상태와 카드 상품 정보. */
	public record CardInput(UserCardSnapshot snapshot, ProductRules product) {
	}
}
