package com.kattok.server.home.dto;

import com.kattok.server.payment.dto.PaymentResponses;

public record HomeResponse(PaymentResponses.Record pendingPayment, long pendingCount, SavedBenefit savedBenefit) {
	public record SavedBenefit(String month, long amount, long paymentCount) {}
}
