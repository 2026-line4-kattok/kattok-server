package com.kattok.server.payment.repository;

import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import com.kattok.server.payment.entity.PaymentRecordStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.payment.entity.PaymentRecord;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

	long countByUserId(Long userId);

	List<PaymentRecord> findByUserIdAndStatusIn(Long userId, Collection<PaymentRecordStatus> statuses);

	List<PaymentRecord> findByUserIdAndStatus(Long userId, PaymentRecordStatus status);

	@Query("""
			select p from PaymentRecord p join fetch p.recommendation r
			where p.user.id = :userId and p.status <> :pending
			and r.createdAt >= :start and r.createdAt < :end
			""")
	List<PaymentRecord> findConfirmedInMonth(@Param("userId") Long userId,
			@Param("pending") PaymentRecordStatus pending,
			@Param("start") Instant start, @Param("end") Instant end);

	long countByUserIdAndStatus(Long userId, PaymentRecordStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PaymentRecord p where p.recommendation.id = :id and p.user.id = :userId")
	Optional<PaymentRecord> findForUpdate(@Param("id") Long recommendationId, @Param("userId") Long userId);

	@Query("""
			select r.merchant.id as merchantId, max(r.createdAt) as lastRecommendedAt
			from PaymentRecord p join p.recommendation r
			where p.user.id = :userId and r.user.id = :userId
			and p.status in :paidResults and r.merchant is not null
			group by r.merchant.id
			order by max(r.createdAt) desc, r.merchant.id asc
			""")
	List<RecentMerchantProjection> findRecentMerchants(@Param("userId") Long userId,
			@Param("paidResults") Collection<PaymentRecordStatus> paidResults, Pageable pageable);

	/** 결제처 없이 업종으로만 고른 결제 완료 기록을 업종별로 집계한다. */
	@Query("""
			select r.category.id as categoryId, max(r.createdAt) as lastRecommendedAt
			from PaymentRecord p join p.recommendation r
			where p.user.id = :userId and r.user.id = :userId
			and p.status in :paidResults and r.merchant is null
			group by r.category.id
			order by max(r.createdAt) desc, r.category.id asc
			""")
	List<RecentCategoryProjection> findRecentCategories(@Param("userId") Long userId,
			@Param("paidResults") Collection<PaymentRecordStatus> paidResults, Pageable pageable);

	interface RecentCategoryProjection {
		Long getCategoryId();
		Instant getLastRecommendedAt();
	}

	interface RecentMerchantProjection {
		Long getMerchantId();
		Instant getLastRecommendedAt();
	}

	Optional<PaymentRecord> findByRecommendationId(Long recommendationId);
}
