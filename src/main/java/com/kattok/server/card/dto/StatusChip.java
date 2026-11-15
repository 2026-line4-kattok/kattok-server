package com.kattok.server.card.dto;

/** 보유 카드 목록의 상태 칩. 우선순위는 선언 순서와 같다(한도 소진 > 실적 입력 필요 > 다 확인함). */
public enum StatusChip {
	/** 이 카드에 적용되는 한도 중 하나라도 이번 기간에 소진됨 */
	LIMIT_EXHAUSTED,
	/** 실적 조건이 있는 카드인데 아직 실적을 확인하지 않음 */
	NEEDS_PERFORMANCE,
	/** 확인할 게 남지 않음 */
	ALL_CONFIRMED
}
