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
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.entity.CardIssuer;
import com.kattok.server.card.repository.CardIssuerRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class CardIssuerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CardIssuerRepository cardIssuerRepository;

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

	@Test
	@DisplayName("등록된 상품이 없는 카드사는 목록에서 빠진다")
	void excludesIssuerWithoutProducts() throws Exception {
		cardIssuerRepository.save(new CardIssuer("상품 없는 카드사", "NO_PRODUCT_ISSUER"));

		mockMvc.perform(get("/api/v1/card-issuers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == 'NO_PRODUCT_ISSUER')]").doesNotExist());
	}
}
