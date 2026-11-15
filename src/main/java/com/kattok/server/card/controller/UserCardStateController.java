package com.kattok.server.card.controller;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.ArrayList;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.card.dto.PerformanceInput;
import com.kattok.server.card.dto.UpdateCardStateRequest;
import com.kattok.server.card.dto.UpdateCardStateRequest.CapUpdate;
import com.kattok.server.card.dto.UserCardStateResponse;
import com.kattok.server.card.service.UserCardStateService;
import com.kattok.server.global.ApiException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PUT 요청 본문은 {@code @Valid}로 바로 바인딩하지 않고 {@link JsonNode}로 받는다. {@code performance}
 * 필드를 "안 보냄"과 "null로 보냄"을 구분해야 부분 수정(보낸 필드만 반영)이 가능한데, 일반 DTO 바인딩은
 * 둘 다 Java {@code null}이 되어 구분할 수 없기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/owned-cards/{id}/state")
@RequiredArgsConstructor
public class UserCardStateController {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserCardStateService userCardStateService;
	private final JsonMapper jsonMapper;
	private final Validator validator;

	@GetMapping
	public UserCardStateResponse getState(@PathVariable Long id, @RequestParam(required = false) String month,
			@RequestParam(required = false) LocalDate date) {
		return userCardStateService.getState(id, parseMonth(month), date);
	}

	@PutMapping
	public UserCardStateResponse updateState(@PathVariable Long id, @RequestBody JsonNode body) {
		UpdateCardStateRequest request = toUpdateRequest(body);
		validate(request);
		return userCardStateService.updateState(id, request);
	}

	private UpdateCardStateRequest toUpdateRequest(JsonNode body) {
		YearMonth month = parseRequiredMonth(body.get("month"));
		try {
			PerformanceInput performance = body.hasNonNull("performance")
					? jsonMapper.treeToValue(body.get("performance"), PerformanceInput.class)
					: null;
			List<CapUpdate> caps = null;
			if (body.hasNonNull("caps")) {
				if (!body.get("caps").isArray()) throw ApiException.invalidInput("caps는 배열이어야 합니다.");
				caps = new ArrayList<>();
				for (JsonNode node : body.get("caps")) {
					CapInput value = jsonMapper.treeToValue(node, CapInput.class);
					if (value == null || !node.has("remainingLimitAmount") && !node.has("remainingCount"))
						throw ApiException.invalidInput("변경할 잔여 금액 또는 횟수가 필요합니다.");
					caps.add(new CapUpdate(value.capGroupId(), value.remainingLimitAmount(), value.remainingCount(),
							node.has("remainingLimitAmount"), node.has("remainingCount"), value.periodStart()));
				}
			}
			return new UpdateCardStateRequest(month, performance, caps);
		} catch (DatabindException e) {
			throw ApiException.invalidInput("입력값을 확인해 주세요.");
		}
	}

	private record CapInput(Long capGroupId, Long remainingLimitAmount, Integer remainingCount, LocalDate periodStart) {}

	private void validate(UpdateCardStateRequest request) {
		Set<ConstraintViolation<UpdateCardStateRequest>> violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw ApiException.invalidInput("입력값을 확인해 주세요.");
		}
	}

	private static YearMonth parseRequiredMonth(JsonNode monthNode) {
		if (monthNode == null || monthNode.isNull()) {
			return null;
		}
		try {
			return YearMonth.parse(monthNode.asString());
		} catch (DateTimeException e) {
			throw ApiException.invalidInput("month 형식을 확인해 주세요.");
		}
	}

	private static YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			return YearMonth.now(KST);
		}
		try {
			return YearMonth.parse(month);
		} catch (DateTimeException e) {
			throw ApiException.invalidInput("month 형식을 확인해 주세요.");
		}
	}
}
