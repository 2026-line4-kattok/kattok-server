package com.kattok.server.card.dto;

import com.kattok.server.card.entity.CardIssuer;

public record CardIssuerResponse(Long id, String name, String code) {

	public static CardIssuerResponse from(CardIssuer issuer) {
		return new CardIssuerResponse(issuer.getId(), issuer.getName(), issuer.getCode());
	}
}
