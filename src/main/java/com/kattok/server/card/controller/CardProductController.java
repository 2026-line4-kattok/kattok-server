package com.kattok.server.card.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.card.dto.CardProductDetailResponse;
import com.kattok.server.card.dto.CardProductResponse;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.service.CardProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/card-products")
@RequiredArgsConstructor
public class CardProductController {

	private final CardProductService cardProductService;

	@GetMapping
	public List<CardProductResponse> search(
			@RequestParam(required = false) Long issuerId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) CardType cardType) {
		return cardProductService.search(issuerId, keyword, cardType);
	}

	@GetMapping("/{id}")
	public CardProductDetailResponse getDetail(@PathVariable Long id) {
		return cardProductService.getDetail(id);
	}
}
