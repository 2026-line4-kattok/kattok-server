package com.kattok.server.global;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 HTTP 상태 코드와 {@code code}로 변환해 응답하기 위한 예외.
 * {@code code}는 프론트엔드가 분기에 쓰는 값이므로 한 번 정하면 임의로 바꾸지 않는다.
 */
@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException invalidInput(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}
}
