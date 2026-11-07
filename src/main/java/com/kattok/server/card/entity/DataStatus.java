package com.kattok.server.card.entity;

/** 카드 상품의 혜택 규칙 확보·검증 상태. 계산 가능 여부와 직결된다. */
public enum DataStatus {
	/** 해당 결제에 필요한 검증된 규칙이 있음 */
	CALCULABLE,
	/** 필요한 규칙 중 일부만 검증 */
	PARTIAL,
	/** 관련 규칙을 확보·검증하지 못함 */
	UNVERIFIED
}
