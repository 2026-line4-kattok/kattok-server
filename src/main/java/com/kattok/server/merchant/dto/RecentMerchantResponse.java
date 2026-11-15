package com.kattok.server.merchant.dto;

import java.time.Instant;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;

public record RecentMerchantResponse(Long id, String name, MerchantChannel channel,
		MerchantResponse.CategorySummary category, Instant lastRecommendedAt) {
	public static RecentMerchantResponse from(Merchant merchant, Instant lastRecommendedAt) {
		var current = MerchantResponse.from(merchant);
		return new RecentMerchantResponse(current.id(), current.name(), current.channel(), current.category(), lastRecommendedAt);
	}
}
