package com.kattok.server.user.dto;

import com.kattok.server.global.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식을 확인해 주세요.")
		@Size(max = 254, message = "이메일은 254자 이하로 입력해 주세요.")
		String email,

		@NotNull(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
		@MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다. 한글은 24자까지 입력할 수 있어요.")
		String password,

		@NotNull(message = "약관에 동의해 주세요.")
		@AssertTrue(message = "약관에 동의해 주세요.")
		Boolean termsAgreed) {

	/** 형식 검사 전에 앞뒤 공백을 뺀다. 붙여 넣기로 들어온 공백 때문에 가입이 막히지 않게 한다. */
	public SignupRequest {
		email = email == null ? null : email.trim();
	}
}
