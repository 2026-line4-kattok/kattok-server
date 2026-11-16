package com.kattok.server.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.benefit.entity.BenefitCapGroup;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.benefit.repository.BenefitCapGroupRepository;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.repository.CardIssuerRepository;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
@Transactional
class VerifiedBenefitStackingTest {
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager em;
	@Autowired private UserRepository users;
	@Autowired private CardIssuerRepository issuers;
	@Autowired private CardProductRepository products;
	@Autowired private BenefitCapGroupRepository caps;
	@Autowired private MerchantRepository merchants;
	@Autowired private TokenProvider tokens;
	@Autowired private JsonMapper json;

	@Test void unmarkedRulesRemainExclusiveAndStoredResultsDoNotChangeWhenMarkedLater() throws Exception {
		assertThat(jdbc.queryForObject("select count(*) from benefit_rules where stackable", Long.class)).isZero();
		Fixture f = fixture(1000);
		String legacy = create(f);
		assertThat(json.readTree(legacy).get("items").get(0).get("expectedBenefit").asLong()).isEqualTo(250);
		markVerifiedPair(f);
		String stacked = create(f);
		JsonNode item = json.readTree(stacked).get("items").get(0);
		assertThat(item.get("expectedBenefit").asLong()).isEqualTo(300);
		assertThat(item.get("appliedRules").size()).isEqualTo(2);
		long legacyId = json.readTree(legacy).get("id").asLong();
		flush();
		mvc.perform(get("/api/v1/recommendations/{id}", legacyId).header(HttpHeaders.AUTHORIZATION, f.auth()))
				.andExpect(status().isOk()).andExpect(content().json(legacy, JsonCompareMode.STRICT));
	}

	@ParameterizedTest @ValueSource(longs = {1000, 250})
	void generatedStackedSnapshotDebitsAndRestoresSharedAndChildCaps(long parentAmount) throws Exception {
		Fixture f = fixture(parentAmount);
		markVerifiedPair(f);
		long expected = parentAmount == 250 ? 250 : 300;
		long firstAmount = expected - 200;
		String created = create(f);
		JsonNode rec = json.readTree(created), item = rec.get("items").get(0);
		long id = rec.get("id").asLong();
		assertThat(item.get("expectedBenefit").asLong()).isEqualTo(expected);
		assertThat(item.get("appliedRules").size()).isEqualTo(2);
		assertThat(item.get("appliedRules").get(0).get("ruleId").asLong()).isEqualTo(f.extraRule());
		assertThat(item.get("appliedRules").get(0).get("benefitAmount").asLong()).isEqualTo(200);
		assertThat(item.get("appliedRules").get(1).get("benefitAmount").asLong()).isEqualTo(firstAmount);
		assertThat(item.get("appliedRules").get(0).get("calculation").get("rate").asDouble()).isEqualTo(2);
		assertThat(item.get("appliedRules").get(1).get("calculation").get("rate").asDouble()).isEqualTo(1);
		assertThat(item.get("calculation").get("capApplied").asBoolean()).isFalse();
		assertThat(item.get("remainingLimitAfter").get("amount").asLong()).isEqualTo(parentAmount - expected);
		assertThat(item.get("remainingLimitsAfter").size()).isEqualTo(3);
		assertThat(projection(item, f.parent()).get("remainingCount").asInt()).isEqualTo(1);
		assertThat(projection(item, f.parent()).get("remainingLimitAmount").asLong()).isEqualTo(parentAmount - expected);
		assertThat(projection(item, f.first()).get("remainingLimitAmount").asLong()).isEqualTo(1000 - firstAmount);
		assertThat(projection(item, f.extra()).get("remainingLimitAmount").asLong()).isEqualTo(800);
		assertThat(jdbc.queryForObject("select count(*) from card_cap_balances where user_card_id=?", Long.class, f.card())).isZero();

		// 현재 규칙이 바뀌어도 저장된 합산·차감량을 사용한다.
		jdbc.update("update benefit_rules set stackable=false,rate=0.50 where product_id=?", f.product());
		flush();
		mvc.perform(get("/api/v1/recommendations/{id}", id).header(HttpHeaders.AUTHORIZATION, f.auth()))
				.andExpect(status().isOk()).andExpect(content().json(created, JsonCompareMode.STRICT));
		confirmPending(f, id);
		answer(f, id, "PAID_RECOMMENDED").andExpect(status().isOk()).andExpect(jsonPath("$.savedBenefit").value(expected))
				.andExpect(jsonPath("$.monthlySavedBenefit.after").value(expected));
		assertBalance(f, f.parent(), parentAmount - expected, 1);
		assertBalance(f, f.first(), 1000 - firstAmount, null);
		assertBalance(f, f.extra(), 800L, null);
		answer(f, id, "PAID_RECOMMENDED").andExpect(status().isOk()).andExpect(jsonPath("$.capChanges.length()").value(0));
		answer(f, id, "NOT_PAID").andExpect(status().isOk()).andExpect(jsonPath("$.monthlySavedBenefit.after").value(0));
		for (Long cap : new Long[] {f.parent(), f.first(), f.extra()}) {
			assertBalance(f, cap, null, null);
		}
	}

	@Test void manualAmountOverridesStackedCorrectionWhileCountRestoresIndependently() throws Exception {
		Fixture f = fixture(1000);
		markVerifiedPair(f);
		JsonNode rec = json.readTree(create(f));
		long id = rec.get("id").asLong();
		confirmPending(f, id);
		answer(f, id, "PAID_RECOMMENDED").andExpect(status().isOk());
		String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		mvc.perform(put("/api/v1/owned-cards/{id}/state", f.card()).header(HttpHeaders.AUTHORIZATION, f.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"%s\",\"caps\":[{\"capGroupId\":%d,\"remainingLimitAmount\":50}]}".formatted(month, f.parent())))
				.andExpect(status().isOk());
		answer(f, id, "NOT_PAID").andExpect(status().isOk());
		assertBalance(f, f.parent(), 50L, null);
		assertThat(jdbc.queryForObject("select amount_source from card_cap_balances where user_card_id=? and cap_group_id=?",
				String.class, f.card(), f.parent())).isEqualTo("USER_INPUT");
	}

	@Test void knownAmountAndUnknownCountKeepIndependentSourcesInStackedSnapshot() throws Exception {
		Fixture f = fixture(1000);
		markVerifiedPair(f);
		String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
		mvc.perform(put("/api/v1/owned-cards/{id}/state", f.card()).header(HttpHeaders.AUTHORIZATION, f.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"%s\",\"caps\":[{\"capGroupId\":%d,\"remainingLimitAmount\":900}]}".formatted(month, f.parent())))
				.andExpect(status().isOk());
		JsonNode rec = json.readTree(create(f)), item = rec.get("items").get(0);
		JsonNode shared = projection(item, f.parent());
		assertThat(shared.get("amountSource").asText()).isEqualTo("USER_INPUT");
		assertThat(shared.get("countSource").asText()).isEqualTo("ASSUMED");
		assertThat(shared.get("remainingLimitAmount").asLong()).isEqualTo(600);
		int countQuestions = 0;
		for (JsonNode assumption : item.get("assumptions")) {
			if (assumption.get("capGroupId").asLong() == f.parent()) {
				assertThat(assumption.get("type").asText()).isEqualTo("REMAINING_COUNT");
				countQuestions++;
			}
		}
		assertThat(countQuestions).isEqualTo(1);
		long id = rec.get("id").asLong();
		confirmPending(f, id);
		answer(f, id, "PAID_RECOMMENDED").andExpect(status().isOk());
		assertBalance(f, f.parent(), 600L, 1);
		answer(f, id, "NOT_PAID").andExpect(status().isOk());
		assertBalance(f, f.parent(), 900L, null);
	}

	@Test void stackedDailyMonthlyAndYearlyCapsUseSavedRecommendationPeriods() throws Exception {
		Fixture f = fixture(1000);
		jdbc.update("update benefit_cap_groups set cap_period='YEARLY' where id=?", f.parent());
		jdbc.update("update benefit_cap_groups set cap_period='DAILY' where id=?", f.first());
		markVerifiedPair(f);
		JsonNode rec = json.readTree(create(f));
		var date = java.time.OffsetDateTime.parse(rec.get("createdAt").asText()).toLocalDate();
		long id = rec.get("id").asLong();
		// 규칙을 바꿔도 결제 원장은 추천 당시 기간 정보를 사용한다.
		jdbc.update("update benefit_cap_groups set cap_period='MONTHLY' where id in (?,?)", f.parent(), f.first());
		flush();
		confirmPending(f, id);
		answer(f, id, "PAID_RECOMMENDED").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select period_start::text from card_cap_balances where user_card_id=? and cap_group_id=?",
				String.class, f.card(), f.parent())).isEqualTo(date.withDayOfYear(1).toString());
		assertThat(jdbc.queryForObject("select period_start::text from card_cap_balances where user_card_id=? and cap_group_id=?",
				String.class, f.card(), f.first())).isEqualTo(date.toString());
		assertThat(jdbc.queryForObject("select period_start::text from card_cap_balances where user_card_id=? and cap_group_id=?",
				String.class, f.card(), f.extra())).isEqualTo(date.withDayOfMonth(1).toString());
		assertBalance(f, f.parent(), 700L, 1);
		answer(f, id, "NOT_PAID").andExpect(status().isOk());
		assertBalance(f, f.parent(), null, null);
	}

	private Fixture fixture(long parentAmount) throws Exception {
		User user = users.saveAndFlush(new User(UUID.randomUUID() + "@stacking.test", "unused", Instant.now()));
		String auth = "Bearer " + tokens.issue(user.getId()).value();
		CardProduct product = products.saveAndFlush(new CardProduct(issuers.findAll().getFirst(), UUID.randomUUID().toString(),
				"합산 테스트 카드", new String[0], CardType.CREDIT, null, DataStatus.CALCULABLE, null, null, 0L, PerformanceType.NONE));
		var parent = caps.saveAndFlush(new BenefitCapGroup(product, CapPeriod.MONTHLY, parentAmount, 2, null, "공유 한도"));
		var first = caps.saveAndFlush(new BenefitCapGroup(product, CapPeriod.MONTHLY, 1000L, null, parent, "기본 한도"));
		var extra = caps.saveAndFlush(new BenefitCapGroup(product, CapPeriod.MONTHLY, 1000L, null, parent, "추가 한도"));
		var exclusive = caps.saveAndFlush(new BenefitCapGroup(product, CapPeriod.MONTHLY, null, null, null, "단독 혜택"));
		var merchant = merchants.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		long baseRule = rule(product.getId(), merchant.getId(), first.getId(), "0.01");
		long extraRule = rule(product.getId(), merchant.getId(), extra.getId(), "0.02");
		rule(product.getId(), merchant.getId(), exclusive.getId(), parentAmount == 250 ? "0.015" : "0.025");
		flush();
		String registered = mvc.perform(post("/api/v1/owned-cards").header(HttpHeaders.AUTHORIZATION, auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":%d}".formatted(product.getId())))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return new Fixture(product.getId(), json.readTree(registered).get("id").asLong(), merchant.getId(),
				parent.getId(), first.getId(), extra.getId(), baseRule, extraRule, auth);
	}

	private long rule(long product, long merchant, long cap, String rate) {
		return jdbc.queryForObject("""
			insert into benefit_rules(product_id,merchant_id,cap_group_id,benefit_type,rate,conditions,
			verification_status,rule_version,valid_from,display_text)
			values(?,?,?,'DISCOUNT_RATE',?::numeric,'{}','VERIFIED',1,current_date,'검증 테스트 혜택') returning id
			""", Long.class, product, merchant, cap, rate);
	}
	private void markVerifiedPair(Fixture f) {
		jdbc.update("update benefit_rules set stackable=true where id in (?,?)", f.baseRule(), f.extraRule());
		flush();
	}
	private String create(Fixture f) throws Exception {
		flush();
		return mvc.perform(post("/api/v1/recommendations").header(HttpHeaders.AUTHORIZATION, f.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":%d,\"amount\":10000}".formatted(f.merchant())))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
	}
	private void confirmPending(Fixture f, long id) throws Exception {
		mvc.perform(post("/api/v1/recommendations/{id}/confirm", id).header(HttpHeaders.AUTHORIZATION, f.auth()))
				.andExpect(status().isNoContent());
	}
	private org.springframework.test.web.servlet.ResultActions answer(Fixture f, long id, String result) throws Exception {
		flush();
		return mvc.perform(put("/api/v1/recommendations/{id}/payment", id).header(HttpHeaders.AUTHORIZATION, f.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"%s\"}".formatted(result)));
	}
	private void assertBalance(Fixture f, long cap, Long amount, Integer count) {
		var row = jdbc.queryForMap("select remaining_amount,remaining_count from card_cap_balances where user_card_id=? and cap_group_id=?", f.card(), cap);
		assertThat(row.get("remaining_amount")).isEqualTo(amount);
		assertThat(row.get("remaining_count")).isEqualTo(count);
	}
	private JsonNode projection(JsonNode item, long cap) {
		for (JsonNode node : item.get("remainingLimitsAfter")) if (node.get("capGroupId").asLong() == cap) return node;
		throw new AssertionError("한도 없음: " + cap);
	}
	private void flush() { em.flush(); em.clear(); }
	private record Fixture(long product, long card, long merchant, long parent, long first, long extra,
			long baseRule, long extraRule, String auth) {}
}
