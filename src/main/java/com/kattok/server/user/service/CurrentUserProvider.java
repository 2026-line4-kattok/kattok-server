package com.kattok.server.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.global.ApiException;
import com.kattok.server.global.security.AuthProperties;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 요청을 보낸 사용자를 돌려준다. 액세스 토큰이 있으면 토큰의 사용자이고, 없으면 401이다.
 *
 * <p>{@code kattok.auth.anonymous-demo-user}가 켜져 있으면(로컬 프로필 기본) 토큰 없는 요청을 테스트 계정으로
 * 처리한다. 프론트엔드가 로그인을 붙이기 전 API 연결 테스트용 임시 동작이다(2026-11 회의: 회원가입·로그인은
 * 그대로 만들고, 시연은 테스트 계정으로 로그인). 로그인 연결이 끝나면 이 설정을 없앤다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserProvider {

	private static final String DEMO_EMAIL = "demo@kattok.app";

	private final UserRepository userRepository;
	private final AuthProperties authProperties;

	public User get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			Long userId = Long.valueOf(token.getToken().getSubject());
			// 토큰은 유효하지만 사용자가 없는 경우(탈퇴 등). 다시 로그인해야 한다.
			return userRepository.findById(userId).orElseThrow(CurrentUserProvider::unauthorized);
		}
		if (!authProperties.anonymousDemoUser()) {
			throw unauthorized();
		}
		return userRepository.findByEmail(DEMO_EMAIL)
				.orElseThrow(() -> new IllegalStateException("데모 계정이 시드되지 않았습니다: " + DEMO_EMAIL));
	}

	private static ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
	}
}
