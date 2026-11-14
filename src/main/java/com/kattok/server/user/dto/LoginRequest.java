package com.kattok.server.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Size(max = 254, message = "이메일은 254자 이하로 입력해 주세요.")
		String email,

		/** 가입 규칙과 같은 64자 상한. 8자 미만은 막지 않는다. 규칙이 바뀌어도 기존 비밀번호로 로그인할 수 있게 한다. */
		@NotNull(message = "비밀번호를 입력해 주세요.")
		@Size(max = 64, message = "비밀번호는 64자 이하로 입력해 주세요.")
		String password) {

	public LoginRequest {
		email = email == null ? null : email.trim();
	}
}
