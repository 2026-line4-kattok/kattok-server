package com.kattok.server.merchant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.merchant.dto.MerchantCategoryResponse;
import com.kattok.server.merchant.service.MerchantService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/merchant-categories")
@RequiredArgsConstructor
public class MerchantCategoryController {

	private final MerchantService merchantService;

	@GetMapping
	public List<MerchantCategoryResponse> list() {
		return merchantService.categories();
	}
}
