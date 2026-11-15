package com.kattok.server.merchant.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;
import com.kattok.server.merchant.repository.MerchantRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class MerchantSearchTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MerchantRepository merchantRepository;

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\r\n"})
	@DisplayName("검색어를 입력하지 않으면 전체 결제처를 노출하지 않는다")
	void blankKeywordReturnsEmpty(String keyword) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/merchants");
		if (keyword != null) {
			request.param("keyword", keyword);
		}
		mockMvc.perform(request).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"  스타벅스  ", " sTaRbUcKs ", "rBuC", "스벅"})
	@DisplayName("이름과 별칭은 공백 제거·영문 대소문자 무시 후 대표 결제처로 반환한다")
	void searchesNameAndAliases(String keyword) throws Exception {
		Merchant stored = merchantRepository.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		mockMvc.perform(get("/api/v1/merchants").param("keyword", keyword))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(stored.getId()))
				.andExpect(jsonPath("$[0].name").value("스타벅스"))
				.andExpect(jsonPath("$[0].channel").value("OFFLINE"))
				.andExpect(jsonPath("$[0].category.id").value(stored.getCategory().getId()));
	}

	@Test
	@DisplayName("이름과 여러 별칭이 함께 일치해도 같은 결제처는 한 번만 반환한다")
	void nameAndMultipleAliasMatchesDoNotDuplicateMerchant() throws Exception {
		mockMvc.perform(get("/api/v1/merchants").param("keyword", "스"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == '스타벅스')]").value(hasSize(1)));
	}

	@Test
	@DisplayName("검색 결과는 이름순이며 같은 이름의 다른 채널은 ID순으로 유지한다")
	void ordersByNameThenId() throws Exception {
		Merchant existing = merchantRepository.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		merchantRepository.saveAndFlush(new Merchant("검색정렬B", MerchantChannel.OFFLINE, existing.getCategory()));
		Merchant first = merchantRepository.saveAndFlush(
				new Merchant("검색정렬A", MerchantChannel.ONLINE, existing.getCategory()));
		Merchant second = merchantRepository.saveAndFlush(
				new Merchant("검색정렬A", MerchantChannel.OFFLINE, existing.getCategory()));
		mockMvc.perform(get("/api/v1/merchants").param("keyword", "검색정렬"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(first.getId()))
				.andExpect(jsonPath("$[1].id").value(second.getId()))
				.andExpect(jsonPath("$[2].name").value("검색정렬B"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"%", "_", "!", "\\"})
	@DisplayName("검색 특수문자는 전체 일치 패턴이 아니라 입력한 문자로 검색한다")
	void searchesPatternCharactersLiterally(String keyword) throws Exception {
		Merchant existing = merchantRepository.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		Merchant literal = merchantRepository.saveAndFlush(
				new Merchant("리터럴" + keyword + "검색", MerchantChannel.OFFLINE, existing.getCategory()));
		mockMvc.perform(get("/api/v1/merchants").param("keyword", keyword))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(literal.getId()));
	}
}
