package com.kattok.server.card.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.dto.CardProductResponse;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardProductService {

	private final CardProductRepository cardProductRepository;
	private final UserCardRepository userCardRepository;
	private final CurrentUserProvider currentUserProvider;

	public List<CardProductResponse> search(Long issuerId, String keyword, CardType cardType) {
		Long userId = currentUserProvider.get().getId();
		Set<Long> ownedProductIds = userCardRepository.findActiveProductIdsByUserId(userId);

		List<CardProduct> products = cardProductRepository.search(
				issuerId, blankToNull(keyword), cardType == null ? null : cardType.name());

		return products.stream()
				.map(product -> CardProductResponse.of(product, ownedProductIds.contains(product.getId())))
				.toList();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
