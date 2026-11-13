package com.kattok.server.card.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.card.dto.CardIssuerResponse;
import com.kattok.server.card.service.CardIssuerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/card-issuers")
@RequiredArgsConstructor
public class CardIssuerController {

	private final CardIssuerService cardIssuerService;

	@GetMapping
	public List<CardIssuerResponse> findAll() {
		return cardIssuerService.findAll();
	}
}
