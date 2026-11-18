package com.kattok.server.recommendation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.recommendation.dto.ConfirmRecommendationRequest;
import com.kattok.server.recommendation.dto.CreateRecommendationRequest;
import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.recommendation.service.RecommendationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RecommendationResponse create(@Valid @RequestBody CreateRecommendationRequest request) {
		return recommendationService.create(request);
	}

	@GetMapping("/{id}")
	public RecommendationResponse get(@PathVariable Long id) {
		return recommendationService.get(id);
	}

	/** 본문 없이 호출하면 추천 1순위 카드 그대로 확정한다. {@code userCardId}를 보내면 비교 결과 중 그 카드로 바꿔서 확정한다. */
	@PostMapping("/{id}/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmRecommendationRequest request) {
		recommendationService.confirm(id, request == null ? null : request.userCardId());
	}
}
