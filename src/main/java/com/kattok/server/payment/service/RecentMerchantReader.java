package com.kattok.server.payment.service;

import java.time.Instant;
import java.util.List;

/** merchant 도메인이 현재 사용자의 실제 결제 결과를 조회하는 창구. */
public interface RecentMerchantReader {
	/** 결제처별 가장 최근 추천 시각 내림차순, 동률이면 결제처 ID 오름차순. */
	List<RecentMerchant> recentMerchants(Long userId, int limit);

	record RecentMerchant(Long merchantId, Instant lastRecommendedAt) {}
}
