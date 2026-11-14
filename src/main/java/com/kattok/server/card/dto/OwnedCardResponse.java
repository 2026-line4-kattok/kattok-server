package com.kattok.server.card.dto;

import java.time.Instant;

import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.UserCard;

public record OwnedCardResponse(
		Long id,
		ProductSummary product,
		PerformanceStatus performanceStatus,
		PerformanceTierSummary performanceTier,
		Instant createdAt) {

	public static OwnedCardResponse of(
			UserCard userCard, PerformanceStatus performanceStatus, CardPerformanceTier performanceTier) {
		CardProduct product = userCard.getProduct();
		return new OwnedCardResponse(
				userCard.getId(),
				new ProductSummary(
						product.getId(), product.getName(), product.getIssuer().getName(), product.getCardType(),
						product.getDataStatus()),
				performanceStatus,
				performanceTier == null ? null : new PerformanceTierSummary(performanceTier.getId(), performanceTier.getLabel()),
				userCard.getCreatedAt());
	}

	public record ProductSummary(Long id, String name, String issuerName, CardType cardType, DataStatus dataStatus) {
	}

	public record PerformanceTierSummary(Long id, String label) {
	}
}
