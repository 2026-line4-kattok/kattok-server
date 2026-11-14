package com.kattok.server.merchant.dto;

import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;

public record MerchantResponse(Long id, String name, MerchantChannel channel, CategorySummary category) {

	public static MerchantResponse from(Merchant merchant) {
		return new MerchantResponse(
				merchant.getId(), merchant.getName(), merchant.getChannel(),
				new CategorySummary(merchant.getCategory().getId(), merchant.getCategory().getName()));
	}

	public record CategorySummary(Long id, String name) {
	}
}
