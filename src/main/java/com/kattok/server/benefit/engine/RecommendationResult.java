package com.kattok.server.benefit.engine;

import java.util.List;

/**
 * 보유 카드 전체를 비교한 결과.
 *
 * @param overallStatus 전체 결과 상태. 추천 카드의 상태를 따른다.
 * @param cards 계산한 카드는 순위대로, 계산하지 못한 카드는 그 뒤에 등록 순서대로
 * @param recommendedUserCardId 추천 카드. 혜택이 있는 카드가 없으면 {@code null}
 * @param tiedUserCardIds 추천 카드와 예상 혜택이 같은 다른 카드
 * @param differenceFromSecond 추천 카드와 2위 카드의 예상 혜택 차이(원). 2위가 없으면 추천 카드의 예상 혜택
 */
public record RecommendationResult(
		OverallStatus overallStatus,
		List<CardBenefitResult> cards,
		Long recommendedUserCardId,
		List<Long> tiedUserCardIds,
		long differenceFromSecond) {

	public RecommendationResult {
		cards = List.copyOf(cards);
		tiedUserCardIds = List.copyOf(tiedUserCardIds);
	}

	public CardBenefitResult cardOf(Long userCardId) {
		return cards.stream()
				.filter(card -> card.userCardId().equals(userCardId))
				.findFirst()
				.orElseThrow();
	}

	public enum OverallStatus {
		CONFIRMED,
		CONDITIONAL,
		NO_BENEFIT,
		NOT_CALCULABLE
	}
}
