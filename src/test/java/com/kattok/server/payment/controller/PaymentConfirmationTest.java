package com.kattok.server.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.benefit.entity.BenefitCapGroup;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.benefit.repository.BenefitCapGroupRepository;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.service.JpaUserCardStateReader;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.payment.entity.PaymentRecord;
import com.kattok.server.payment.repository.PaymentRecordRepository;
import com.kattok.server.recommendation.entity.OverallStatus;
import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.recommendation.entity.RecommendationItem;
import com.kattok.server.recommendation.entity.RecommendationItemStatus;
import com.kattok.server.recommendation.repository.RecommendationItemRepository;
import com.kattok.server.recommendation.repository.RecommendationRepository;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
@Transactional
class PaymentConfirmationTest {
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private UserCardRepository cards;
	@Autowired private CardProductRepository products;
	@Autowired private MerchantRepository merchants;
	@Autowired private BenefitCapGroupRepository caps;
	@Autowired private RecommendationRepository recommendations;
	@Autowired private RecommendationItemRepository items;
	@Autowired private PaymentRecordRepository payments;
	@Autowired private TokenProvider tokens;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager em;
	@Autowired private JpaUserCardStateReader stateReader;
	@Autowired private tools.jackson.databind.json.JsonMapper json;

	@Test
	void confirmsWithSnapshotAndResendsWithoutDoubleDebit() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f, true, "2026-10-31T15:00:00Z");
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBenefit").value(300))
				.andExpect(jsonPath("$.monthlySavedBenefit.month").value("2026-11"))
				.andExpect(jsonPath("$.monthlySavedBenefit.before").value(0))
				.andExpect(jsonPath("$.monthlySavedBenefit.after").value(300))
				.andExpect(jsonPath("$.capChanges[0].before.remainingLimitAmount").isEmpty())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").value(700))
				.andExpect(jsonPath("$.capChanges[0].after.remainingCount").value(9))
				.andExpect(jsonPath("$.capChanges[0].after.amountSource").value("ESTIMATED"));
		Instant confirmed = jdbc.queryForObject("select confirmed_at from payment_records where recommendation_id = ?",
				(java.sql.ResultSet r, int n) -> r.getTimestamp(1).toInstant(), rec);
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges.length()").value(0))
				.andExpect(jsonPath("$.monthlySavedBenefit.before").value(300))
				.andExpect(jsonPath("$.monthlySavedBenefit.after").value(300));
		assertThat(jdbc.queryForObject("select confirmed_at from payment_records where recommendation_id = ?",
				(java.sql.ResultSet r, int n) -> r.getTimestamp(1).toInstant(), rec)).isEqualTo(confirmed);
	}

	@Test
	void cancelsOnlyItsActualEffectAndRestoresUnknownAfterLastEffect() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long a = recommendation(f, true, "2026-11-01T00:00:00Z");
		Long b = recommendation(f, true, "2026-11-02T00:00:00Z");
		answer(f, a, "PAID_RECOMMENDED").andExpect(status().isOk());
		answer(f, b, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").value(400));
		answer(f, a, "NOT_PAID").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").value(700))
				.andExpect(jsonPath("$.capChanges[0].after.remainingCount").value(9));
		answer(f, b, "PAID_UNREGISTERED").andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBenefit").value(0))
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").isEmpty())
				.andExpect(jsonPath("$.capChanges[0].after.amountSource").value("UNKNOWN"));
	}

	@Test
	void restoresSaturatedActualDebitInsteadOfSnapshotBenefit() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		jdbc.update("""
			insert into user_card_cap_usages(user_card_id,cap_group_id,period_month,remaining_limit_amount,
			remaining_count,source,confirmed_at) values (?,?,'2026-11-01',100,0,'USER_CONFIRMED',now())
			""", f.cardId(), f.capId());
		Long rec = recommendation(f, true, "2026-11-01T00:00:00Z");
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBenefit").value(300))
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").value(0))
				.andExpect(jsonPath("$.capChanges[0].after.countSource").value("USER_INPUT"))
				.andExpect(jsonPath("$.capChanges[0].newlyAmountExhausted").value(true))
				.andExpect(jsonPath("$.capChanges[0].newlyCountExhausted").value(false));
		answer(f, rec, "NOT_PAID").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").value(100))
				.andExpect(jsonPath("$.capChanges[0].after.amountSource").value("USER_INPUT"));
	}

	@Test
	void manualAmountOnlyAndExplicitNullSurvivePaymentCorrection() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f, true, "2026-11-01T00:00:00Z");
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk());
		mvc.perform(put("/api/v1/owned-cards/{id}/state", f.cardId())
				.header(HttpHeaders.AUTHORIZATION, f.auth()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"month\":\"2026-11\",\"caps\":[{\"capGroupId\":%s,\"remainingLimitAmount\":50}]}".formatted(f.capId())))
				.andExpect(status().isOk());
		answer(f, rec, "NOT_PAID").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").value(50))
				.andExpect(jsonPath("$.capChanges[0].after.amountSource").value("USER_INPUT"))
				.andExpect(jsonPath("$.capChanges[0].after.remainingCount").isEmpty());
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk());
		mvc.perform(put("/api/v1/owned-cards/{id}/state", f.cardId())
				.header(HttpHeaders.AUTHORIZATION, f.auth()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"month\":\"2026-11\",\"caps\":[{\"capGroupId\":%s,\"remainingLimitAmount\":null}]}".formatted(f.capId())))
				.andExpect(status().isOk());
		answer(f, rec, "NOT_PAID").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").isEmpty());
	}

	@Test
	void deletedCardAllowsOnlySameSavedAnswerOrCorrectionAway() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f, true, "2026-11-01T00:00:00Z");
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk());
		cards.findById(f.cardId()).orElseThrow().delete(); cards.flush(); em.clear();
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges.length()").value(0));
		answer(f, rec, "NOT_PAID").andExpect(status().isOk());
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	@Test
	void incompleteLegacySnapshotConflictsWithoutSavingOrChangingState() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f, false, "2026-11-01T00:00:00Z");
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAYMENT_SNAPSHOT_INCOMPLETE"));
		assertThat(jdbc.queryForObject("select status from payment_records where recommendation_id = ?", String.class, rec))
				.isEqualTo("PENDING");
		answer(f, rec, "NOT_PAID").andExpect(status().isOk());
	}

	@Test
	void dailyAndYearlyUseRecommendationKstPeriodNotConfirmationDate() throws Exception {
		for (CapPeriod period : List.of(CapPeriod.DAILY, CapPeriod.YEARLY)) {
			Fixture f = fixture(period);
			Long rec = recommendation(f, true, "2025-12-31T15:00:00Z");
			answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
					.andExpect(jsonPath("$.monthlySavedBenefit.month").value("2026-01"));
			assertThat(jdbc.queryForObject("select period_start::text from card_cap_balances where user_card_id = ?",
					String.class, f.cardId())).isEqualTo("2026-01-01");
			assertThat(jdbc.queryForObject("select cap_period from card_cap_balances where user_card_id = ?",
					String.class, f.cardId())).isEqualTo(period.name());
			var exact = stateReader.findForRecommendation(f.userId(), java.time.LocalDate.of(2026,1,1)).getFirst();
			assertThat(exact.caps().get(f.capId()).remainingAmount()).isEqualTo(700L);
			var outside = stateReader.findForRecommendation(f.userId(), period == CapPeriod.DAILY
					? java.time.LocalDate.of(2026,1,2) : java.time.LocalDate.of(2027,1,1)).getFirst();
			assertThat(outside.caps()).doesNotContainKey(f.capId());
		}
	}

	@Test
	void recommendationReaderSeparatesDailyYearlyAndMonthlyFallback() throws Exception {
		Fixture daily = fixture(CapPeriod.DAILY);
		jdbc.update("""
			insert into user_card_cap_usages(user_card_id,cap_group_id,period_month,remaining_limit_amount,
			remaining_count,source,confirmed_at) values (?,?,'2026-11-01',25,1,'USER_CONFIRMED',now())
			""", daily.cardId(), daily.capId());
		// 과거 월 단위로 저장했던 DAILY 값은 현재 날짜 상태로 재사용하면 안 된다.
		assertThat(stateReader.findForRecommendation(daily.userId(), java.time.YearMonth.of(2026,11)).getFirst().caps())
				.doesNotContainKey(daily.capId());
	}

	@Test
	void laterCardIsUncalculatedAndItsConfirmationNameIsFrozen() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f, true, "2026-11-01T00:00:00Z");
		var product = products.findAll().get(1);
		String originalName = product.getName();
		UserCard later = cards.saveAndFlush(new UserCard(users.findById(f.userId()).orElseThrow(), product));
		mvc.perform(put("/api/v1/recommendations/{id}/payment", rec).header(HttpHeaders.AUTHORIZATION, f.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"PAID_OTHER\",\"userCardId\":%s}".formatted(later.getId())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.savedBenefit").isEmpty())
				.andExpect(jsonPath("$.capChanges.length()").value(0))
				.andExpect(jsonPath("$.monthlySavedBenefit.after").value(0));
		jdbc.update("update card_products set name=? where id=?", "변경 이름", product.getId()); em.clear();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payment-records")
				.param("month","2026-11").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.records[0].userCard.productName").value(originalName));
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBenefit").value(300))
				.andExpect(jsonPath("$.capChanges[0].userCardId").value(f.cardId()));
	}

	@Test
	void trueUncappedSnapshotSucceedsButMissingAccountingIsNotTreatedAsUncapped() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f, true, "2026-11-01T00:00:00Z");
		jdbc.update("""
			update recommendation_items set applied_rules = jsonb_set(jsonb_set(applied_rules,
			'{rules,0,capGroupIds}','[]'),'{remainingLimitsAfter}','[]') where recommendation_id=?
			""",rec); em.clear();
		answer(f, rec, "PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBenefit").value(300)).andExpect(jsonPath("$.capChanges.length()").value(0));
	}

	@Test
	void manualCountKeepsAmountSourceAndSurvivesCorrection() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f,true,"2026-11-01T00:00:00Z");
		answer(f,rec,"PAID_RECOMMENDED").andExpect(status().isOk());
		mvc.perform(put("/api/v1/owned-cards/{id}/state",f.cardId()).header(HttpHeaders.AUTHORIZATION,f.auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"month\":\"2026-11\",\"caps\":[{\"capGroupId\":%s,\"remainingCount\":5}]}".formatted(f.capId())))
				.andExpect(status().isOk());
		var state = stateReader.findForRecommendation(f.userId(),java.time.LocalDate.of(2026,11,1)).getFirst().caps().get(f.capId());
		assertThat(state.remainingAmount()).isEqualTo(700L);
		assertThat(state.amountSource()).isEqualTo(com.kattok.server.card.entity.ConfirmationSource.SYSTEM_ESTIMATED);
		assertThat(state.countSource()).isEqualTo(com.kattok.server.card.entity.ConfirmationSource.USER_CONFIRMED);
		answer(f,rec,"NOT_PAID").andExpect(status().isOk())
				.andExpect(jsonPath("$.capChanges[0].after.remainingLimitAmount").isEmpty())
				.andExpect(jsonPath("$.capChanges[0].after.remainingCount").value(5));
	}

	@Test
	void invalidResultsOwnershipAndCardCombinationsAreRejected() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f,true,"2026-11-01T00:00:00Z");
		for (String body : List.of("{}","{\"result\":\"PENDING\"}","{\"result\":\"INVALID\"}",
				"{\"result\":\"PAID_OTHER\"}","{\"result\":\"NOT_PAID\",\"userCardId\":1}",
				"{\"result\":\"PAID_OTHER\",\"userCardId\":%s}".formatted(f.cardId()))) {
			mvc.perform(put("/api/v1/recommendations/{id}/payment",rec).header(HttpHeaders.AUTHORIZATION,f.auth())
					.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
		Fixture other = fixture(CapPeriod.MONTHLY);
		mvc.perform(put("/api/v1/recommendations/{id}/payment",rec).header(HttpHeaders.AUTHORIZATION,other.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"NOT_PAID\"}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
		mvc.perform(put("/api/v1/recommendations/{id}/payment",rec).header(HttpHeaders.AUTHORIZATION,f.auth())
				.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"PAID_OTHER\",\"userCardId\":%s}".formatted(other.cardId())))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	@Test
	void legacySavedSameAnswerReturnsSnapshotWithoutInventingNewDebits() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		Long rec = recommendation(f,true,"2026-11-01T00:00:00Z");
		payments.findByRecommendationId(rec).orElseThrow().confirmPaidWithRecommended(); payments.flush(); em.clear();
		answer(f,rec,"PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.userCard.userCardId").value(f.cardId()))
				.andExpect(jsonPath("$.savedBenefit").value(300))
				.andExpect(jsonPath("$.capChanges.length()").value(0));
	}

	@Test
	void generatedUncappedGroupCanBeConfirmedWithoutLosingSnapshotMeaning() throws Exception {
		Fixture f = fixture(CapPeriod.MONTHLY);
		UserCard card = cards.findById(f.cardId()).orElseThrow();
		var uncapped = caps.saveAndFlush(new BenefitCapGroup(card.getProduct(),CapPeriod.MONTHLY,null,null,null,"무한도"));
		var merchant = merchants.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		jdbc.update("update card_products set data_status='CALCULABLE' where id=?",card.getProduct().getId());
		jdbc.update("update benefit_rules set valid_to='2020-01-01' where product_id=?",card.getProduct().getId());
		jdbc.update("""
			insert into benefit_rules(product_id,merchant_id,cap_group_id,benefit_type,amount,conditions,
			verification_status,rule_version,valid_from,display_text)
			values(?,?,?,'DISCOUNT_FIXED',5000,'{}','VERIFIED',1,'2020-01-01','무한도 혜택')
			""",card.getProduct().getId(),merchant.getId(),uncapped.getId()); em.clear();
		String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/recommendations")
				.header(HttpHeaders.AUTHORIZATION,f.auth()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"merchantId\":%s,\"amount\":5000}".formatted(merchant.getId())))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].expectedBenefit").value(5000))
				.andExpect(jsonPath("$.items[0].appliedRules[0].capGroupIds[0]").value(uncapped.getId()))
				.andExpect(jsonPath("$.items[0].remainingLimitsAfter.length()").value(0)).andReturn().getResponse().getContentAsString();
		Long rec = json.readTree(body).get("id").asLong();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/recommendations/{id}/confirm",rec)
				.header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isNoContent());
		answer(f,rec,"PAID_RECOMMENDED").andExpect(status().isOk())
				.andExpect(jsonPath("$.savedBenefit").value(5000)).andExpect(jsonPath("$.capChanges.length()").value(0));
	}

	private org.springframework.test.web.servlet.ResultActions answer(Fixture f, Long rec, String result) throws Exception {
		em.flush(); em.clear();
		return mvc.perform(put("/api/v1/recommendations/{id}/payment", rec)
				.header(HttpHeaders.AUTHORIZATION, f.auth()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"result\":\"%s\"}".formatted(result)));
	}

	private Fixture fixture(CapPeriod period) {
		User user = users.saveAndFlush(new User(UUID.randomUUID()+"@kattok.test", "unused", Instant.now()));
		UserCard card = cards.saveAndFlush(new UserCard(user, products.findAll().getFirst()));
		BenefitCapGroup cap = caps.saveAndFlush(new BenefitCapGroup(card.getProduct(), period, 1000L, 10, null, "검증용 한도"));
		return new Fixture(user.getId(), card.getId(), cap.getId(), period, "Bearer " + tokens.issue(user.getId()).value());
	}

	private Long recommendation(Fixture f, boolean complete, String at) {
		User user = users.findById(f.userId()).orElseThrow();
		UserCard card = cards.findById(f.cardId()).orElseThrow();
		var merchant = merchants.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		var rec = recommendations.saveAndFlush(new Recommendation(user, merchant, merchant.getCategory(), 5000L,
				Map.of(), OverallStatus.CONFIRMED, null));
		Map<String, Object> applied = complete ? Map.of(
				"rules", List.of(Map.of("ruleId", 1, "description", "스냅샷 혜택", "benefitAmount", 300,
						"calculation", Map.of("baseAmount", 5000, "rate", 6, "capApplied", false), "capGroupIds", List.of(f.capId()))),
				"remainingLimitsAfter", List.of(Map.ofEntries(
						Map.entry("capGroupId", f.capId()), Map.entry("description", "검증용 한도"), Map.entry("capPeriod", f.period().name()),
						Map.entry("capAmount", 1000), Map.entry("capCount", 10), Map.entry("remainingLimitBefore", 1000),
						Map.entry("remainingLimitAmount", 700), Map.entry("remainingCountBefore", 10), Map.entry("remainingCount", 9),
						Map.entry("amountSource", "ASSUMED"), Map.entry("countSource", "ASSUMED")))) : Map.of();
		items.saveAndFlush(new RecommendationItem(rec, card, "추천 당시 카드", "추천 당시 카드사",
				RecommendationItemStatus.CONFIRMED, true, 1, 300L, 300L, applied, Map.of(), Map.of()));
		payments.saveAndFlush(new PaymentRecord(user, rec));
		jdbc.update("update recommendations set created_at = ? where id = ?", Timestamp.from(Instant.parse(at)), rec.getId());
		em.clear();
		return rec.getId();
	}

	private record Fixture(Long userId, Long cardId, Long capId, CapPeriod period, String auth) {}
}
