package com.kattok.server.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.global.security.TokenProvider;

/** 요청을 보낸 사용자를 토큰으로 정하는지 확인한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CurrentUserTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenProvider tokenProvider;

	@Test
	@DisplayName("가입 때 받은 토큰으로 호출하면 그 사용자로 처리한다")
	void tokenUser() throws Exception {
		String email = "me-" + UUID.randomUUID() + "@kattok.test";
		String body = """
				{"email": "%s", "password": "password123", "termsAgreed": true}
				""".formatted(email);
		String response = mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(response, "$.accessToken");

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	@DisplayName("토큰이 없으면 임시로 테스트 계정으로 처리한다")
	void withoutTokenUsesDemoAccount() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("demo@kattok.app"));
	}

	@Test
	@DisplayName("토큰의 사용자가 없으면 401 UNAUTHORIZED")
	void tokenForMissingUser() throws Exception {
		String token = tokenProvider.issue(Long.MAX_VALUE).value();

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
