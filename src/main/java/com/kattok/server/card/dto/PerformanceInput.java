package com.kattok.server.card.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 카드 등록·상태 수정 요청에 실리는 실적 정보. {@code status}가 {@link PerformanceStatus#CONFIRMED}일
 * 때만 {@code tierId}가 필요하다(나머지 상태에서 보내면 400).
 */
public record PerformanceInput(@NotNull PerformanceStatus status, Long tierId) {
}
