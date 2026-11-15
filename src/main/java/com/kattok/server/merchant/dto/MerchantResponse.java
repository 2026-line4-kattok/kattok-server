package com.kattok.server.merchant.dto;

import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;

public record MerchantResponse(
		Long id, String name, MerchantChannel channel, CategorySummary category, boolean supported,
		String unsupportedReason) {

	private static final String UNSUPPORTED_REASON = "카똑이 아직 계산하지 않는 곳";

	public static MerchantResponse of(Merchant merchant, boolean supported) {
		return new MerchantResponse(
				merchant.getId(), merchant.getName(), merchant.getChannel(),
				new CategorySummary(merchant.getCategory().getId(), merchant.getCategory().getName()),
				supported, supported ? null : UNSUPPORTED_REASON);
	}

	public record CategorySummary(Long id, String name) {
	}
}
