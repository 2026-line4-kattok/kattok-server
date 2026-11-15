package com.kattok.server.recommendation.service;

import java.time.Instant;
import java.util.List;

/**
 * 결제처 검색 화면의 "최근 결제한 곳" 칩이 읽는 창구. merchant 도메인은 이 인터페이스로만 추천 데이터를 본다.
 *
 * <p>지금은 확정한 추천({@code Recommendation.savedAt})을 기준으로 본다. 결제 완료 여부를 확인하는 API가
 * 생기면, 이 구현을 {@code PaymentRecord}의 실제 결제 완료 상태를 보도록 바꾸면 된다(merchant 쪽은 그대로).
 */
public interface RecentMerchantReader {

	/**
	 * 사용자가 결제처를 골라 확정한 추천 중 최근 것부터, 결제처 중복 없이 최대 {@code limit}개.
	 *
	 * @return 최근 사용 순. 확정한 추천이 없으면 빈 목록
	 */
	List<RecentMerchant> recentMerchants(Long userId, int limit);

	/** @param lastRecommendedAt 이 결제처의 대상 기록 중 가장 최근 추천(확정) 시각 */
	record RecentMerchant(Long merchantId, Instant lastRecommendedAt) {
	}
}
