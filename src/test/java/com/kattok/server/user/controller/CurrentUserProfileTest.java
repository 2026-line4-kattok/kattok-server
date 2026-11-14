package com.kattok.server.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class CurrentUserProfileTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private TokenProvider tokenProvider;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("내 정보는 저장된 가입 시각을 KST로 반환하며 반복 조회해도 바뀌지 않는다")
	void returnsStoredCreationTimeInKst() throws Exception {
		String email = "profile-" + UUID.randomUUID() + "@kattok.test";
		User user = userRepository.saveAndFlush(new User(email, "unused-hash", Instant.now()));
		Long userId = user.getId();
		// UTC의 전날이 KST에서는 다음 날이 되는 경계. 실제 DB에 저장한 값으로 검증한다.
		Instant createdAt = Instant.parse("2026-11-14T15:30:00Z");
		jdbcTemplate.update("update users set created_at = ? where id = ?", Timestamp.from(createdAt), userId);
		entityManager.clear();
		String token = tokenProvider.issue(userId).value();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(userId))
					.andExpect(jsonPath("$.email").value(email))
					.andExpect(jsonPath("$.createdAt").value("2026-11-15T00:30:00+09:00"));
		}
	}

	@Test
	@DisplayName("내 정보 확장 후에도 회원가입·로그인 사용자 응답은 id와 email만 유지한다")
	void authUserShapeIsUnchanged() throws Exception {
		String email = "profile-auth-" + UUID.randomUUID() + "@kattok.test";
		String password = UUID.randomUUID().toString();
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\",\"termsAgreed\":true}".formatted(email, password)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.id").isNumber())
				.andExpect(jsonPath("$.user.email").value(email))
				.andExpect(jsonPath("$.user.size()").value(2))
				.andExpect(jsonPath("$.user.createdAt").doesNotExist());
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.id").isNumber())
				.andExpect(jsonPath("$.user.email").value(email))
				.andExpect(jsonPath("$.user.size()").value(2))
				.andExpect(jsonPath("$.user.createdAt").doesNotExist());
	}
}
