package com.kattok.server.global.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.kattok.server.global.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터에서 막힌 요청도 컨트롤러 에러와 같은 {@link ErrorResponse} 형식으로 응답한다. 필터 단계는
 * {@code GlobalExceptionHandler}를 거치지 않아서 따로 둔다.
 */
@Component
@RequiredArgsConstructor
public class JsonErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	/** 토큰이 없거나, 만료됐거나, 서명이 맞지 않을 때. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
			throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, ErrorResponse.of("UNAUTHORIZED", "로그인이 필요합니다."));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
			throws IOException {
		write(response, HttpStatus.FORBIDDEN, ErrorResponse.of("FORBIDDEN", "접근 권한이 없습니다."));
	}

	private void write(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), body);
	}
}
