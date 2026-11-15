package com.kattok.server.merchant.dto;

import java.time.Instant;

import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;

/** 4-4 결제처 객체에 {@code lastRecommendedAt}이 추가된, "최근 결제한 곳"(4-2) 전용 응답. */
public record RecentMerchantResponse(
		Long id, String name, MerchantChannel channel, MerchantResponse.CategorySummary category,
		Instant lastRecommendedAt) {

	public static RecentMerchantResponse of(Merchant merchant, Instant lastRecommendedAt) {
		return new RecentMerchantResponse(
				merchant.getId(), merchant.getName(), merchant.getChannel(),
				new MerchantResponse.CategorySummary(merchant.getCategory().getId(), merchant.getCategory().getName()),
				lastRecommendedAt);
	}
}
