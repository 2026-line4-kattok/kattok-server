package com.kattok.server.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;

/** 테스트 계정 대체 동작을 끈 상태(운영 기본값)에서는 토큰이 필요한지 확인한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
class AuthRequiredTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenProvider tokenProvider;

	@Test
	@DisplayName("토큰 없이 일반 API를 부르면 401 UNAUTHORIZED")
	void withoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("로그인은 토큰 없이 부를 수 있다")
	void loginIsPublic() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "nobody@kattok.test", "password": "password123"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	@DisplayName("발급한 토큰이 있으면 일반 API를 부를 수 있다")
	void withToken() throws Exception {
		String token = tokenProvider.issue(Long.MAX_VALUE).value();
		// 카드사 목록은 사용자 정보를 쓰지 않으므로, 서명이 맞는 토큰이면 필터를 통과해 200이다.
		mockMvc.perform(get("/api/v1/card-issuers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
	}
}
