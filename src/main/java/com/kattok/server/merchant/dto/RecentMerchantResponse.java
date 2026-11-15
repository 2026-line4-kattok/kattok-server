package com.kattok.server.merchant.dto;

import java.time.Instant;

import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;

/** 결제처 검색(4-4)과 같은 결제처 필드에 최근 추천 시각을 더한다. 지원 여부도 검색과 같은 기준이다. */
public record RecentMerchantResponse(Long id, String name, MerchantChannel channel,
		MerchantResponse.CategorySummary category, boolean supported, String unsupportedReason,
		Instant lastRecommendedAt) {
	public static RecentMerchantResponse from(Merchant merchant, Instant lastRecommendedAt, boolean supported) {
		MerchantResponse current = MerchantResponse.of(merchant, supported);
		return new RecentMerchantResponse(current.id(), current.name(), current.channel(), current.category(),
				current.supported(), current.unsupportedReason(), lastRecommendedAt);
	}
}
