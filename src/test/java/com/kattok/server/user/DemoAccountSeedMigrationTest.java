package com.kattok.server.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.kattok.server.TestcontainersConfiguration;

/**
 * 운영 배포처럼 demoseed=true로 마이그레이션하면 시연용 테스트 계정이 발표용 카드 4장으로 맞춰지고(V37),
 * 발표·부스 시나리오가 실제 추천·결제 API에서 안내한 숫자대로 나온다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {"spring.flyway.placeholders.demoseed=true", "kattok.auth.anonymous-demo-user=true"})
@AutoConfigureMockMvc
class DemoAccountSeedMigrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("시연 계정에 카드 4장이 들어가고, 실적 확정 카드는 이번 달·다음 달 모두 확정이다")
	void seedsPresentationCards() throws Exception {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				select p.product_key, t.min_amount, s.performance_status
				from users u
				join user_cards c on c.user_id = u.id and c.deleted_at is null
				join card_products p on p.id = c.product_id
				left join user_card_states s on s.user_card_id = c.id
				left join card_performance_tiers t on t.id = s.performance_tier_id
				where u.email = 'demo@kattok.app'
				order by p.product_key, s.period_month
				""");

		assertThat(rows).extracting(row -> row.get("product_key") + ":" + row.get("min_amount"))
				.containsExactly("CR004:null", "CT04:150000", "CT04:150000", "CT06:300000", "CT06:300000", "CT10:null");
		mockMvc.perform(get("/api/v1/owned-cards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4));
	}

	@Test
	@DisplayName("시나리오: 브랜드 한정 → 월 한도 소진 → 결제금액별 할인율 → 실적 미확정 조건부 추천")
	void presentationScenario() throws Exception {
		long kaPick = userCardId("CT04");
		long macao = userCardId("CT06");
		long goodDay = userCardId("CR004");
		long travlog = userCardId("CT10");

		// 1) 브랜드 한정: 같은 온라인쇼핑이어도 KaPick은 쿠팡만 7%
		String coupang = recommend("merchantId", merchantId("쿠팡"), 10000);
		assertThat(top(coupang)).isEqualTo(kaPick);
		assertThat(benefit(coupang, kaPick)).isEqualTo(700);
		String st11 = recommend("merchantId", merchantId("11번가"), 10000);
		assertThat(benefit(st11, kaPick)).isZero();
		assertThat(top(st11)).isEqualTo(travlog);
		assertThat(benefit(st11, travlog)).isEqualTo(100);

		// 2) 월 한도: KaPick 쇼핑 한도 1,500원 → 쿠팡 1만원 두 번 결제(1,400원) 뒤에는 100원만 남아 MACAO 3%가 1위
		payTop(coupang);
		payTop(recommend("merchantId", merchantId("쿠팡"), 10000));
		String afterCap = recommend("merchantId", merchantId("쿠팡"), 10000);
		assertThat(benefit(afterCap, kaPick)).isEqualTo(100);
		assertThat(top(afterCap)).isEqualTo(macao);
		assertThat(benefit(afterCap, macao)).isEqualTo(300);

		// 3) 결제금액별 할인율: MACAO 이마트 3만원 3%, 10만원 10%(장보기 한도 1만원)
		String emart30k = recommend("merchantId", merchantId("이마트"), 30000);
		assertThat(top(emart30k)).isEqualTo(macao);
		assertThat(benefit(emart30k, macao)).isEqualTo(900);
		String emart100k = recommend("merchantId", merchantId("이마트"), 100000);
		assertThat(benefit(emart100k, macao)).isEqualTo(10000);

		// 4) 실적 미확정: 굿데이카드 음식점 10%는 60만원 이상 조건부, 확정 기준 1위는 트래블로그 1%
		String restaurant = recommend("categoryId", categoryId("RESTAURANT"), 20000);
		assertThat((String) JsonPath.read(restaurant, "$.overallStatus")).isEqualTo("CONDITIONAL");
		assertThat(benefit(restaurant, goodDay)).isEqualTo(2000);
		assertThat(((Number) JsonPath.read(restaurant, "$.confirmedOnlyTopUserCardId")).longValue()).isEqualTo(travlog);
	}

	private String recommend(String field, long id, long amount) throws Exception {
		return mockMvc.perform(post("/api/v1/recommendations").contentType(MediaType.APPLICATION_JSON)
						.content("{\"%s\": %d, \"amount\": %d}".formatted(field, id, amount)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	/** 추천 1위 카드로 결제 확정까지 마쳐 한도를 쓴다. */
	private void payTop(String recommendation) throws Exception {
		long id = ((Number) JsonPath.read(recommendation, "$.id")).longValue();
		mockMvc.perform(post("/api/v1/recommendations/{id}/confirm", id)).andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/recommendations/{id}/payment", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"result\": \"PAID_RECOMMENDED\"}"))
				.andExpect(status().isOk());
	}

	private static long top(String recommendation) {
		return ((Number) JsonPath.read(recommendation, "$.recommendedUserCardId")).longValue();
	}

	private static long benefit(String recommendation, long userCardId) {
		List<Number> values = JsonPath.read(recommendation, "$.items[?(@.userCardId == %d)].expectedBenefit".formatted(userCardId));
		assertThat(values).as("비교 결과에 카드 %d가 있어야 한다", userCardId).hasSize(1);
		return values.getFirst().longValue();
	}

	private long userCardId(String productKey) {
		return jdbc.queryForObject("""
				select c.id from user_cards c join users u on u.id = c.user_id join card_products p on p.id = c.product_id
				where u.email = 'demo@kattok.app' and p.product_key = ? and c.deleted_at is null
				""", Long.class, productKey);
	}

	private long merchantId(String name) {
		return jdbc.queryForObject("select id from merchants where name = ?", Long.class, name);
	}

	private long categoryId(String code) {
		return jdbc.queryForObject("select id from merchant_categories where code = ?", Long.class, code);
	}
}
