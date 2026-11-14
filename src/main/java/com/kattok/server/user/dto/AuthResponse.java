package com.kattok.server.user.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.kattok.server.global.security.TokenProvider.IssuedToken;
import com.kattok.server.user.entity.User;

/**
 * 회원가입·로그인 응답. 이후 요청은 {@code Authorization: Bearer <accessToken>} 헤더로 보낸다.
 *
 * @param expiresAt 토큰 만료 시각(한국 시간). 지나면 다시 로그인한다.
 */
public record AuthResponse(String accessToken, String tokenType, OffsetDateTime expiresAt, UserResponse user) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static AuthResponse of(IssuedToken token, User user) {
		return new AuthResponse(
				token.value(), "Bearer", token.expiresAt().atZone(KST).toOffsetDateTime(), UserResponse.from(user));
	}
}
