package com.kattok.server.card.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * @param performance 실적 조건이 있는 카드(SINGLE·MULTIPLE)만 보낸다. 실적 조건이 없거나 확인이 필요한
 *     카드(NONE·NEEDS_CHECK)는 필드를 아예 보내지 않는다. 명시적 {@code null}은 컨트롤러에서 400으로 막는다.
 */
public record RegisterCardRequest(@NotNull Long productId, @Valid PerformanceInput performance) {
}
