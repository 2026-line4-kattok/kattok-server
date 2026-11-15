package com.kattok.server.card.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.dto.CardIssuerResponse;
import com.kattok.server.card.repository.CardIssuerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardIssuerService {

	private final CardIssuerRepository cardIssuerRepository;

	public List<CardIssuerResponse> findAll() {
		return cardIssuerRepository.findAllWithProductsOrderByNameAsc().stream()
				.map(CardIssuerResponse::from)
				.toList();
	}
}
