package com.kattok.server.card.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class OwnedCardRegisterControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private CardPerformanceTierRepository cardPerformanceTierRepository;

	@Test
	@DisplayName("실적 조건이 없는 카드는 실적 없이 등록하면 performanceStatus는 UNKNOWN, performanceTier는 null인 채로 201을 반환한다")
	void register_withoutTier() throws Exception {
		Long productId = noPerformanceRequiredProductId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d}".formatted(productId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.performanceStatus").value("UNKNOWN"))
				.andExpect(jsonPath("$.performanceTier").doesNotExist());
	}

	@Test
	@DisplayName("실적 구간이 있는 카드를 실적 없이 등록하면 400을 반환한다")
	void register_requiresPerformance_returns400() throws Exception {
		Long productId = starPlusProductId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d}".formatted(productId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("실적 조건이 없는 카드에 실적을 보내면 400을 반환한다")
	void register_performanceNotAllowed_returns400() throws Exception {
		Long productId = noPerformanceRequiredProductId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": {\"status\": \"UNKNOWN\"}}".formatted(productId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("실적 조건이 없는 카드(NONE·NEEDS_CHECK)에 performance를 null로 보내면 400을 반환한다")
	void register_explicitNullPerformanceOnNoPerformanceCard_returns400() throws Exception {
		for (PerformanceType type : new PerformanceType[] {PerformanceType.NONE, PerformanceType.NEEDS_CHECK}) {
			Long productId = productIdOfType(type);

			mockMvc.perform(post("/api/v1/owned-cards")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"productId\": %d, \"performance\": null}".formatted(productId)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		}
	}

	@Test
	@DisplayName("실적 구간이 있는 카드에 performance를 null로 보내면 400을 반환한다")
	void register_explicitNullPerformanceOnTierCard_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": null}".formatted(starPlusProductId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("실적 구간을 같이 보내면 이번 달 실적으로 바로 반영된다")
	void register_withTier() throws Exception {
		Long productId = starPlusProductId();
		Long tierId = firstTierId(productId);

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content(("{\"productId\": %d, \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}}")
								.formatted(productId, tierId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.performanceStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.performanceTier.id").value(tierId))
				.andExpect(jsonPath("$.statusChip").value("ALL_CONFIRMED"))
				.andExpect(jsonPath("$.representativeBenefit").isNotEmpty());
	}

	@Test
	@DisplayName("실적 조건이 있는데 모른다고 등록하면 statusChip은 NEEDS_PERFORMANCE다")
	void register_unknownPerformance_needsPerformanceChip() throws Exception {
		Long productId = starPlusProductId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": {\"status\": \"UNKNOWN\"}}".formatted(productId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.statusChip").value("NEEDS_PERFORMANCE"));
	}

	@Test
	@DisplayName("최저 구간도 못 채웠다고 등록하면 BELOW로 반영된다")
	void register_withBelow() throws Exception {
		Long productId = starPlusProductId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": {\"status\": \"BELOW\"}}".formatted(productId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.performanceStatus").value("BELOW"))
				.andExpect(jsonPath("$.performanceTier").doesNotExist());
	}

	@Test
	@DisplayName("다른 상품의 실적 구간 id를 보내면 400을 반환한다")
	void register_tierFromOtherProduct() throws Exception {
		Long productId = starPlusProductId();
		Long otherProductId = cardProductRepository.findAll().stream()
				.map(CardProduct::getId)
				.filter(id -> !id.equals(productId))
				.filter(id -> !cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(id).isEmpty())
				.findFirst()
				.orElseThrow();
		Long wrongTierId = cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(otherProductId).get(0).getId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content(("{\"productId\": %d, \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}}")
								.formatted(productId, wrongTierId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("CONFIRMED인데 tierId를 안 보내면 400을 반환한다")
	void register_confirmedWithoutTierId_returns400() throws Exception {
		Long productId = starPlusProductId();

		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": {\"status\": \"CONFIRMED\"}}".formatted(productId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("존재하지 않는 상품을 등록하면 404를 반환한다")
	void register_productNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": 999999999}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CARD_PRODUCT_NOT_FOUND"));
	}

	@Test
	@DisplayName("이미 등록한 카드를 다시 등록하면 409를 반환한다")
	void register_alreadyRegistered() throws Exception {
		Long productId = noPerformanceRequiredProductId();
		String body = "{\"productId\": %d}".formatted(productId);

		mockMvc.perform(post("/api/v1/owned-cards").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/owned-cards").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CARD_ALREADY_REGISTERED"));
	}

	private Long starPlusProductId() {
		return cardProductRepository.search(null, "스타플러스", null).stream().findFirst().orElseThrow().getId();
	}

	private Long noPerformanceRequiredProductId() {
		return cardProductRepository.findAll().stream()
				.filter(product -> product.getPerformanceType() == PerformanceType.NONE
						|| product.getPerformanceType() == PerformanceType.NEEDS_CHECK)
				.findFirst()
				.orElseThrow()
				.getId();
	}

	private Long productIdOfType(PerformanceType type) {
		return cardProductRepository.findAll().stream()
				.filter(product -> product.getPerformanceType() == type)
				.findFirst()
				.orElseThrow()
				.getId();
	}

	private Long firstTierId(Long productId) {
		return cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(productId).stream()
				.map(CardPerformanceTier::getId)
				.findFirst()
				.orElseThrow();
	}
}
