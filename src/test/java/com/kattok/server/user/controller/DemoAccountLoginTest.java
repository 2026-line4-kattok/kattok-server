package com.kattok.server.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DemoAccountLoginTest {

	/** V23 마이그레이션 주석에 적힌 테스트 계정 비밀번호. */
	private static final String DEMO_PASSWORD = "kattok-demo-1b9e2b";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("시연용 테스트 계정으로 로그인할 수 있다")
	void demoAccountCanLogin() throws Exception {
		String body = """
				{"email": "demo@kattok.app", "password": "%s"}
				""".formatted(DEMO_PASSWORD);
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.email").value("demo@kattok.app"));
	}
}
