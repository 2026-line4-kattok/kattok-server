package com.kattok.server.user.dto;

import com.kattok.server.user.entity.User;

public record UserResponse(Long id, String email) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail());
	}
}
