package com.kattok.server.benefit.engine;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 계산할 결제 한 건.
 *
 * @param merchantId 결제처 id. 업종만 고른 경우 {@code null}이고, 이때 브랜드 한정 혜택은 적용하지 않는다.
 * @param categoryId 업종 id. 결제처를 고른 경우 그 결제처의 업종이다.
 * @param amount 결제 금액(원)
 * @param paidAt 결제 시각(한국 시간). 요일·시간대 조건 판단에 쓴다.
 */
public record PaymentContext(Long merchantId, Long categoryId, long amount, LocalDateTime paidAt) {

	public PaymentContext {
		Objects.requireNonNull(categoryId, "categoryId");
		Objects.requireNonNull(paidAt, "paidAt");
		if (amount < 1) {
			throw new IllegalArgumentException("결제 금액은 1원 이상이어야 합니다.");
		}
	}

	public static PaymentContext atMerchant(long merchantId, long categoryId, long amount, LocalDateTime paidAt) {
		return new PaymentContext(merchantId, categoryId, amount, paidAt);
	}

	public static PaymentContext inCategory(long categoryId, long amount, LocalDateTime paidAt) {
		return new PaymentContext(null, categoryId, amount, paidAt);
	}
}
