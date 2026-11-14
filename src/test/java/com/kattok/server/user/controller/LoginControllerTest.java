package com.kattok.server.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.kattok.server.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LoginControllerTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	private String email;

	@BeforeEach
	void signup() throws Exception {
		email = "login-" + UUID.randomUUID() + "@kattok.test";
		String body = """
				{"email": "%s", "password": "%s", "termsAgreed": true}
				""".formatted(email, PASSWORD);
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("맞는 이메일과 비밀번호면 200과 토큰을 반환한다")
	void login() throws Exception {
		login(email, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value(email));
	}

	@Test
	@DisplayName("이메일 대소문자가 달라도 로그인된다")
	void emailIsCaseInsensitive() throws Exception {
		login(email.toUpperCase(), PASSWORD).andExpect(status().isOk());
	}

	@Test
	@DisplayName("비밀번호가 틀리면 401 INVALID_CREDENTIALS")
	void wrongPassword() throws Exception {
		login(email, "wrong-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	@DisplayName("가입하지 않은 이메일도 같은 401 INVALID_CREDENTIALS")
	void unknownEmail() throws Exception {
		login("nobody-" + UUID.randomUUID() + "@kattok.test", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	@DisplayName("이메일을 비우면 400 INVALID_INPUT")
	void blankEmail() throws Exception {
		login("", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	private ResultActions login(String email, String password) throws Exception {
		String body = """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
