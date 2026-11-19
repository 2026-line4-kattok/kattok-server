package com.kattok.server.merchant.dto;

import java.time.Instant;

import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;
import com.kattok.server.merchant.entity.MerchantChannel;

/**
 * 결제처 검색(4-4)과 같은 결제처 필드에 최근 추천 시각을 더한다. 지원 여부도 검색과 같은 기준이다.
 * 업종으로만 고른 기록은 {@code type}이 {@code CATEGORY}이고 {@code id}·{@code channel}은 {@code null}, {@code name}은 업종 이름이다.
 */
public record RecentMerchantResponse(Long id, String name, MerchantChannel channel,
		MerchantResponse.CategorySummary category, boolean supported, String unsupportedReason,
		Instant lastRecommendedAt, Type type) {

	public enum Type { MERCHANT, CATEGORY }

	public static RecentMerchantResponse from(Merchant merchant, Instant lastRecommendedAt, boolean supported) {
		MerchantResponse current = MerchantResponse.of(merchant, supported);
		return new RecentMerchantResponse(current.id(), current.name(), current.channel(), current.category(),
				current.supported(), current.unsupportedReason(), lastRecommendedAt, Type.MERCHANT);
	}

	public static RecentMerchantResponse ofCategory(MerchantCategory category, String displayName,
			Instant lastRecommendedAt, boolean supported) {
		return new RecentMerchantResponse(null, displayName, null,
				new MerchantResponse.CategorySummary(category.getId(), displayName), supported,
				supported ? null : MerchantResponse.UNSUPPORTED_REASON, lastRecommendedAt, Type.CATEGORY);
	}
}
