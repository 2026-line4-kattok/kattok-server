package com.kattok.server.global;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link ResponseEntityExceptionHandler}를 상속해 Spring MVC가 원래 4xx/5xx로 처리하던 예외(경로 없음,
 * 지원하지 않는 메서드, 필수 파라미터 누락, 잘못된 JSON 본문 등)의 상태 코드는 그대로 두고 응답 본문만
 * {@link ErrorResponse} 형식으로 맞춘다. {@code @ExceptionHandler(Exception.class)}로 전부 받아버리면
 * 이 예외들도 500으로 바뀌어버리므로, 그 핸들러는 두지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(ErrorResponse.of(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		String message = "%s 값을 확인해 주세요.".formatted(e.getName());
		return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_INPUT", message));
	}

	/** {@link ResponseEntityExceptionHandler}가 모르는, 진짜 예상 못한 예외만 여기로 온다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리하지 못한 예외", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorResponse.FieldError(error.getField(), fieldMessage(error)))
				.toList();
		ErrorResponse body = ErrorResponse.of("INVALID_INPUT", "입력값을 확인해 주세요.", fieldErrors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * {@link ResponseEntityExceptionHandler}의 다른 handle* 메서드들은 대부분 본문 없이 이 메서드를 호출한다.
	 * 그런 경우(= 아직 {@link ErrorResponse}로 안 바뀐 경우)만 상태 코드에 맞는 기본 본문을 만들어 채운다.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		Object responseBody = body instanceof ErrorResponse
				? body
				: ErrorResponse.of(codeFor(statusCode), messageFor(statusCode));
		return super.handleExceptionInternal(ex, responseBody, headers, statusCode, request);
	}

	private static String fieldMessage(FieldError error) {
		return error.getDefaultMessage() != null ? error.getDefaultMessage() : "값을 확인해 주세요.";
	}

	private static String codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> "NOT_FOUND";
			case 405 -> "METHOD_NOT_ALLOWED";
			default -> status.is4xxClientError() ? "INVALID_INPUT" : "INTERNAL_ERROR";
		};
	}

	private static String messageFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> "요청한 경로를 찾을 수 없습니다.";
			case 405 -> "지원하지 않는 요청 방식입니다.";
			default -> status.is4xxClientError()
					? "요청을 확인해 주세요."
					: "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
		};
	}
}
