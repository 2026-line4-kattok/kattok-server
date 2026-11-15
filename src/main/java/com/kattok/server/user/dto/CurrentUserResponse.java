package com.kattok.server.user.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.kattok.server.user.entity.User;

public record CurrentUserResponse(Long id, String email, OffsetDateTime createdAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static CurrentUserResponse from(User user) {
		return new CurrentUserResponse(
			user.getId(),
			user.getEmail(),
			user.getCreatedAt().atZone(KST).toOffsetDateTime()
		);
	}
}
