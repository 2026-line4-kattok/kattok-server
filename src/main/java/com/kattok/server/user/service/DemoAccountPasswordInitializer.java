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
 * 값이 없으면 로그인할 수 없는 값으로 되돌린다. 환경변수를 지우면 테스트 계정 로그인도 막힌다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountPasswordInitializer implements ApplicationRunner {

	static final String DEMO_EMAIL = "demo@kattok.app";
	/** 어떤 비밀번호와도 맞지 않는 값(V22와 같음). */
	private static final String NO_LOGIN = "DEMO-NO-LOGIN";
	private static final int MIN_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${kattok.demo.password:}")
	private String demoPassword;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (demoPassword == null || demoPassword.isBlank()) {
			userRepository.findByEmail(DEMO_EMAIL)
					.filter(user -> !NO_LOGIN.equals(user.getPasswordHash()))
					.ifPresent(user -> {
						user.changePasswordHash(NO_LOGIN);
						log.info("DEMO_USER_PASSWORD가 없어 테스트 계정 로그인을 막았습니다.");
					});
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
