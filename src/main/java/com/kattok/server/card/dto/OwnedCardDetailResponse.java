package com.kattok.server.card.dto;

import java.util.List;

import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.PerformanceType;

/**
 * 보유 카드 상세(3-2). {@code product}/{@code performance}/{@code caps}는 명확히 정의된 부분만 담는다.
 * 명세의 {@code benefits}(추천 반영 여부 판단 기준 없음)와 {@code savedBenefit}(이번 달/전체 기간 기준이
 * 기획에서 아직 결정 대기, 7장 참고)은 기준이 정해지기 전까지 이 응답에 넣지 않는다.
 */
public record OwnedCardDetailResponse(
		Long id,
		ProductDetail product,
		PerformanceDetail performance,
		List<UserCardStateResponse.CapStatus> caps) {

	public record ProductDetail(
			Long id, String name, String issuerName, CardType cardType, Long annualFee,
			PerformanceType performanceType) {
	}

	/**
	 * 지난달 실적(이번 달 혜택 구간을 정하는 기준).
	 *
	 * @param baseMonth 실적 기준 월(지난달, 예: {@code 2026-10})
	 */
	public record PerformanceDetail(
			PerformanceStatus status,
			UserCardStateResponse.PerformanceTierOption tier,
			List<UserCardStateResponse.PerformanceTierOption> options,
			String baseMonth) {
	}
}
