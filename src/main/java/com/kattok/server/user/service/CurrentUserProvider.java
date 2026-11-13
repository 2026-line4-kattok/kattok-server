package com.kattok.server.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 없이 데모 계정 하나로 운영하기로 한 팀 결정(2026-11)에 따라, 모든 요청을 이 계정의 것으로 취급한다.
 * 실제 로그인이 생기면 이 클래스를 인증된 사용자를 돌려주는 구현으로 교체한다.
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
