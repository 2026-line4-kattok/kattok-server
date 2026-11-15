package com.kattok.server.card.dto;

import com.kattok.server.card.entity.ConfirmationSource;

/** 금액과 횟수는 사용자 입력·결제 추정 출처를 각각 보관한다. */
public record CapState(Long remainingAmount, Integer remainingCount,
		ConfirmationSource amountSource, ConfirmationSource countSource) {
	public CapState(Long amount, Integer count, ConfirmationSource source) { this(amount, count, source, source); }

	/** 이전 호출의 단일 출처 접근 호환. 계산 상세에서는 필드별 출처를 사용한다. */
	public ConfirmationSource source() {
		if (amountSource == ConfirmationSource.SYSTEM_ESTIMATED || countSource == ConfirmationSource.SYSTEM_ESTIMATED)
			return ConfirmationSource.SYSTEM_ESTIMATED;
		return amountSource == null ? countSource : amountSource;
	}
}
