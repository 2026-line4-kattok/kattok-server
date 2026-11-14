package com.kattok.server.recommendation.entity;

/**
 * 추천 결과에서 카드 1장(RecommendationItem)의 계산 상태. 앞의 세 값은 API 명세 1-1의 {@code items[].status}와 같고,
 * {@link #NOT_CALCULABLE}은 비교에서 뺀 카드({@code excludedCards})다.
 */
public enum RecommendationItemStatus {
	CONFIRMED,
	CONDITIONAL,
	NO_BENEFIT,
	NOT_CALCULABLE
}
