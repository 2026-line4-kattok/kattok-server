package com.kattok.server.card.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.repository.CardProductRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardProductDetailControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Test
	@DisplayName("실적 조건 없는 혜택은 alwaysOnBenefits에, 구간별 혜택은 그 구간 밑에 들어간다")
	void detail() throws Exception {
		Long starPlusId = findIdByKeyword("스타플러스");

		mockMvc.perform(get("/api/v1/card-products/{id}", starPlusId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(starPlusId))
				.andExpect(jsonPath("$.performanceType").value("SINGLE"))
				.andExpect(jsonPath("$.alwaysOnBenefits[0].displayText").value(org.hamcrest.Matchers.containsString("커피")))
				.andExpect(jsonPath("$.performanceTiers[0].label").value("20만원 이상"))
				.andExpect(jsonPath("$.performanceTiers[0].benefits[0].displayText")
						.value(org.hamcrest.Matchers.containsString("GS25")));
	}

	@Test
	@DisplayName("혜택 데이터를 검증하지 못한 상품은 performanceType이 NEEDS_CHECK이고 실적 구간 선택지는 빈 배열이다")
	void detail_needsCheck() throws Exception {
		Long productId = cardProductRepository.findAll().stream()
				.filter(product -> product.getDataStatus() != com.kattok.server.card.entity.DataStatus.CALCULABLE)
				.findFirst()
				.orElseThrow()
				.getId();

		mockMvc.perform(get("/api/v1/card-products/{id}", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceType").value("NEEDS_CHECK"))
				.andExpect(jsonPath("$.performanceTiers.length()").value(0));
	}

	@Test
	@DisplayName("존재하지 않는 상품 id는 404와 CARD_PRODUCT_NOT_FOUND를 반환한다")
	void notFound() throws Exception {
		mockMvc.perform(get("/api/v1/card-products/{id}", 999_999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CARD_PRODUCT_NOT_FOUND"));
	}

	private Long findIdByKeyword(String keyword) {
		var result = cardProductRepository.search(null, keyword, null);
		assertThat(result).isNotEmpty();
		return result.stream().findFirst().map(CardProduct::getId).orElseThrow();
	}
}
