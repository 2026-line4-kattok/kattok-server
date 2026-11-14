package com.kattok.server.recommendation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.repository.MerchantRepository;
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
