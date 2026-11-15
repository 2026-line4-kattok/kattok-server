package com.kattok.server.card.entity;

/** 카드 상품이 실적 조건을 어떤 방식으로 가지는지. */
public enum PerformanceType {
	/** 실적 조건이 없음 */
	NONE,
	/** 기준 1개(받음/못 받음) */
	SINGLE,
	/** 구간 여러 개 */
	MULTIPLE,
	/** 실적 구간 정보를 아직 확인하지 못함 */
	NEEDS_CHECK
}
