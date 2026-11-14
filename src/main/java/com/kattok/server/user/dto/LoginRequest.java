package com.kattok.server.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		String email,

		@NotNull(message = "비밀번호를 입력해 주세요.")
		String password) {

	public LoginRequest {
		email = email == null ? null : email.trim();
	}
}
