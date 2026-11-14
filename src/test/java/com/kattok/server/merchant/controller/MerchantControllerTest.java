package com.kattok.server.merchant.controller;

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
class MerchantControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("키워드로 검색하면 이름·채널·업종을 담아 반환한다")
	void searchByKeyword() throws Exception {
		mockMvc.perform(get("/api/v1/merchants").param("keyword", "스타벅스"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("스타벅스"))
				.andExpect(jsonPath("$[0].channel").value("OFFLINE"))
				.andExpect(jsonPath("$[0].category.name").value("커피전문점"));
	}

	@Test
	@DisplayName("키워드 없이 호출하면 전체 목록을 반환한다")
	void searchWithoutKeyword() throws Exception {
		mockMvc.perform(get("/api/v1/merchants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)));
	}

	@Test
	@DisplayName("일치하는 결제처가 없으면 빈 배열을 반환한다")
	void searchNoMatch() throws Exception {
		mockMvc.perform(get("/api/v1/merchants").param("keyword", "존재하지않는결제처이름"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("V2에서 이미 적재된 쿠팡이 중복되지 않는다")
	void noDuplicateForExistingMerchant() throws Exception {
		mockMvc.perform(get("/api/v1/merchants").param("keyword", "쿠팡"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}
}
