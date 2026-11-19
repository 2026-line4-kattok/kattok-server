package com.kattok.server.payment.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.payment.entity.PaymentRecordStatus;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;

/** 전체 결제 완료 기록을 DB에서 결제처별·업종별로 집계해 각각 상위 항목만 조회한 뒤 합친다. */
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
		var page = PageRequest.of(0, limit);
		Stream<RecentMerchant> merchants = payments.findRecentMerchants(userId, PAID_RESULTS, page).stream()
				.map(row -> new RecentMerchant(row.getMerchantId(), null, row.getLastRecommendedAt()));
		Stream<RecentMerchant> categories = payments.findRecentCategories(userId, PAID_RESULTS, page).stream()
				.map(row -> new RecentMerchant(null, row.getCategoryId(), row.getLastRecommendedAt()));
		return Stream.concat(merchants, categories)
				.sorted(Comparator.comparing(RecentMerchant::lastRecommendedAt).reversed()
						.thenComparing(row -> row.merchantId() == null)
						.thenComparing(row -> row.merchantId() != null ? row.merchantId() : row.categoryId()))
				.limit(limit)
				.toList();
	}
}
