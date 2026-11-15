package com.kattok.server.merchant.dto;

import java.time.Instant;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;

public record RecentMerchantResponse(Long id, String name, MerchantChannel channel,
		MerchantResponse.CategorySummary category, Instant lastRecommendedAt) {
	public static RecentMerchantResponse from(Merchant merchant, Instant lastRecommendedAt) {
		return new RecentMerchantResponse(merchant.getId(), merchant.getName(), merchant.getChannel(),
				new MerchantResponse.CategorySummary(merchant.getCategory().getId(), merchant.getCategory().getName()),
				lastRecommendedAt);
	}
}
