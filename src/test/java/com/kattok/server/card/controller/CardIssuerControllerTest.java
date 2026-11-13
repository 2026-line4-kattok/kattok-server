package com.kattok.server.card.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardIssuerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("등록된 카드사 목록을 id, name, code 필드와 함께 반환한다")
	void findAll() throws Exception {
		mockMvc.perform(get("/api/v1/card-issuers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").exists())
				.andExpect(jsonPath("$[0].name").isNotEmpty())
				.andExpect(jsonPath("$[0].code").isNotEmpty());
	}

	@Test
	@DisplayName("V5 시드에 있는 발급사(현대카드)가 목록에 포함된다")
	void containsSeededIssuer() throws Exception {
		mockMvc.perform(get("/api/v1/card-issuers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == 'HYUNDAI')]").exists());
	}
}
