package com.kattok.server.merchant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.merchant.dto.MerchantResponse;
import com.kattok.server.merchant.service.MerchantService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

	private final MerchantService merchantService;

	@GetMapping
	public List<MerchantResponse> search(@RequestParam(required = false) String keyword) {
		return merchantService.search(keyword);
	}

	@GetMapping("/recent")
	public List<MerchantResponse> recent() {
		return merchantService.recent();
	}
}
