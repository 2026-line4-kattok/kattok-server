package com.kattok.server.recommendation.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.recommendation.repository.RecommendationRepository;

import lombok.RequiredArgsConstructor;

/** {@link RecentMerchantReader}의 실제 구현. DB에서 확정한 추천을 최신순으로 읽어 결제처를 중복 없이 추린다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaRecentMerchantReader implements RecentMerchantReader {

	/** 중복 결제처를 걸러내고도 limit개를 채울 수 있도록 넉넉히 읽어 오는 행 수. */
	private static final int SEARCH_WINDOW = 50;

	private final RecommendationRepository recommendationRepository;

	@Override
	public List<Long> recentMerchantIds(Long userId, int limit) {
		List<Recommendation> recent = recommendationRepository
				.findByUserIdAndMerchantIsNotNullAndSavedAtIsNotNullOrderBySavedAtDesc(
						userId, PageRequest.of(0, SEARCH_WINDOW));

		Set<Long> merchantIds = new LinkedHashSet<>();
		for (Recommendation recommendation : recent) {
			merchantIds.add(recommendation.getMerchant().getId());
			if (merchantIds.size() == limit) {
				break;
			}
		}
		return List.copyOf(merchantIds);
	}
}
