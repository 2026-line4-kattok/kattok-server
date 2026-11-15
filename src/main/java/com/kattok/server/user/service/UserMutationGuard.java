package com.kattok.server.user.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import com.kattok.server.global.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/** 같은 사용자의 상태 변경을 직렬화한다. 모든 호출은 사용자 → 추천 → 결제 순서로 잠근다. */
@Service
@RequiredArgsConstructor
public class UserMutationGuard {
	private final JdbcTemplate jdbc;

	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(Long userId) {
		if (jdbc.queryForList("select id from users where id = ? for update", Long.class, userId).isEmpty()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
		}
	}
}
