package com.kattok.server.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.jayway.jsonpath.JsonPath;
import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.payment.entity.PaymentRecord;
import com.kattok.server.payment.entity.PaymentRecordStatus;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import com.kattok.server.recommendation.entity.OverallStatus;
import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.recommendation.entity.RecommendationItem;
import com.kattok.server.recommendation.repository.RecommendationRepository;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class RecommendationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private UserCardRepository userCardRepository;

	@Autowired
	private MerchantRepository merchantRepository;

	@Autowired
	private RecommendationRepository recommendationRepository;

	@Autowired
	private PaymentRecordRepository paymentRecordRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("실적을 모르는 두 카드를 비교하면 가장 낮은 구간으로 가정한 조건부 결과를 순위대로 반환한다")
	void create_conditional() throws Exception {
		UserCard goodDay = register("굿데이");
		UserCard alphabet = register("알파벳카드S");

		create("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.basis").value("MERCHANT"))
				.andExpect(jsonPath("$.merchant.name").value("스타벅스"))
				.andExpect(jsonPath("$.category.name").value("커피전문점"))
				.andExpect(jsonPath("$.amount").value(5000))
				.andExpect(jsonPath("$.overallStatus").value("CONDITIONAL"))
				.andExpect(jsonPath("$.recommendedUserCardId").value(goodDay.getId()))
				.andExpect(jsonPath("$.tiedUserCardIds", hasSize(0)))
				.andExpect(jsonPath("$.differenceFromSecond").value(250))
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].userCardId").value(goodDay.getId()))
				.andExpect(jsonPath("$.items[0].rank").value(1))
				.andExpect(jsonPath("$.items[0].status").value("CONDITIONAL"))
				.andExpect(jsonPath("$.items[0].expectedBenefit").value(500))
				.andExpect(jsonPath("$.items[0].calculation.baseAmount").value(5000))
				.andExpect(jsonPath("$.items[0].calculation.rate").value(10))
				.andExpect(jsonPath("$.items[0].calculation.capApplied").value(false))
				.andExpect(jsonPath("$.items[0].appliedRules[0].benefitAmount").value(500))
				.andExpect(jsonPath("$.items[0].assumptions[?(@.type == 'PERFORMANCE_TIER')].affectsRank").value(true))
				.andExpect(jsonPath("$.items[0].remainingLimitAfter.source").value("ASSUMED"))
				.andExpect(jsonPath("$.items[0].noBenefitReason").doesNotExist())
				.andExpect(jsonPath("$.items[1].userCardId").value(alphabet.getId()))
				.andExpect(jsonPath("$.items[1].rank").value(2))
				.andExpect(jsonPath("$.items[1].expectedBenefit").value(250))
				.andExpect(jsonPath("$.excludedCards", hasSize(0)))
				.andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+09:00")));
	}

	@Test
	@DisplayName("추천 응답에 혜택별 계산식과 한도별 잔여 값 및 질문 대상 ID를 추가한다")
	void create_expandedDetails() throws Exception {
		register("굿데이");
		create("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.items[0].expectedBenefit").value(500))
				.andExpect(jsonPath("$.items[0].calculation.rate").value(10))
				.andExpect(jsonPath("$.items[0].remainingLimitAfter.source").value("ASSUMED"))
				.andExpect(jsonPath("$.items[0].appliedRules[0].calculation.rate").value(10))
				.andExpect(jsonPath("$.items[0].appliedRules[0].capGroupIds[0]").isNumber())
				.andExpect(jsonPath("$.items[0].remainingLimitsAfter[0].capGroupId").isNumber())
				.andExpect(jsonPath("$.items[0].remainingLimitsAfter[0].remainingLimitBefore").isNumber())
				.andExpect(jsonPath("$.items[0].remainingLimitsAfter[0].amountSource").value("ASSUMED"))
				.andExpect(jsonPath("$.items[0].assumptions[?(@.type == 'REMAINING_LIMIT')].capGroupId").value(
						org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.instanceOf(Number.class))));
	}

	@Test
	@DisplayName("업종만 고르면 basis는 CATEGORY이고 merchant는 null이다")
	void create_categoryOnly() throws Exception {
		register("굿데이");

		create("{\"categoryId\": %d, \"amount\": 5000}".formatted(starbucks().getCategory().getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.basis").value("CATEGORY"))
				.andExpect(jsonPath("$.merchant").doesNotExist())
				.andExpect(jsonPath("$.category.name").value("커피전문점"))
				.andExpect(jsonPath("$.items[0].expectedBenefit").value(500))
				// 비교할 2위가 없으면 차이 배지를 띄우지 않는다
				.andExpect(jsonPath("$.differenceFromSecond").doesNotExist());
	}

	@Test
	@DisplayName("혜택 정보가 검증되지 않은 카드는 items가 아니라 excludedCards에 들어간다")
	void create_excludesUnverifiedCard() throws Exception {
		register("굿데이");
		UserCard unverified = registerProduct(cardProductRepository.findAll().stream()
				.filter(product -> product.getDataStatus() == DataStatus.UNVERIFIED)
				.findFirst()
				.orElseThrow());

		create("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.excludedCards[0].userCardId").value(unverified.getId()))
				.andExpect(jsonPath("$.excludedCards[0].reason.type").value("UNVERIFIED_CARD"));
	}

	@Test
	@DisplayName("혜택 대상이 아닌 결제처면 0원과 이유를 주고, 전체 상태는 NO_BENEFIT이다")
	void create_noBenefit() throws Exception {
		register("알파벳카드S");
		Merchant gs25 = merchantRepository.findByNameContainingIgnoreCaseOrderByName("GS25").getFirst();

		create("{\"merchantId\": %d, \"amount\": 5000}".formatted(gs25.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.overallStatus").value("NO_BENEFIT"))
				.andExpect(jsonPath("$.recommendedUserCardId").doesNotExist())
				.andExpect(jsonPath("$.differenceFromSecond").doesNotExist())
				.andExpect(jsonPath("$.items[0].status").value("NO_BENEFIT"))
				.andExpect(jsonPath("$.items[0].expectedBenefit").value(0))
				.andExpect(jsonPath("$.items[0].calculation").doesNotExist())
				.andExpect(jsonPath("$.items[0].remainingLimitsAfter", hasSize(0)))
				.andExpect(jsonPath("$.items[0].noBenefitReason").isNotEmpty());
	}

	@Test
	@DisplayName("결제처와 업종을 둘 다 보내거나 둘 다 없으면 400이다")
	void create_invalidTarget() throws Exception {
		register("굿데이");
		Merchant starbucks = starbucks();

		create("{\"merchantId\": %d, \"categoryId\": %d, \"amount\": 5000}"
				.formatted(starbucks.getId(), starbucks.getCategory().getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		create("{\"amount\": 5000}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("금액이 범위를 벗어나면 400과 fieldErrors를 준다")
	void create_invalidAmount() throws Exception {
		create("{\"merchantId\": %d, \"amount\": 0}".formatted(starbucks().getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
		create("{\"merchantId\": %d, \"amount\": 10000001}".formatted(starbucks().getId()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("없는 결제처나 업종이면 404다")
	void create_notFound() throws Exception {
		create("{\"merchantId\": 999999, \"amount\": 5000}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MERCHANT_NOT_FOUND"));
		create("{\"categoryId\": 999999, \"amount\": 5000}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	@Test
	@DisplayName("등록된 카드가 없으면 409 NO_OWNED_CARDS다")
	void create_noOwnedCards() throws Exception {
		create("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_OWNED_CARDS"));
	}

	@Test
	@DisplayName("저장한 추천을 조회하면 생성 때와 같은 응답을 준다")
	void get_sameAsCreated() throws Exception {
		UserCard goodDay = register("굿데이");
		register("알파벳카드S");
		String created = create("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(created, "$.id");
		entityManager.flush();
		entityManager.clear();
		// 실적을 바꾼 뒤에도 한도별 예측과 가정은 추천 당시 스냅샷 그대로여야 한다.
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
				"/api/v1/owned-cards/{id}/state", goodDay.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"month\": \"%s\", \"performance\": {\"status\": \"BELOW\"}}".formatted(
						java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")))))
				.andExpect(status().isOk());
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get("/api/v1/recommendations/{id}", id.longValue()))
				.andExpect(status().isOk())
				.andExpect(content().json(created, JsonCompareMode.STRICT));
	}

	@Test
	@DisplayName("확장 필드가 없는 이전 스냅샷은 현재 규칙으로 채우지 않고 기존 응답을 유지한다")
	void get_legacySnapshot() throws Exception {
		register("굿데이");
		long id = createdId("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()));
		RecommendationItem item = entityManager.createQuery(
				"select i from RecommendationItem i where i.recommendation.id = :id", RecommendationItem.class)
				.setParameter("id", id).getSingleResult();
		item.getAppliedRules().remove("remainingLimitsAfter");
		item.getAppliedRules().put("rules", java.util.List.of(Map.of(
				"ruleId", 123L, "description", "이전에 저장된 혜택", "benefitAmount", 500L)));
		item.getUnknownConditions().put("assumptions", java.util.List.of(Map.of(
				"type", "PERFORMANCE_TIER", "message", "이전 가정", "affectsRank", true)));
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get("/api/v1/recommendations/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].expectedBenefit").value(500))
				.andExpect(jsonPath("$.items[0].calculation.rate").value(10))
				.andExpect(jsonPath("$.items[0].appliedRules[0].ruleId").value(123))
				.andExpect(jsonPath("$.items[0].appliedRules[0].calculation").doesNotExist())
				.andExpect(jsonPath("$.items[0].appliedRules[0].capGroupIds", hasSize(0)))
				.andExpect(jsonPath("$.items[0].assumptions[0].message").value("이전 가정"))
				.andExpect(jsonPath("$.items[0].assumptions[0].capGroupId").doesNotExist())
				.andExpect(jsonPath("$.items[0].remainingLimitsAfter", hasSize(0)));
	}

	@Test
	@DisplayName("없는 추천이나 다른 사용자의 추천을 조회하면 404 RECOMMENDATION_NOT_FOUND다")
	void get_notFound() throws Exception {
		mockMvc.perform(get("/api/v1/recommendations/{id}", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));

		User other = userRepository.save(new User("other@kattok.app", "hash", Instant.now()));
		Merchant starbucks = starbucks();
		Recommendation othersRecommendation = recommendationRepository.saveAndFlush(new Recommendation(
				other, starbucks, starbucks.getCategory(), 5000, Map.of(), OverallStatus.NO_BENEFIT, null));
		mockMvc.perform(get("/api/v1/recommendations/{id}", othersRecommendation.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("결제할게요를 여러 번 눌러도 결제 확인 대기 기록은 1건이다")
	void confirm_createsOnePendingRecord() throws Exception {
		register("굿데이");
		long id = createdId("{\"merchantId\": %d, \"amount\": 5000}".formatted(starbucks().getId()));

		mockMvc.perform(post("/api/v1/recommendations/{id}/confirm", id)).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/recommendations/{id}/confirm", id)).andExpect(status().isNoContent());

		PaymentRecord record = paymentRecordRepository.findByRecommendationId(id).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(PaymentRecordStatus.PENDING);
		assertThat(paymentRecordRepository.findAll().stream()
				.filter(r -> r.getRecommendation().getId().equals(id))).hasSize(1);
		assertThat(recommendationRepository.findById(id).orElseThrow().getSavedAt()).isNotNull();
	}

	@Test
	@DisplayName("추천 카드가 없는 결과(NO_BENEFIT)는 409 NO_RECOMMENDED_CARD이고 기록을 만들지 않는다")
	void confirm_noRecommendedCard() throws Exception {
		register("알파벳카드S");
		Merchant gs25 = merchantRepository.findByNameContainingIgnoreCaseOrderByName("GS25").getFirst();
		long id = createdId("{\"merchantId\": %d, \"amount\": 5000}".formatted(gs25.getId()));

		mockMvc.perform(post("/api/v1/recommendations/{id}/confirm", id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_RECOMMENDED_CARD"));
		assertThat(paymentRecordRepository.findByRecommendationId(id)).isEmpty();
	}

	@Test
	@DisplayName("없는 추천에 결제 확인을 만들면 404다")
	void confirm_notFound() throws Exception {
		mockMvc.perform(post("/api/v1/recommendations/{id}/confirm", 999999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
	}

	private long createdId(String body) throws Exception {
		String response = create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(response, "$.id").longValue();
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Merchant starbucks() {
		return merchantRepository.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
	}

	private UserCard register(String productKeyword) {
		return registerProduct(cardProductRepository.search(null, productKeyword, null).getFirst());
	}

	private UserCard registerProduct(CardProduct product) {
		User demo = userRepository.findByEmail("demo@kattok.app").orElseThrow();
		return userCardRepository.saveAndFlush(new UserCard(demo, product));
	}
}
