package com.kattok.server.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.global.ApiException;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 요청을 보낸 사용자를 돌려준다. 액세스 토큰이 있으면 토큰의 사용자다.
 *
 * <p>토큰이 없으면 임시로 테스트 계정을 쓴다. 프론트엔드가 로그인을 붙이기 전까지 API 연결 테스트를 막지
 * 않기 위해서다(2026-11 회의: 회원가입·로그인은 그대로 만들고, 시연은 테스트 계정으로 로그인). 로그인
 * 연결이 끝나면 회원가입·로그인을 뺀 API를 인증 필수로 바꾸고 이 대체 동작을 없앤다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserProvider {

	private static final String DEMO_EMAIL = "demo@kattok.app";

	private final UserRepository userRepository;

	public User get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			Long userId = Long.valueOf(token.getToken().getSubject());
			// 토큰은 유효하지만 사용자가 없는 경우(탈퇴 등). 다시 로그인해야 한다.
			return userRepository.findById(userId)
					.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
		}
		return userRepository.findByEmail(DEMO_EMAIL)
				.orElseThrow(() -> new IllegalStateException("데모 계정이 시드되지 않았습니다: " + DEMO_EMAIL));
	}
}
