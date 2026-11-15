package com.kattok.server.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.merchant.entity.MerchantCategory;
import com.kattok.server.merchant.repository.MerchantCategoryRepository;
import com.kattok.server.user.repository.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
@Transactional
class MerchantCategoryControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private MerchantCategoryRepository categoryRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private TokenProvider tokenProvider;
	@Autowired private JsonMapper jsonMapper;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("지원 업종을 명세 순서와 표시 이름으로 반환하고 실제 추천용 ID를 유지한다")
	void listsSupportedCategoriesInDisplayOrder() throws Exception {
		categoryRepository.saveAndFlush(new MerchantCategory("미지원 업종", "UNSUPPORTED_TEST"));
		String body = mockMvc.perform(get("/api/v1/merchant-categories")
				.header(HttpHeaders.AUTHORIZATION, authorization()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode result = jsonMapper.readTree(body);
		assertThat(result.size()).isEqualTo(8);
		List<String> codes = List.of("CONVENIENCE_STORE", "CAFE", "RESTAURANT", "MART",
				"ONLINE_SHOPPING", "PHARMACY", "TAXI", "DEPARTMENT_STORE");
		List<String> names = List.of("편의점", "카페", "음식점", "마트", "온라인쇼핑", "약국", "택시", "백화점");
		for (int i = 0; i < codes.size(); i++) {
			String code = codes.get(i);
			MerchantCategory stored = categoryRepository.findAll().stream()
					.filter(category -> category.getCode().equals(code)).findFirst().orElseThrow();
			assertThat(result.get(i).get("id").asLong()).isEqualTo(stored.getId());
			assertThat(result.get(i).get("name").asString()).isEqualTo(names.get(i));
			assertThat(result.get(i).size()).isEqualTo(2);
		}
	}

	@Test
	@DisplayName("지원 업종 데이터가 없으면 빈 배열을 반환한다")
	void returnsEmptyWhenNoSupportedCategoryExists() throws Exception {
		jdbcTemplate.update("update merchant_categories set code = 'TEST_' || code");
		mockMvc.perform(get("/api/v1/merchant-categories")
				.header(HttpHeaders.AUTHORIZATION, authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("업종 목록은 로그인하지 않은 요청을 거절한다")
	void rejectsAnonymousRequest() throws Exception {
		mockMvc.perform(get("/api/v1/merchant-categories"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private String authorization() {
		Long userId = userRepository.findByEmail("demo@kattok.app").orElseThrow().getId();
		return "Bearer " + tokenProvider.issue(userId).value();
	}
}
