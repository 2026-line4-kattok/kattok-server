package com.kattok.server.card.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.card.dto.OwnedCardDetailResponse;
import com.kattok.server.card.dto.OwnedCardResponse;
import com.kattok.server.card.dto.RegisterCardRequest;
import com.kattok.server.card.service.OwnedCardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/owned-cards")
@RequiredArgsConstructor
public class OwnedCardController {

	private final OwnedCardService ownedCardService;

	@GetMapping
	public List<OwnedCardResponse> findAll() {
		return ownedCardService.findAll();
	}

	@GetMapping("/{id}")
	public OwnedCardDetailResponse getDetail(@PathVariable Long id) {
		return ownedCardService.getDetail(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OwnedCardResponse register(@Valid @RequestBody RegisterCardRequest request) {
		return ownedCardService.register(request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		ownedCardService.delete(id);
	}
}
