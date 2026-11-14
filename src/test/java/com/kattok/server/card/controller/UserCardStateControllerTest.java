package com.kattok.server.card.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class UserCardStateControllerTest {

	private static final String MONTH = "2026-11";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private CardPerformanceTierRepository cardPerformanceTierRepository;

	@Test
	@DisplayName("실적 구간을 확인한 적 없으면 performanceTier는 null, caps는 빈 배열이다")
	void getState_beforeConfirmation() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceTier").doesNotExist())
				.andExpect(jsonPath("$.caps").isArray())
				.andExpect(jsonPath("$.caps.length()").value(0))
				.andExpect(jsonPath("$.performanceTierOptions[0].label").value("20만원 이상"));
	}

	@Test
	@DisplayName("실적 구간을 확정하면 그 구간에 해당하는 한도가 caps에 나온다")
	void putState_setsTier() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();

		mockMvc.perform(put("/api/v1/owned-cards/{id}/state", userCardId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"month\": \"%s\", \"performanceTierId\": %d}".formatted(MONTH, tierId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceTier.id").value(tierId))
				.andExpect(jsonPath("$.caps[0].capAmount").value(2000))
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").doesNotExist());
	}

	@Test
	@DisplayName("한도를 수정해도 다음 PUT에서 caps를 안 보내면 이전 값이 유지된다(부분 수정)")
	void putState_partialUpdateKeepsUnmentionedCaps() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();
		setTier(userCardId, tierId);

		Long capGroupId = capGroupIdFor(userCardId);

		mockMvc.perform(put("/api/v1/owned-cards/{id}/state", userCardId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(("{\"month\": \"%s\", \"performanceTierId\": %d, "
								+ "\"caps\": [{\"capGroupId\": %d, \"remainingLimitAmount\": 1000}]}")
								.formatted(MONTH, tierId, capGroupId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").value(1000));

		// caps를 아예 안 보내고 performanceTierId만 다시 보냄 -> remainingLimitAmount가 그대로 1000이어야 함
		mockMvc.perform(put("/api/v1/owned-cards/{id}/state", userCardId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"month\": \"%s\", \"performanceTierId\": %d}".formatted(MONTH, tierId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").value(1000));
	}

	@Test
	@DisplayName("전체 한도를 넘는 값을 보내면 400을 반환한다")
	void putState_exceedingCapAmount_returns400() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();
		setTier(userCardId, tierId);
		Long capGroupId = capGroupIdFor(userCardId);

		mockMvc.perform(put("/api/v1/owned-cards/{id}/state", userCardId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(("{\"month\": \"%s\", \"performanceTierId\": %d, "
								+ "\"caps\": [{\"capGroupId\": %d, \"remainingLimitAmount\": 999999}]}")
								.formatted(MONTH, tierId, capGroupId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("이 카드의 한도가 아닌 capGroupId를 보내면 400을 반환한다")
	void putState_capNotBelongingToCard_returns400() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();

		mockMvc.perform(put("/api/v1/owned-cards/{id}/state", userCardId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(("{\"month\": \"%s\", \"performanceTierId\": %d, "
								+ "\"caps\": [{\"capGroupId\": 999999999, \"remainingLimitAmount\": 100}]}")
								.formatted(MONTH, tierId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("존재하지 않는 보유 카드를 조회하면 404를 반환한다")
	void getState_notFound() throws Exception {
		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", 999_999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	private Long registerStarPlus() throws Exception {
		Long productId = cardProductRepository.search(null, "스타플러스", null).stream()
				.findFirst().orElseThrow().getId();
		String response = mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d}".formatted(productId)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private Long convenienceTierId() {
		Long productId = cardProductRepository.search(null, "스타플러스", null).stream()
				.findFirst().orElseThrow().getId();
		return cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(productId).stream()
				.map(CardPerformanceTier::getId)
				.findFirst()
				.orElseThrow();
	}

	private void setTier(Long userCardId, Long tierId) throws Exception {
		mockMvc.perform(put("/api/v1/owned-cards/{id}/state", userCardId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"month\": \"%s\", \"performanceTierId\": %d}".formatted(MONTH, tierId)))
				.andExpect(status().isOk());
	}

	private Long capGroupIdFor(Long userCardId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.caps[0].capGroupId")).longValue();
	}
}
