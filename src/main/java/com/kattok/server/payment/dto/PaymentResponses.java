package com.kattok.server.payment.dto;

import java.time.Instant;
import java.util.List;

import com.kattok.server.recommendation.dto.RecommendationResponse.IdName;

public final class PaymentResponses {
	private PaymentResponses() {}

	public record Card(Long userCardId, String productName) {}
	public record RecommendedCard(Long userCardId, String productName, Long expectedBenefit, boolean deleted) {}
	public record CardOption(Long userCardId, String productName, String issuerName, Long expectedBenefit) {}
	public record Record(Long recommendationId, String result, IdName merchant, IdName category, long amount,
			RecommendedCard recommendedCard, Card userCard, Long savedBenefit, Instant recommendedAt,
			Instant confirmedAt) {}
	public record Options(Long recommendationId, String result, IdName merchant, IdName category, long amount,
			RecommendedCard recommendedCard, Card userCard, Long savedBenefit, Instant recommendedAt,
			Instant confirmedAt, List<CardOption> cardOptions) {}
	public record SavedBenefit(long amount, long paymentCount) {}
	public record ByCard(Long userCardId, String productName, long amount, long paymentCount) {}
	public record History(String month, SavedBenefit savedBenefit, List<ByCard> byCard, long pendingCount,
			List<Record> records) {}
}
