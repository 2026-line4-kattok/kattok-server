package com.kattok.server.user.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.global.ApiException;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.user.dto.AuthResponse;
import com.kattok.server.user.dto.SignupRequest;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenProvider tokenProvider;

	/** 가입하면 바로 로그인된 상태로 토큰을 준다. */
	@Transactional
	public AuthResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.findByEmail(email).isPresent()) {
			throw emailAlreadyRegistered();
		}
		User user;
		try {
			user = userRepository.saveAndFlush(
					new User(email, passwordEncoder.encode(request.password()), Instant.now()));
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일로 동시에 가입한 경우. 먼저 들어온 요청만 성공한다.
			throw emailAlreadyRegistered();
		}
		return AuthResponse.of(tokenProvider.issue(user.getId()), user);
	}

	/** 대소문자와 앞뒤 공백이 달라도 같은 이메일로 본다. */
	static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static ApiException emailAlreadyRegistered() {
		return ApiException.conflict("EMAIL_ALREADY_REGISTERED", "이미 가입한 이메일입니다.");
	}
}
