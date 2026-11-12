package com.kattok.server.card.dto;

/**
 * 보유 카드의 이번 달 실적 상태.
 *
 * <p>카드 상품에 실적 조건이 아예 없는 경우('해당 없음')는 사용자 상태가 아니라 상품 정보로 판단한다.
 * 이때는 {@link PerformanceStatus#UNKNOWN}과 {@code tierId = null}로 준다.
 *
 * @param status 실적 상태
 * @param tierId 확인된 실적 구간 id. {@link PerformanceStatus#CONFIRMED}일 때만 값이 있고, 나머지는 {@code null}
 */
public record PerformanceState(PerformanceStatus status, Long tierId) {

	public PerformanceState {
		if (status == PerformanceStatus.CONFIRMED && tierId == null) {
			throw new IllegalArgumentException("CONFIRMED 상태에는 실적 구간 id가 필요합니다.");
		}
		if (status != PerformanceStatus.CONFIRMED && tierId != null) {
			throw new IllegalArgumentException(status + " 상태에는 실적 구간 id를 넣지 않습니다.");
		}
	}

	public static PerformanceState confirmed(Long tierId) {
		return new PerformanceState(PerformanceStatus.CONFIRMED, tierId);
	}

	public static PerformanceState below() {
		return new PerformanceState(PerformanceStatus.BELOW, null);
	}

	public static PerformanceState unknown() {
		return new PerformanceState(PerformanceStatus.UNKNOWN, null);
	}
}
