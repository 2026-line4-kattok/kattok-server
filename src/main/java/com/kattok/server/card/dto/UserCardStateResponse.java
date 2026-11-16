package com.kattok.server.card.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.dto.CardProductDetailResponse.BenefitSummary;
import com.kattok.server.card.entity.PerformanceType;

/**
 * @param performanceTierOptions 상품 상세(2-3)의 {@code performanceTiers}에 구간별 한도 목록({@code caps})을 더한 형식.
 *     NONE·NEEDS_CHECK는 빈 배열
 * @param caps 실적 무관 혜택의 한도 + 적용 구간 혜택의 한도(상위 한도 포함). 적용 구간은 확인한 구간, 실적을 모르면 추천
 *     계산과 같은 최소 혜택 구간이다.
 */
public record UserCardStateResponse(
		String month,
		PerformanceType performanceType,
		PerformanceStatus performanceStatus,
		PerformanceTierOption performanceTier,
		List<TierOption> performanceTierOptions,
		List<CapStatus> caps,
		Instant confirmedAt) {

	public record PerformanceTierOption(Long id, int level, String label, long minAmount) {
	}

	/**
	 * 실적 수정 시트의 선택지 한 개. 구간을 바꾸면 화면은 {@code caps}에서 {@code tierId}가 {@code null}인 실적 무관 한도와
	 * 고른 선택지의 {@code caps}를 보여 주면 된다.
	 *
	 * @param caps 이 구간 혜택에만 걸린 한도(상위 한도 포함). 실적 무관 혜택과 같이 쓰는 한도는 빼고, 저장된 남은 값을 함께 준다.
	 */
	public record TierOption(
			Long id, int level, long minAmount, String label, List<BenefitSummary> benefits, List<CapStatus> caps) {
	}

	/**
	 * @param capAmount 적용 구간의 전체 금액 한도. 금액 제한이 없으면 {@code null}
	 * @param capCount 적용 구간의 전체 횟수. 횟수 제한이 없으면 {@code null}
	 * @param tierId 이 한도가 속한 실적 구간. 실적 무관 혜택의 한도면 {@code null}
	 */
	public record CapStatus(
			Long capGroupId,
			Long tierId,
			String description,
			CapPeriod capPeriod,
			Long capAmount,
			Integer capCount,
			Long remainingLimitAmount,
			Integer remainingCount,
			Instant confirmedAt, String amountSource, String countSource, LocalDate periodStart) {
	}
}
