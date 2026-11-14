package com.kattok.server.card.dto;

import java.time.YearMonth;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 보내지 않은 필드는 그대로 둔다(부분 수정). {@code performance}를 보내지 않으면 실적 확인 상태는
 * 바뀌지 않는다. {@code caps}에 담지 않은 한도 그룹도 바뀌지 않는다(한도 하나만 고치려다 나머지가
 * 미확인으로 리셋되는 걸 막기 위함, 2026-11 결정). 이 "보냄/안 보냄" 구분은 Java 필드만으로는 표현할 수
 * 없어, 컨트롤러가 요청 본문을 직접 들여다보고 이 레코드를 만든다.
 */
public record UpdateCardStateRequest(
		@NotNull YearMonth month, @Valid PerformanceInput performance, @Valid List<CapUpdate> caps) {

	public record CapUpdate(@NotNull Long capGroupId, Long remainingLimitAmount, Integer remainingCount) {
	}
}
