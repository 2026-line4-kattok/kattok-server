package com.kattok.server.payment.repository;

import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import java.util.List;
import com.kattok.server.payment.entity.PaymentRecordStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.payment.entity.PaymentRecord;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

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

	Optional<PaymentRecord> findByRecommendationId(Long recommendationId);
}
