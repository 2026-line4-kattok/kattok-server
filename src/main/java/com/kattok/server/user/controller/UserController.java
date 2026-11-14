package com.kattok.server.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kattok.server.user.dto.UserResponse;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final CurrentUserProvider currentUserProvider;

	/** 앱 진입 시 저장된 토큰이 아직 유효한지 확인할 때 쓴다. 401이면 로그인 화면으로 보낸다. */
	@GetMapping("/me")
	public UserResponse me() {
		return UserResponse.from(currentUserProvider.get());
	}
}
