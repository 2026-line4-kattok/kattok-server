package com.kattok.server.card.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.time.ZoneId;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.repository.CardProductRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class OwnedCardDetailControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	@Test
	@DisplayName("실적을 모르면 status는 UNKNOWN, tier는 null, caps는 실적 무관 한도 + 최소 혜택 구간 가정 한도다")
	void detail_beforeConfirmation() throws Exception {
		Long userCardId = registerStarPlusWithUnknownPerformance();
		String expectedBaseMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1).toString();

		mockMvc.perform(get("/api/v1/owned-cards/{id}", userCardId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userCardId))
				.andExpect(jsonPath("$.product.name").value("스타플러스 체크카드"))
				.andExpect(jsonPath("$.product.performanceType").value("SINGLE"))
				.andExpect(jsonPath("$.performance.status").value("UNKNOWN"))
				.andExpect(jsonPath("$.performance.tier").doesNotExist())
				.andExpect(jsonPath("$.performance.options[0].label").value("20만원 이상"))
				.andExpect(jsonPath("$.performance.options[0].benefits[0].displayText").value("GS25/CU 5% 할인"))
				.andExpect(jsonPath("$.performance.baseMonth").value(expectedBaseMonth))
				.andExpect(jsonPath("$.caps.length()").value(2))
				.andExpect(jsonPath("$.caps[?(@.capAmount == 3000)]").isNotEmpty())
				.andExpect(jsonPath("$.caps[?(@.capAmount == 2000)]").isNotEmpty())
				.andExpect(jsonPath("$.benefits[0].usedInRecommendation").value(true))
				.andExpect(jsonPath("$.savedBenefit.amount").value(0))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(0));
	}

	@Test
	@DisplayName("미검증 혜택 규칙도 benefits에 나오지만 usedInRecommendation은 false다")
	void detail_unverifiedRule_notUsedInRecommendation() throws Exception {
		Long productId = starPlusProductId();
		Long capGroupId = jdbc.queryForObject(
				"select min(id) from benefit_cap_groups where product_id = ?", Long.class, productId);
		Long categoryId = jdbc.queryForObject("select id from merchant_categories where code = 'CAFE'", Long.class);
		Long unverifiedRuleId = jdbc.queryForObject("""
				insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text,
					source_ref, verification_status, rule_version, valid_from)
				values (?, ?, ?, 'DISCOUNT_RATE', 0.05, '검증 전 혜택', '테스트', 'UNVERIFIED', 1, current_date)
				returning id
				""", Long.class, productId, categoryId, capGroupId);
		Long userCardId = registerStarPlusWithUnknownPerformance();

		mockMvc.perform(get("/api/v1/owned-cards/{id}", userCardId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.benefits[?(@.ruleId == %d)].usedInRecommendation".formatted(unverifiedRuleId))
						.value(false))
				.andExpect(jsonPath("$.benefits[?(@.ruleId != %d)].usedInRecommendation".formatted(unverifiedRuleId))
						.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
	}

	@Test
	@DisplayName("실적 구간을 확정하면 그 구간에 해당하는 한도가 caps에 나온다")
	void detail_afterConfirmation() throws Exception {
		Long tierId = firstTierId(starPlusProductId());
		Long userCardId = registerStarPlusConfirmed(tierId);

		mockMvc.perform(get("/api/v1/owned-cards/{id}", userCardId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performance.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.performance.tier.id").value(tierId))
				.andExpect(jsonPath("$.caps[0].capAmount").exists());
	}

	@Test
	@DisplayName("존재하지 않는 보유 카드는 404를 반환한다")
	void detail_notFound() throws Exception {
		mockMvc.perform(get("/api/v1/owned-cards/{id}", 999_999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	@Test
	@DisplayName("삭제한 보유 카드를 조회하면 404를 반환한다")
	void detail_deletedCard_returns404() throws Exception {
		Long otherProductId = anotherProductId();
		registerProduct(otherProductId, null); // 마지막 카드 삭제 방지용 두 번째 카드
		Long userCardId = registerStarPlusWithUnknownPerformance();

		mockMvc.perform(delete("/api/v1/owned-cards/{id}", userCardId)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/owned-cards/{id}", userCardId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	private Long starPlusProductId() {
		return cardProductRepository.search(null, "스타플러스", null).stream().findFirst().orElseThrow().getId();
	}

	private Long anotherProductId() {
		return cardProductRepository.findAll().stream()
				.filter(product -> product.getPerformanceType() == com.kattok.server.card.entity.PerformanceType.NONE
						|| product.getPerformanceType() == com.kattok.server.card.entity.PerformanceType.NEEDS_CHECK)
				.findFirst()
				.orElseThrow()
				.getId();
	}

	private Long firstTierId(Long productId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/card-products/{id}", productId))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.performanceTiers[0].id")).longValue();
	}

	private Long registerStarPlusWithUnknownPerformance() throws Exception {
		return registerProduct(starPlusProductId(), "{\"status\": \"UNKNOWN\"}");
	}

	private Long registerStarPlusConfirmed(Long tierId) throws Exception {
		return registerProduct(starPlusProductId(), "{\"status\": \"CONFIRMED\", \"tierId\": %d}".formatted(tierId));
	}

	private Long registerProduct(Long productId, String performanceJson) throws Exception {
		String body = performanceJson == null
				? "{\"productId\": %d}".formatted(productId)
				: "{\"productId\": %d, \"performance\": %s}".formatted(productId, performanceJson);
		String response = mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}
}
