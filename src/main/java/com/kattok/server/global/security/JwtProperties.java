package com.kattok.server.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 액세스 토큰 설정.
 *
 * @param secret HS256 서명 키. 32바이트(256비트) 이상이어야 한다. 운영에서는 환경변수로만 넣는다.
 * @param accessTokenValidity 토큰 유효 기간. 리프레시 토큰이 없으므로 이 기간이 자동 로그인 유지 기간이다.
 */
@ConfigurationProperties(prefix = "kattok.jwt")
public record JwtProperties(String secret, Duration accessTokenValidity) {

	private static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("kattok.jwt.secret은 32바이트 이상이어야 합니다.");
		}
		if (accessTokenValidity == null) {
			accessTokenValidity = Duration.ofDays(14);
		}
	}
}
