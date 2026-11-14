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
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "kattok.demo.password=" + DemoAccountLoginTest.DEMO_PASSWORD)
@AutoConfigureMockMvc
class DemoAccountLoginTest {

	/** 이 테스트에서만 쓰는 값. 실제 비밀번호는 DEMO_USER_PASSWORD 환경변수로 넣는다. */
	static final String DEMO_PASSWORD = "test-only-demo-password";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("DEMO_USER_PASSWORD를 넣으면 테스트 계정으로 로그인할 수 있다")
	void demoAccountCanLogin() throws Exception {
		String body = """
				{"email": "demo@kattok.app", "password": "%s"}
				""".formatted(DEMO_PASSWORD);
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.email").value("demo@kattok.app"));
	}
}
