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
import com.kattok.server.global.ApiException;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/owned-cards")
@RequiredArgsConstructor
public class OwnedCardController {

	private final OwnedCardService ownedCardService;
	private final JsonMapper jsonMapper;
	private final Validator validator;

	@GetMapping
	public List<OwnedCardResponse> findAll() {
		return ownedCardService.findAll();
	}

	@GetMapping("/{id}")
	public OwnedCardDetailResponse getDetail(@PathVariable Long id) {
		return ownedCardService.getDetail(id);
	}

	/** 본문을 {@link JsonNode}로 받아 {@code performance}의 "안 보냄"과 "null로 보냄"을 구분한다. 명시적 null은 어떤 카드에서도 400이다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OwnedCardResponse register(@RequestBody JsonNode body) {
		if (body.has("performance") && body.get("performance").isNull()) {
			throw ApiException.invalidInput("performance에 null을 보낼 수 없습니다. 실적 조건이 없는 카드는 필드를 보내지 않습니다.");
		}
		RegisterCardRequest request;
		try {
			request = jsonMapper.treeToValue(body, RegisterCardRequest.class);
		} catch (DatabindException e) {
			throw ApiException.invalidInput("입력값을 확인해 주세요.");
		}
		if (request == null || !validator.validate(request).isEmpty()) {
			throw ApiException.invalidInput("입력값을 확인해 주세요.");
		}
		return ownedCardService.register(request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		ownedCardService.delete(id);
	}
}
