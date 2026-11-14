package com.kattok.server.global.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 문자열을 UTF-8로 바꿨을 때의 바이트 수 상한. 글자 수가 아닌 바이트 수를 본다.
 * BCrypt는 72바이트를 넘는 비밀번호를 받지 않으므로, 한글처럼 한 글자가 여러 바이트인 입력을 미리 막는 데 쓴다.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

	int value();

	String message() default "입력이 너무 깁니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
