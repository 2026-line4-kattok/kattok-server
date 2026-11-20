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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardProductControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("상품명 일부로 검색하면 일치하는 카드가 나온다")
	void searchByKeyword() throws Exception {
		mockMvc.perform(get("/api/v1/card-products").param("keyword", "알파벳"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == '알파벳카드S')]").exists())
				.andExpect(jsonPath("$[0].issuer.name").isNotEmpty())
				.andExpect(jsonPath("$[0].owned").value(false));
	}

	@Test
	@DisplayName("일치하는 상품이 없으면 빈 배열을 반환한다")
	void searchNoMatch() throws Exception {
		mockMvc.perform(get("/api/v1/card-products").param("keyword", "존재하지않는카드이름이다"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("cardType에 잘못된 값을 주면 400과 INVALID_INPUT을 반환한다")
	void invalidCardType() throws Exception {
		mockMvc.perform(get("/api/v1/card-products").param("cardType", "BOGUS"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("혜택을 계산할 수 없는 카드(UNVERIFIED)는 검색 목록에 나오지 않는다")
	void searchHidesUnverifiedCards() throws Exception {
		mockMvc.perform(get("/api/v1/card-products").param("keyword", "LOCA 365"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/card-products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.dataStatus == 'UNVERIFIED')]").isEmpty())
				.andExpect(jsonPath("$.length()").value(jdbc.queryForObject(
						"select count(*) from card_products where data_status <> 'UNVERIFIED'", Integer.class)));
	}

	@Test
	@DisplayName("cardType으로 필터링하면 해당 타입만 반환한다")
	void filterByCardType() throws Exception {
		mockMvc.perform(get("/api/v1/card-products").param("cardType", "CREDIT").param("keyword", "알파벳"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].cardType").value("CREDIT"));
	}
}
