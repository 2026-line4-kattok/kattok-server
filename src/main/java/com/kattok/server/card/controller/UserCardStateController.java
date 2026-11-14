package com.kattok.server.card.controller;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.card.dto.UpdateCardStateRequest;
import com.kattok.server.card.dto.UserCardStateResponse;
import com.kattok.server.card.service.UserCardStateService;
import com.kattok.server.global.ApiException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/owned-cards/{id}/state")
@RequiredArgsConstructor
public class UserCardStateController {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserCardStateService userCardStateService;

	@GetMapping
	public UserCardStateResponse getState(@PathVariable Long id, @RequestParam(required = false) String month) {
		return userCardStateService.getState(id, parseMonth(month));
	}

	@PutMapping
	public UserCardStateResponse updateState(@PathVariable Long id, @Valid @RequestBody UpdateCardStateRequest request) {
		return userCardStateService.updateState(id, request);
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
