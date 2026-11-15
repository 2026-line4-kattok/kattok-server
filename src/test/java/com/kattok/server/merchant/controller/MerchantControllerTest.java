package com.kattok.server.merchant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.recommendation.entity.OverallStatus;
import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.recommendation.repository.RecommendationRepository;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class MerchantControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MerchantRepository merchantRepository;

	@Autowired
	private RecommendationRepository recommendationRepository;

	@Autowired
	private UserRepository userRepository;

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
	@DisplayName("키워드 없이 호출하면 빈 배열을 반환한다")
	void searchWithoutKeyword() throws Exception {
		mockMvc.perform(get("/api/v1/merchants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
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

	@Test
	@DisplayName("확정한 추천이 없으면 최근 결제한 곳은 빈 배열이다")
	void recent_withoutConfirmedRecommendation_returnsEmpty() throws Exception {
		mockMvc.perform(get("/api/v1/merchants/recent"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("확정한 추천의 결제처를 최신순으로, 중복 없이 최대 3개 반환한다")
	void recent_returnsDistinctMerchantsInOrder() throws Exception {
		User demoUser = userRepository.findByEmail("demo@kattok.app").orElseThrow();
		Merchant starbucks = merchantByKeyword("스타벅스");
		Merchant coupang = merchantByKeyword("쿠팡");

		confirmRecommendation(demoUser, starbucks);
		Thread.sleep(5);
		confirmRecommendation(demoUser, coupang);
		Thread.sleep(5);
		confirmRecommendation(demoUser, starbucks); // 가장 최근인데, 이미 나온 결제처라 중복 없이 1건으로 합쳐짐

		mockMvc.perform(get("/api/v1/merchants/recent"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("스타벅스"))
				.andExpect(jsonPath("$[1].name").value("쿠팡"));
	}

	@Test
	@DisplayName("업종만 고른 추천(결제처 없음)은 최근 결제한 곳에서 빠진다")
	void recent_excludesCategoryOnlyRecommendation() throws Exception {
		User demoUser = userRepository.findByEmail("demo@kattok.app").orElseThrow();
		Merchant starbucks = merchantByKeyword("스타벅스");

		Recommendation categoryOnly = recommendationRepository.save(new Recommendation(
				demoUser, null, starbucks.getCategory(), 5000L, Map.of(), OverallStatus.CONFIRMED, null));
		categoryOnly.confirm();
		recommendationRepository.save(categoryOnly);

		mockMvc.perform(get("/api/v1/merchants/recent"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	private Merchant merchantByKeyword(String keyword) {
		return merchantRepository.findByNameContainingIgnoreCaseOrderByName(keyword).stream()
				.findFirst()
				.orElseThrow();
	}

	private void confirmRecommendation(User user, Merchant merchant) {
		Recommendation recommendation = recommendationRepository.save(new Recommendation(
				user, merchant, merchant.getCategory(), 5000L, Map.of(), OverallStatus.CONFIRMED, null));
		recommendation.confirm();
		recommendationRepository.save(recommendation);
	}
}
