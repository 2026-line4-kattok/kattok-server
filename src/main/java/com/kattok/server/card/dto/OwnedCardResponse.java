package com.kattok.server.card.dto;

import java.time.Instant;

import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.entity.UserCard;

public record OwnedCardResponse(
		Long id,
		ProductSummary product,
		PerformanceStatus performanceStatus,
		PerformanceTierSummary performanceTier,
		String representativeBenefit,
		StatusChip statusChip,
		Instant createdAt) {

	public static OwnedCardResponse of(
			UserCard userCard, PerformanceStatus performanceStatus, CardPerformanceTier performanceTier,
			String representativeBenefit, StatusChip statusChip) {
		CardProduct product = userCard.getProduct();
		return new OwnedCardResponse(
				userCard.getId(),
				new ProductSummary(
						product.getId(), product.getName(), product.getIssuer().getName(), product.getCardType(),
						product.getDataStatus(), product.getPerformanceType()),
				performanceStatus,
				performanceTier == null ? null : new PerformanceTierSummary(performanceTier.getId(), performanceTier.getLabel()),
				representativeBenefit,
				statusChip,
				userCard.getCreatedAt());
	}

	public record ProductSummary(
			Long id, String name, String issuerName, CardType cardType, DataStatus dataStatus,
			PerformanceType performanceType) {
	}

	public record PerformanceTierSummary(Long id, String label) {
	}
}
