package com.kattok.server.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 구현 전까지 임시로 테스트 계정을 현재 사용자로 사용한다. 회원가입·로그인은 정상적으로
 * 만들고, 시연 때는 미리 만들어 둔 이 테스트 계정으로 실제 로그인한다(2026-11 회의 결정).
 * 로그인이 붙으면 이 클래스를 인증된 사용자를 돌려주는 구현으로 교체한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserProvider {

	private static final String DEMO_EMAIL = "demo@kattok.app";

	private final UserRepository userRepository;

	public User get() {
		return userRepository.findByEmail(DEMO_EMAIL)
				.orElseThrow(() -> new IllegalStateException("데모 계정이 시드되지 않았습니다: " + DEMO_EMAIL));
	}
}
