package com.kattok.server.global;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * docs/conventions.md의 에러 응답 형식. {@code fieldErrors}는 입력 검증 실패일 때만 채운다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(code, message, null);
	}

	public static ErrorResponse of(String code, String message, List<FieldError> fieldErrors) {
		return new ErrorResponse(code, message, fieldErrors);
	}

	public record FieldError(String field, String message) {
	}
}
