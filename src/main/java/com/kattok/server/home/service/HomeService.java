package com.kattok.server.home.service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.home.dto.HomeResponse;
import com.kattok.server.payment.service.PaymentQueryService;

@Service
public class HomeService {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final PaymentQueryService payments;
	private final Clock clock;

	public HomeService(PaymentQueryService payments, @Qualifier("homeClock") Clock clock) {
		this.payments = payments;
		this.clock = clock;
	}

	/** 조회 도중 확인된 결제가 있어도 대기 카드·건수·혜택을 같은 DB 시점으로 반환한다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public HomeResponse get() {
		String month = YearMonth.from(Instant.now(clock).atZone(KST)).toString();
		var pending = payments.pending();
		var history = payments.history(month, null);
		return new HomeResponse(pending.isEmpty() ? null : pending.getFirst(), history.pendingCount(),
				new HomeResponse.SavedBenefit(month, history.savedBenefit().amount(), history.savedBenefit().paymentCount()));
	}
}
