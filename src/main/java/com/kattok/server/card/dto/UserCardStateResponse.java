package com.kattok.server.card.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.dto.CardProductDetailResponse.PerformanceTierDetail;
import com.kattok.server.card.entity.PerformanceType;

/**
 * @param performanceTierOptions 상품 상세(2-3)의 {@code performanceTiers}와 같은 형식. NONE·NEEDS_CHECK는 빈 배열
 * @param caps 실적 무관 혜택의 한도 + 적용 구간 혜택의 한도(상위 한도 포함). 적용 구간은 확인한 구간, 실적을 모르면 추천
 *     계산과 같은 최소 혜택 구간이다.
 */
public record UserCardStateResponse(
		String month,
		PerformanceType performanceType,
		PerformanceStatus performanceStatus,
		PerformanceTierOption performanceTier,
		List<PerformanceTierDetail> performanceTierOptions,
		List<CapStatus> caps,
		Instant confirmedAt) {

	public record PerformanceTierOption(Long id, int level, String label, long minAmount) {
	}

	/**
	 * @param capAmount 적용 구간의 전체 금액 한도. 금액 제한이 없으면 {@code null}
	 * @param capCount 적용 구간의 전체 횟수. 횟수 제한이 없으면 {@code null}
	 */
	public record CapStatus(
			Long capGroupId,
			String description,
			CapPeriod capPeriod,
			Long capAmount,
			Integer capCount,
			Long remainingLimitAmount,
			Integer remainingCount,
			Instant confirmedAt, String amountSource, String countSource, LocalDate periodStart) {
	}
}
