package com.kattok.server.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.payment.entity.PaymentRecord;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

	Optional<PaymentRecord> findByRecommendationId(Long recommendationId);
}
