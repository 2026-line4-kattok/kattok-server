package com.kattok.server.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 추천 생성 요청. {@code merchantId}와 {@code categoryId} 중 하나만 보낸다(서비스에서 검사).
 *
 * @param merchantId 결제처 id
 * @param categoryId 업종 id. 업종 칩으로 고른 경우
 * @param amount 결제 금액(원)
 */
public record CreateRecommendationRequest(
		Long merchantId,
		Long categoryId,
		@NotNull(message = "결제 금액을 입력해 주세요.")
		@Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
		@Max(value = 10_000_000, message = "결제 금액은 1,000만원 이하여야 합니다.")
		Long amount) {
}
