package com.kattok.server.card.dto;

/** 보유 카드의 이번 달 실적 상태. */
public enum PerformanceStatus {
	/** 실적 구간을 확인함 */
	CONFIRMED,
	/** 가장 낮은 구간도 채우지 못해 실적 조건이 있는 혜택을 못 받음 */
	BELOW,
	/** 실적을 모름 */
	UNKNOWN
}
