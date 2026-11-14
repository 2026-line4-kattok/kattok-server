package com.kattok.server.user.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.global.ApiException;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.user.dto.AuthResponse;
import com.kattok.server.user.dto.LoginRequest;
import com.kattok.server.user.dto.SignupRequest;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	/** 없는 이메일로 로그인할 때 비교에 쓰는 BCrypt 해시. 어떤 비밀번호와도 맞지 않는다. */
	private static final String DUMMY_HASH = "$2y$10$Isi1VGK5EE2M8UcUPiNhh.UGh2sHoldPFPBT5hEM0/XB/WsAhYLhy";

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

	/**
	 * 이메일이 없거나 비밀번호가 틀리면 같은 401을 준다. 어느 쪽이 틀렸는지 알려 주면 가입한 이메일을
	 * 알아낼 수 있어서다. 같은 이유로 이메일이 없을 때도 비밀번호 비교를 한 번 해서 응답 시간을 맞춘다.
	 */
	public AuthResponse login(LoginRequest request) {
		Optional<User> found = userRepository.findByEmail(normalizeEmail(request.email()));
		String hash = found.map(User::getPasswordHash).orElse(DUMMY_HASH);
		boolean matches = passwordEncoder.matches(request.password(), hash);
		User user = found.filter(u -> matches)
				.orElseThrow(() -> new ApiException(
						HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않습니다."));
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
