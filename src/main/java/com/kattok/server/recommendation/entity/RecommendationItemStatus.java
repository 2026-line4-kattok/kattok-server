package com.kattok.server.recommendation.entity;

/** 추천 결과에서 카드 1장(RecommendationItem)의 계산 상태. */
public enum RecommendationItemStatus {
	CALCULATED,
	CONDITIONAL,
	NO_BENEFIT,
	NOT_CALCULABLE
}
