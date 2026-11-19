package com.kattok.server.payment.service;

import java.time.Instant;
import java.util.List;

/** merchant 도메인이 현재 사용자의 실제 결제 결과를 조회하는 창구. */
public interface RecentMerchantReader {
	/**
	 * 결제처별(결제처를 검색해 고른 기록)과 업종별(업종으로만 고른 기록) 가장 최근 추천 시각 내림차순.
	 * 동률이면 결제처가 먼저, 그 안에서는 ID 오름차순.
	 */
	List<RecentMerchant> recentMerchants(Long userId, int limit);

	/** 결제처 기록이면 {@code merchantId}가 있고, 업종으로만 고른 기록이면 {@code merchantId}가 {@code null}이고 {@code categoryId}가 있다. */
	record RecentMerchant(Long merchantId, Long categoryId, Instant lastRecommendedAt) {}
}
