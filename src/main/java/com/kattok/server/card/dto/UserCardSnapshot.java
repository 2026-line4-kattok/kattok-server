package com.kattok.server.card.dto;

import java.util.Map;

/**
 * 추천 시점의 보유 카드 한 장의 상태.
 *
 * @param userCardId 보유 카드 id
 * @param productId 카드 상품 id. 혜택 규칙과 한도 그룹은 계산 엔진이 이 id로 따로 읽는다.
 * @param registrationOrder 등록 순서(1부터). 예상 혜택과 필요 실적, 연회비가 모두 같을 때 마지막 비교 기준이다.
 * @param performance 이번 달 실적 상태
 * @param caps 한도 그룹 id별 남은 한도. 상위 한도가 있는 혜택이면 하위 한도와 상위 한도가 각각 별도 항목으로 들어간다.
 *     키가 없으면 해당 한도 그룹의 상태를 모르는 것으로 보고, {@code remainingAmount}가 {@code null}인 경우와 같게 처리한다.
 */
public record UserCardSnapshot(
		Long userCardId,
		Long productId,
		int registrationOrder,
		PerformanceState performance,
		Map<Long, CapState> caps) {

	public UserCardSnapshot {
		caps = Map.copyOf(caps);
	}
}
