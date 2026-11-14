package com.kattok.server.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시연용 테스트 계정(demo@kattok.app)의 비밀번호를 서버 시작 때 {@code kattok.demo.password}로 맞춘다.
 * 비밀번호를 코드나 마이그레이션에 남기지 않으려고 환경변수({@code DEMO_USER_PASSWORD})로만 받는다.
 * 값이 없으면 아무것도 하지 않으며, 이때 테스트 계정으로는 로그인할 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountPasswordInitializer implements ApplicationRunner {

	static final String DEMO_EMAIL = "demo@kattok.app";
	private static final int MIN_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${kattok.demo.password:}")
	private String demoPassword;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (demoPassword == null || demoPassword.isBlank()) {
			return;
		}
		if (demoPassword.length() < MIN_LENGTH) {
			throw new IllegalStateException("kattok.demo.password는 8자 이상이어야 합니다.");
		}
		userRepository.findByEmail(DEMO_EMAIL).ifPresentOrElse(
				user -> {
					if (!passwordEncoder.matches(demoPassword, user.getPasswordHash())) {
						user.changePasswordHash(passwordEncoder.encode(demoPassword));
						log.info("테스트 계정 비밀번호를 설정했습니다.");
					}
				},
				() -> log.warn("테스트 계정이 없어 비밀번호를 설정하지 못했습니다: {}", DEMO_EMAIL));
	}
}
