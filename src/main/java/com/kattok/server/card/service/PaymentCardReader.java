package com.kattok.server.card.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.global.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCardReader {
	private final UserCardRepository cards;

	public Card owned(Long userId, Long id) {
		return cards.findByIdAndUserId(id, userId).map(PaymentCardReader::map)
				.orElseThrow(() -> ApiException.notFound("OWNED_CARD_NOT_FOUND", "존재하지 않는 카드입니다."));
	}

	public List<Card> active(Long userId) {
		return cards.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
				.sorted(Comparator.comparing(UserCard::getCreatedAt).thenComparing(UserCard::getId))
				.map(PaymentCardReader::map).toList();
	}

	private static Card map(UserCard card) {
		return new Card(card.getId(), card.getProduct().getName(), card.getProduct().getIssuer().getName(),
				card.getDeletedAt() != null);
	}

	public record Card(Long userCardId, String productName, String issuerName, boolean deleted) {}
}
