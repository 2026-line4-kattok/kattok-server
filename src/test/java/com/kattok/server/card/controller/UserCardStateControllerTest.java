package com.kattok.server.card.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
	@DisplayName("실적 구간을 확인한 적 없으면 상태는 UNKNOWN, performanceTier는 null, caps는 빈 배열이다")
	void getState_beforeConfirmation() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("UNKNOWN"))
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

		mockMvc.perform(putStateRequest(userCardId, confirmedBody(tierId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.performanceTier.id").value(tierId))
				.andExpect(jsonPath("$.caps[0].capAmount").value(2000))
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").doesNotExist());
	}

	@Test
	@DisplayName("실적을 확인했지만 최저 구간도 못 채웠으면 BELOW로 저장되고 caps는 빈 배열이다")
	void putState_setsBelow() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(putStateRequest(userCardId,
						"{\"month\": \"%s\", \"performance\": {\"status\": \"BELOW\"}}".formatted(MONTH)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("BELOW"))
				.andExpect(jsonPath("$.performanceTier").doesNotExist())
				.andExpect(jsonPath("$.caps.length()").value(0));
	}

	@Test
	@DisplayName("performance를 보내지 않고 caps만 수정해도 실적 구간은 그대로 유지된다(부분 수정)")
	void putState_capsOnlyKeepsConfirmedTier() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();
		setTier(userCardId, tierId);
		Long capGroupId = capGroupIdFor(userCardId);

		mockMvc.perform(putStateRequest(userCardId,
						("{\"month\": \"%s\", \"caps\": [{\"capGroupId\": %d, \"remainingLimitAmount\": 1000}]}")
								.formatted(MONTH, capGroupId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.performanceTier.id").value(tierId))
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").value(1000));
	}

	@Test
	@DisplayName("한도를 수정해도 다음 PUT에서 caps를 안 보내면 이전 값이 유지된다(부분 수정)")
	void putState_partialUpdateKeepsUnmentionedCaps() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();
		setTier(userCardId, tierId);

		Long capGroupId = capGroupIdFor(userCardId);

		mockMvc.perform(putStateRequest(userCardId,
						("{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}, "
								+ "\"caps\": [{\"capGroupId\": %d, \"remainingLimitAmount\": 1000}]}")
								.formatted(MONTH, tierId, capGroupId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").value(1000));

		// performance를 아예 안 보냄 -> remainingLimitAmount가 그대로 1000이어야 함
		mockMvc.perform(putStateRequest(userCardId, "{\"month\": \"%s\"}".formatted(MONTH)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").value(1000));
	}

	@Test
	@DisplayName("전체 한도를 넘는 값을 보내면 400을 반환한다")
	void putState_exceedingCapAmount_returns400() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();
		setTier(userCardId, tierId);
		Long capGroupId = capGroupIdFor(userCardId);

		mockMvc.perform(putStateRequest(userCardId,
						("{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}, "
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

		mockMvc.perform(putStateRequest(userCardId,
						("{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}, "
								+ "\"caps\": [{\"capGroupId\": 999999999, \"remainingLimitAmount\": 100}]}")
								.formatted(MONTH, tierId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("CONFIRMED인데 tierId를 안 보내면 400을 반환한다")
	void putState_confirmedWithoutTierId_returns400() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(putStateRequest(userCardId,
						"{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\"}}".formatted(MONTH)))
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

	@Test
	@DisplayName("삭제된 카드의 상태를 조회·수정하면 404를 반환한다")
	void deletedCard_stateAccess_returns404() throws Exception {
		registerAnotherProduct(); // 마지막 카드는 삭제할 수 없으므로(F-04-04) 다른 카드를 하나 더 등록해 둔다
		Long userCardId = registerStarPlus();
		mockMvc.perform(delete("/api/v1/owned-cards/{id}", userCardId)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
		mockMvc.perform(putStateRequest(userCardId, "{\"month\": \"%s\"}".formatted(MONTH)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	private MockHttpServletRequestBuilder putStateRequest(Long userCardId, String body) {
		return put("/api/v1/owned-cards/{id}/state", userCardId).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String confirmedBody(Long tierId) {
		return "{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}}".formatted(MONTH, tierId);
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

	private void registerAnotherProduct() throws Exception {
		Long starPlusId = cardProductRepository.search(null, "스타플러스", null).stream()
				.findFirst().orElseThrow().getId();
		Long productId = cardProductRepository.findAll().stream()
				.map(com.kattok.server.card.entity.CardProduct::getId)
				.filter(id -> !id.equals(starPlusId))
				.findFirst()
				.orElseThrow();
		mockMvc.perform(post("/api/v1/owned-cards")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\": %d}".formatted(productId)));
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
		mockMvc.perform(putStateRequest(userCardId, confirmedBody(tierId)))
				.andExpect(status().isOk());
	}

	private Long capGroupIdFor(Long userCardId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.caps[0].capGroupId")).longValue();
	}
}
