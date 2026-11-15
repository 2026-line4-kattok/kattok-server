package com.kattok.server.card.dto;

import java.time.Instant;
import java.util.List;

import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.entity.PerformanceType;

public record UserCardStateResponse(
		String month,
		PerformanceType performanceType,
		PerformanceStatus performanceStatus,
		PerformanceTierOption performanceTier,
		List<PerformanceTierOption> performanceTierOptions,
		List<CapStatus> caps,
		Instant confirmedAt) {

	public record PerformanceTierOption(Long id, int level, String label, long minAmount) {
	}

	public record CapStatus(
			Long capGroupId,
			String description,
			CapPeriod capPeriod,
			Long capAmount,
			Long remainingLimitAmount,
			Integer remainingCount,
			Instant confirmedAt, String amountSource, String countSource, java.time.LocalDate periodStart) {
	}
}
