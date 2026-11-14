package com.kattok.server.card.dto;

import java.time.YearMonth;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * 보내지 않은 필드는 그대로 둔다(부분 수정). {@code caps}에 담지 않은 한도 그룹은 바뀌지 않는다 — 통째로
 * 덮어쓰지 않기로 했다(한도 하나만 고치려다 나머지가 미확인으로 리셋되는 걸 막기 위함, 2026-11 결정).
 */
public record UpdateCardStateRequest(@NotNull YearMonth month, Long performanceTierId, List<CapUpdate> caps) {

	public record CapUpdate(@NotNull Long capGroupId, Long remainingLimitAmount, Integer remainingCount) {
	}
}
