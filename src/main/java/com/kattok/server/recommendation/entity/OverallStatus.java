package com.kattok.server.recommendation.entity;

/** 추천 1건 전체의 비교 결과 상태. 값은 API 명세 1-1의 {@code overallStatus}와 같다. */
public enum OverallStatus {
	/** 추천 카드에 가정이 없음 */
	CONFIRMED,
	/** 추천 카드에 가정이 있음 */
	CONDITIONAL,
	/** 비교한 모든 카드가 0원 */
	NO_BENEFIT,
	/** 비교할 수 있는 카드가 없음 */
	NOT_CALCULABLE
}
