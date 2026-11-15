package com.kattok.server.recommendation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.kattok.server.recommendation.entity.Recommendation;

import jakarta.persistence.LockModeType;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	/** 다른 사용자의 추천은 없는 것으로 본다. */
	Optional<Recommendation> findByIdAndUserId(Long id, Long userId);

	/** "최근 결제한 곳" 칩에 쓴다. 결제처를 고른 추천(업종만 고른 추천은 제외) 중 확정한 것만, 최신순. */
	List<Recommendation> findByUserIdAndMerchantIsNotNullAndSavedAtIsNotNullOrderBySavedAtDesc(
			Long userId, Pageable pageable);

	/** 결제 확인 대기 기록을 만들 때 같은 추천에 대한 동시 요청을 한 번에 하나씩 처리하려고 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Recommendation> findWithLockByIdAndUserId(Long id, Long userId);
}
