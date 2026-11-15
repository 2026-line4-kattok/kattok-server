package com.kattok.server.payment.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.payment.entity.PaymentRecordStatus;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;

/** 전체 결제 완료 기록을 DB에서 결제처별로 집계한 뒤 상위 항목만 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaRecentMerchantReader implements RecentMerchantReader {
	private static final Set<PaymentRecordStatus> PAID_RESULTS = Set.of(
			PaymentRecordStatus.PAID_RECOMMENDED, PaymentRecordStatus.PAID_OTHER, PaymentRecordStatus.PAID_UNREGISTERED);
	private final PaymentRecordRepository payments;

	@Override
	public List<RecentMerchant> recentMerchants(Long userId, int limit) {
		if (limit <= 0) return List.of();
		return payments.findRecentMerchants(userId, PAID_RESULTS, PageRequest.of(0, limit)).stream()
				.map(row -> new RecentMerchant(row.getMerchantId(), row.getLastRecommendedAt())).toList();
	}
}
