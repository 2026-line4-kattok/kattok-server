package com.kattok.server.global.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 로그인한 사용자에게 줄 액세스 토큰을 만든다. 토큰의 {@code sub}는 사용자 id다. */
@Component
@RequiredArgsConstructor
public class TokenProvider {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;
	private final Clock clock = Clock.systemUTC();

	public IssuedToken issue(Long userId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.accessTokenValidity());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(SecurityConfig.ISSUER)
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(value, expiresAt);
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}
}
