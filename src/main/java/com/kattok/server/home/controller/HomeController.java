package com.kattok.server.home.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.home.dto.HomeResponse;
import com.kattok.server.home.service.HomeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {
	private final HomeService service;

	@GetMapping
	public HomeResponse get() {
		return service.get();
	}
}
