package com.kattok.server.global.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import com.kattok.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/** 서명이 유효해도 계정이 삭제된 JWT는 모든 인증 경로에서 거절한다. */
@Component
@RequiredArgsConstructor
public class ActiveUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
	private final UserRepository users;
	private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		long id;
		try {
			String subject = jwt.getSubject();
			if (subject == null || !subject.matches("[1-9][0-9]*")) throw invalid();
			id = Long.parseLong(subject);
		} catch (IllegalArgumentException e) {
			throw invalid();
		}
		if (!users.existsById(id)) throw invalid();
		return delegate.convert(jwt);
	}

	private static OAuth2AuthenticationException invalid() {
		return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "유효한 사용자 인증이 필요합니다.");
	}
}
