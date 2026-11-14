package com.kattok.server.card.dto;

import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;

public record CardProductResponse(
		Long id,
		String name,
		IssuerSummary issuer,
		CardType cardType,
		Long annualFee,
		DataStatus dataStatus,
		boolean owned) {

	public static CardProductResponse of(CardProduct product, boolean owned) {
		return new CardProductResponse(
				product.getId(),
				product.getName(),
				new IssuerSummary(product.getIssuer().getId(), product.getIssuer().getName()),
				product.getCardType(),
				product.getAnnualFee(),
				product.getDataStatus(),
				owned);
	}

	public record IssuerSummary(Long id, String name) {
	}
}
