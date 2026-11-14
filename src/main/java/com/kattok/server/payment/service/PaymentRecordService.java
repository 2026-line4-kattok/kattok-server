package com.kattok.server.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.payment.entity.PaymentRecord;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import com.kattok.server.recommendation.entity.Recommendation;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRecordService {

	private final PaymentRecordRepository paymentRecordRepository;

	/**
	 * 추천에 대한 결제 확인 대기 기록을 만든다. 이미 있으면 그대로 둔다(추천 1건당 1건).
	 * 같은 추천에 동시에 들어오는 요청은 호출하는 쪽에서 추천을 잠가 한 번에 하나만 들어오게 한다.
	 */
	@Transactional
	public PaymentRecord createPendingIfAbsent(Recommendation recommendation) {
		return paymentRecordRepository.findByRecommendationId(recommendation.getId())
				.orElseGet(() -> paymentRecordRepository.save(
						new PaymentRecord(recommendation.getUser(), recommendation)));
	}
}
