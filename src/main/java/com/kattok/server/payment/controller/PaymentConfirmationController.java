package com.kattok.server.payment.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.kattok.server.payment.dto.ConfirmPaymentRequest;
import com.kattok.server.payment.service.PaymentConfirmationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentConfirmationController {
	private final PaymentConfirmationService service;

	@PutMapping("/api/v1/recommendations/{id}/payment")
	public PaymentConfirmationService.Response confirm(@PathVariable Long id, @Valid @RequestBody ConfirmPaymentRequest request) {
		return service.confirm(id, request);
	}
}
