package com.kattok.server.card.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;

import jakarta.persistence.EntityManager;

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

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("실적을 모르면 실적 무관 한도에 더해 최소 혜택 구간을 가정한 한도가 caps에 나오고, 선택지에는 구간별 혜택이 붙는다")
	void getState_beforeConfirmation() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceType").value("SINGLE"))
				.andExpect(jsonPath("$.performanceStatus").value("UNKNOWN"))
				.andExpect(jsonPath("$.performanceTier").doesNotExist())
				.andExpect(jsonPath("$.caps.length()").value(2))
				.andExpect(jsonPath("$.caps[?(@.capAmount == 3000)]").isNotEmpty())
				.andExpect(jsonPath("$.caps[?(@.capAmount == 2000)]").isNotEmpty())
				.andExpect(jsonPath("$.caps[0].remainingLimitAmount").doesNotExist())
				.andExpect(jsonPath("$.caps[0].capCount").hasJsonPath())
				.andExpect(jsonPath("$.performanceTierOptions[0].label").value("20만원 이상"))
				.andExpect(jsonPath("$.performanceTierOptions[0].benefits[0].displayText").value("GS25/CU 5% 할인"))
				.andExpect(jsonPath("$.performanceTierOptions[0].benefits[0].capAmount").value(2000));
	}

	@Test
	@DisplayName("구간 확인이 필요한(NEEDS_CHECK) 카드는 실적 선택지가 비어 있고 구간 한도를 가정하지 않는다")
	void getState_needsCheckCard_noOptions() throws Exception {
		Long userCardId = registerProductOfType(PerformanceType.NEEDS_CHECK);

		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceType").value("NEEDS_CHECK"))
				.andExpect(jsonPath("$.performanceTierOptions.length()").value(0));
	}

	@Test
	@DisplayName("performance에 명시적으로 null을 보내면 400을 반환한다")
	void putState_explicitNullPerformance_returns400() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(putStateRequest(userCardId, "{\"month\": \"%s\", \"performance\": null}".formatted(MONTH)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("caps에 명시적으로 null을 보내면 400을 반환한다")
	void putState_explicitNullCaps_returns400() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(putStateRequest(userCardId, "{\"month\": \"%s\", \"caps\": null}".formatted(MONTH)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("caps에 같은 capGroupId를 중복해서 보내면 400을 반환한다")
	void putState_duplicateCapGroupId_returns400() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();
		setTier(userCardId, tierId);
		Long capGroupId = capGroupIdFor(userCardId);

		mockMvc.perform(putStateRequest(userCardId,
						("{\"month\": \"%s\", \"caps\": ["
								+ "{\"capGroupId\": %d, \"remainingLimitAmount\": 1000}, "
								+ "{\"capGroupId\": %d, \"remainingLimitAmount\": 2000}]}")
								.formatted(MONTH, capGroupId, capGroupId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("실적 구간을 확정하면 그 구간에 해당하는 한도도 실적 무관 한도와 함께 caps에 나온다")
	void putState_setsTier() throws Exception {
		Long userCardId = registerStarPlus();
		Long tierId = convenienceTierId();

		mockMvc.perform(putStateRequest(userCardId, confirmedBody(tierId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.performanceTier.id").value(tierId))
				.andExpect(jsonPath("$.caps.length()").value(2))
				.andExpect(jsonPath("$.caps[?(@.capAmount == 2000)][0].remainingLimitAmount").doesNotExist());
	}

	@Test
	@DisplayName("실적을 확인했지만 최저 구간도 못 채웠어도 실적 조건 없는 혜택의 한도는 caps에 나온다")
	void putState_setsBelow() throws Exception {
		Long userCardId = registerStarPlus();

		mockMvc.perform(putStateRequest(userCardId,
						"{\"month\": \"%s\", \"performance\": {\"status\": \"BELOW\"}}".formatted(MONTH)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("BELOW"))
				.andExpect(jsonPath("$.performanceTier").doesNotExist())
				.andExpect(jsonPath("$.caps.length()").value(1))
				.andExpect(jsonPath("$.caps[0].capAmount").value(3000));
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

	@Test
	@DisplayName("실적 조건이 없는 카드(NONE·NEEDS_CHECK)에 실적을 보내면 400이고, 실적 없이 보내면 200이다")
	void putState_performanceOnNoPerformanceCard_returns400() throws Exception {
		for (PerformanceType type : new PerformanceType[] {PerformanceType.NONE, PerformanceType.NEEDS_CHECK}) {
			Long userCardId = registerProductOfType(type);

			mockMvc.perform(putStateRequest(userCardId,
							"{\"month\": \"%s\", \"performance\": {\"status\": \"UNKNOWN\"}}".formatted(MONTH)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
			mockMvc.perform(putStateRequest(userCardId, "{\"month\": \"%s\"}".formatted(MONTH)))
					.andExpect(status().isOk());
		}
	}

	private Long registerProductOfType(PerformanceType type) throws Exception {
		Long productId = cardProductRepository.findAll().stream()
				.filter(product -> product.getPerformanceType() == type)
				.findFirst()
				.orElseThrow()
				.getId();
		String response = mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d}".formatted(productId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private MockHttpServletRequestBuilder putStateRequest(Long userCardId, String body) {
		return put("/api/v1/owned-cards/{id}/state", userCardId).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String confirmedBody(Long tierId) {
		return "{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}}".formatted(MONTH, tierId);
	}

	@Test
	@DisplayName("실적 선택지의 혜택마다 ruleId·capGroupId가 있고, 구간마다 상위 한도를 포함한 한도 목록이 붙는다")
	void getState_tierOptionsCarryCapsPerTier() throws Exception {
		Long userCardId = registerEverydayCheck();
		List<Long> tierIds = everydayTierIds();

		mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceStatus").value("UNKNOWN"))
				.andExpect(jsonPath("$.performanceTierOptions[0].benefits[0].ruleId").isNumber())
				.andExpect(jsonPath("$.performanceTierOptions[0].benefits[0].capGroupId").isNumber())
				// 구간 2: 커피·쇼핑·택시 한도 + 특별적립 통합 월한도(상위 한도)
				.andExpect(jsonPath("$.performanceTierOptions[1].caps.length()").value(4))
				.andExpect(jsonPath("$.performanceTierOptions[1].caps[?(@.tierId == %d)]".formatted(tierIds.get(1)))
						.value(org.hamcrest.Matchers.hasSize(4)))
				.andExpect(jsonPath("$.performanceTierOptions[1].caps[?(@.capAmount == 20000)].description")
						.value("특별적립 통합 월한도(60만원 이상)"))
				// 현재 적용 caps: 실적 무관 기본 적립(tierId null) + 최소 구간 가정(구간 1) 한도 4개
				.andExpect(jsonPath("$.caps.length()").value(5))
				.andExpect(jsonPath("$.caps[?(@.tierId == null)]").value(org.hamcrest.Matchers.hasSize(1)))
				.andExpect(jsonPath("$.caps[?(@.tierId == %d)]".formatted(tierIds.get(0)))
						.value(org.hamcrest.Matchers.hasSize(4)));
	}

	@Test
	@DisplayName("구간 변경과 새 구간 한도를 한 요청으로 저장할 수 있고, 이전 구간 한도는 지우지 않고 선택지에 남는다")
	void putState_changeTierAndCapsTogether() throws Exception {
		Long userCardId = registerEverydayCheck();
		List<Long> tierIds = everydayTierIds();
		String state = mockMvc.perform(get("/api/v1/owned-cards/{id}/state", userCardId).param("month", MONTH))
				.andReturn().getResponse().getContentAsString();
		List<Number> parentIds = JsonPath.read(state, "$.performanceTierOptions[1].caps[?(@.capAmount == 20000)].capGroupId");
		Number parentT2 = parentIds.getFirst();

		mockMvc.perform(putStateRequest(userCardId,
						("{\"month\": \"%s\", \"performance\": {\"status\": \"CONFIRMED\", \"tierId\": %d}, "
								+ "\"caps\": [{\"capGroupId\": %d, \"remainingLimitAmount\": 15000}]}")
								.formatted(MONTH, tierIds.get(1), parentT2.longValue())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.performanceTier.id").value(tierIds.get(1)))
				.andExpect(jsonPath("$.caps[?(@.tierId == %d)]".formatted(tierIds.get(0))).isEmpty())
				.andExpect(jsonPath("$.caps[?(@.capGroupId == %d)].remainingLimitAmount".formatted(parentT2.longValue()))
						.value(org.hamcrest.Matchers.contains(15000)));

		// 테스트 전체가 한 트랜잭션이라, 실제 서버처럼 요청마다 새 영속성 컨텍스트에서 상태를 읽도록 비운다.
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(putStateRequest(userCardId, confirmedBody(tierIds.get(0))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caps[?(@.capGroupId == %d)]".formatted(parentT2.longValue())).isEmpty())
				.andExpect(jsonPath("$.performanceTierOptions[1].caps[?(@.capGroupId == %d)].remainingLimitAmount"
						.formatted(parentT2.longValue())).value(org.hamcrest.Matchers.contains(15000)));
	}

	private Long registerEverydayCheck() throws Exception {
		String response = mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": {\"status\": \"UNKNOWN\"}}".formatted(everydayProductId())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private Long everydayProductId() {
		return cardProductRepository.findAll().stream()
				.filter(product -> "CK058".equals(product.getProductKey()))
				.findFirst().orElseThrow().getId();
	}

	private List<Long> everydayTierIds() {
		return cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(everydayProductId()).stream()
				.map(CardPerformanceTier::getId)
				.toList();
	}

	private Long registerStarPlus() throws Exception {
		Long productId = cardProductRepository.search(null, "스타플러스", null).stream()
				.findFirst().orElseThrow().getId();
		String response = mockMvc.perform(post("/api/v1/owned-cards")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"productId\": %d, \"performance\": {\"status\": \"UNKNOWN\"}}".formatted(productId)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private void registerAnotherProduct() throws Exception {
		Long productId = cardProductRepository.findAll().stream()
				.filter(product -> product.getPerformanceType() == com.kattok.server.card.entity.PerformanceType.NONE
						|| product.getPerformanceType() == com.kattok.server.card.entity.PerformanceType.NEEDS_CHECK)
				.findFirst()
				.orElseThrow()
				.getId();
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
