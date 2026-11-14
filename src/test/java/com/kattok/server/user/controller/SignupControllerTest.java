package com.kattok.server.user.controller;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.kattok.server.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SignupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("가입하면 201과 함께 토큰과 사용자 정보를 반환한다")
	void signup() throws Exception {
		String email = uniqueEmail();

		signup(email, "password123", true)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
				.andExpect(jsonPath("$.user.id").isNumber())
				.andExpect(jsonPath("$.user.email").value(email));
	}

	@Test
	@DisplayName("이메일은 앞뒤 공백을 빼고 소문자로 저장한다")
	void normalizesEmail() throws Exception {
		String email = uniqueEmail();

		signup("  " + email.toUpperCase() + " ", "password123", true)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.email").value(email));
	}

	@Test
	@DisplayName("이미 가입한 이메일은 대소문자가 달라도 409 EMAIL_ALREADY_REGISTERED")
	void duplicateEmail() throws Exception {
		String email = uniqueEmail();
		signup(email, "password123", true).andExpect(status().isCreated());

		signup(email.toUpperCase(), "password456", true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	@DisplayName("약관에 동의하지 않으면 400 INVALID_INPUT")
	void termsNotAgreed() throws Exception {
		signup(uniqueEmail(), "password123", false)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("termsAgreed"));
	}

	@Test
	@DisplayName("비밀번호가 8자보다 짧으면 400 INVALID_INPUT")
	void shortPassword() throws Exception {
		signup(uniqueEmail(), "short", true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	@DisplayName("비밀번호가 72바이트를 넘으면 64자 이하여도 400 INVALID_INPUT")
	void passwordOverBcryptLimit() throws Exception {
		signup(uniqueEmail(), "가".repeat(25), true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	@DisplayName("한글 비밀번호도 72바이트 이하면 가입된다")
	void koreanPasswordWithinLimit() throws Exception {
		signup(uniqueEmail(), "가".repeat(24), true)
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("이메일 형식이 아니면 400 INVALID_INPUT")
	void invalidEmail() throws Exception {
		signup("not-an-email", "password123", true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
	}

	private ResultActions signup(String email, String password, boolean termsAgreed) throws Exception {
		String body = """
				{"email": "%s", "password": "%s", "termsAgreed": %s}
				""".formatted(email, password, termsAgreed);
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String uniqueEmail() {
		return "signup-" + UUID.randomUUID() + "@kattok.test";
	}
}
