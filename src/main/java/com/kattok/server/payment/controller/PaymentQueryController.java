package com.kattok.server.payment.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.kattok.server.payment.dto.PaymentResponses;
import com.kattok.server.payment.service.PaymentQueryService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentQueryController {
	private final PaymentQueryService service;

	@GetMapping("/api/v1/recommendations/{id}/payment")
	public PaymentResponses.Options options(@PathVariable Long id) { return service.options(id); }

	@GetMapping("/api/v1/payment-records/pending")
	public List<PaymentResponses.Record> pending() { return service.pending(); }

	@GetMapping("/api/v1/payment-records")
	public PaymentResponses.History history(@RequestParam(required = false) String month,
			@RequestParam(required = false) Long userCardId) { return service.history(month, userCardId); }
}
