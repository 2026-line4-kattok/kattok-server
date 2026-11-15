package com.kattok.server.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.entity.UserCard;
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
class PaymentQueryTest {
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private UserCardRepository cards;
	@Autowired private CardProductRepository products;
	@Autowired private MerchantRepository merchants;
	@Autowired private RecommendationRepository recommendations;
	@Autowired private RecommendationItemRepository items;
	@Autowired private PaymentRecordRepository payments;
	@Autowired private TokenProvider tokens;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager em;

	@Test
	void pendingUsesRecommendationTimeAndOnlyCurrentUser() throws Exception {
		User user = user();
		UserCard card = card(user, 0);
		PaymentRecord newer = record(user, card, "2026-11-01T00:00:00Z", 500L);
		PaymentRecord older = record(user, card, "2026-10-01T00:00:00Z", 500L);
		PaymentRecord done = record(user, card, "2026-11-02T00:00:00Z", 500L);
		done.confirmNotPaid();
		User other = user();
		record(other, card(other, 0), "2026-11-03T00:00:00Z", 900L);
		payments.flush(); em.clear();
		mvc.perform(get("/api/v1/payment-records/pending").header(HttpHeaders.AUTHORIZATION, auth(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].recommendationId").value(newer.getRecommendation().getId()))
				.andExpect(jsonPath("$[1].recommendationId").value(older.getRecommendation().getId()))
				.andExpect(jsonPath("$[0].result").value("PENDING"))
				.andExpect(jsonPath("$[0].userCard").isEmpty())
				.andExpect(jsonPath("$[0].savedBenefit").value(0))
				.andExpect(jsonPath("$[0].confirmedAt").isEmpty());
	}

	@Test
	void optionsUseSnapshotAndShowDeletedRecommendedCard() throws Exception {
		User user = user();
		UserCard recommended = card(user, 0);
		UserCard compared = card(user, 1);
		UserCard later = card(user, 2);
		PaymentRecord payment = record(user, recommended, "2026-11-01T00:00:00Z", 500L);
		items.save(new RecommendationItem(payment.getRecommendation(), compared, "당시 상품명", "당시 카드사",
				RecommendationItemStatus.CONFIRMED, false, 2, 250L, 250L, Map.of(), Map.of(), Map.of()));
		recommended.delete();
		cards.flush(); items.flush(); em.clear();
		mvc.perform(get("/api/v1/recommendations/{id}/payment", payment.getRecommendation().getId())
				.header(HttpHeaders.AUTHORIZATION, auth(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendedCard.deleted").value(true))
				.andExpect(jsonPath("$.recommendedCard.productName").value("추천 당시 상품명"))
				.andExpect(jsonPath("$.cardOptions.length()").value(2))
				.andExpect(jsonPath("$.cardOptions[0].userCardId").value(compared.getId()))
				.andExpect(jsonPath("$.cardOptions[0].expectedBenefit").value(250))
				.andExpect(jsonPath("$.cardOptions[1].userCardId").value(later.getId()))
				.andExpect(jsonPath("$.cardOptions[1].expectedBenefit").isEmpty());
	}

	@Test
	void historyUsesKstMonthActualCardAndCountsZeroButNotNull() throws Exception {
		User user = user();
		UserCard recommended = card(user, 0);
		UserCard actual = card(user, 1);
		PaymentRecord october = record(user, recommended, "2026-10-31T14:59:59Z", 900L);
		october.confirmPaidWithRecommended();
		PaymentRecord november = record(user, recommended, "2026-10-31T15:00:00Z", 500L);
		items.save(new RecommendationItem(november.getRecommendation(), actual, "실제 사용 당시 이름", "당시 카드사",
				RecommendationItemStatus.CONFIRMED, false, 2, 250L, 250L, Map.of(), Map.of(), Map.of()));
		november.confirmPaidWithOther(actual);
		PaymentRecord zero = record(user, actual, "2026-11-02T00:00:00Z", 0L);
		zero.confirmPaidWithRecommended();
		PaymentRecord unknown = record(user, recommended, "2026-11-03T00:00:00Z", 500L);
		unknown.confirmPaidWithOther(actual);
		record(user, recommended, "2026-09-01T00:00:00Z", 500L);
		actual.delete(); cards.flush(); items.flush(); payments.flush(); em.clear();
		mvc.perform(get("/api/v1/payment-records").param("month", "2026-11")
				.param("userCardId", actual.getId().toString()).header(HttpHeaders.AUTHORIZATION, auth(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value("2026-11"))
				.andExpect(jsonPath("$.savedBenefit.amount").value(250))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(2))
				.andExpect(jsonPath("$.byCard.length()").value(1))
				.andExpect(jsonPath("$.byCard[0].userCardId").value(actual.getId()))
				.andExpect(jsonPath("$.byCard[0].productName").value("추천 당시 상품명"))
				.andExpect(jsonPath("$.records.length()").value(3))
				.andExpect(jsonPath("$.records[0].savedBenefit").isEmpty())
				.andExpect(jsonPath("$.pendingCount").value(1));
	}

	@Test
	void guardsOwnershipAndMissingPayment() throws Exception {
		User owner = user();
		PaymentRecord record = record(owner, card(owner, 0), "2026-11-01T00:00:00Z", 500L);
		User other = user();
		mvc.perform(get("/api/v1/recommendations/{id}/payment", record.getRecommendation().getId())
				.header(HttpHeaders.AUTHORIZATION, auth(other)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
		payments.delete(record); payments.flush();
		mvc.perform(get("/api/v1/recommendations/{id}/payment", record.getRecommendation().getId())
				.header(HttpHeaders.AUTHORIZATION, auth(owner)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_REQUESTED"));
		mvc.perform(get("/api/v1/payment-records").param("month", "2026-13")
				.header(HttpHeaders.AUTHORIZATION, auth(owner))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/payment-records/pending")).andExpect(status().isUnauthorized());
	}

	@Test
	void historyExcludesNonPaidAndUnregisteredButKeepsRecordsAndEmptyMonth() throws Exception {
		User user = user();
		UserCard card = card(user, 0);
		PaymentRecord notPaid = record(user, card, "2026-11-01T00:00:00Z", 700L);
		notPaid.confirmNotPaid();
		PaymentRecord unregistered = record(user, card, "2026-11-02T00:00:00Z", 500L);
		payments.flush();
		jdbc.update("update payment_records set status = 'PAID_UNREGISTERED', confirmed_at = now() where id = ?",
				unregistered.getId());
		em.clear();
		mvc.perform(get("/api/v1/payment-records").param("month", "2026-11")
				.header(HttpHeaders.AUTHORIZATION, auth(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.records.length()").value(2))
				.andExpect(jsonPath("$.records[0].result").value("PAID_UNREGISTERED"))
				.andExpect(jsonPath("$.records[0].userCard").isEmpty())
				.andExpect(jsonPath("$.savedBenefit.amount").value(0))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(0))
				.andExpect(jsonPath("$.byCard.length()").value(0));
		mvc.perform(get("/api/v1/payment-records").param("month", "2026-12")
				.header(HttpHeaders.AUTHORIZATION, auth(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.records.length()").value(0))
				.andExpect(jsonPath("$.savedBenefit.amount").value(0));
		User other = user();
		mvc.perform(get("/api/v1/payment-records").param("userCardId", card.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, auth(other)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	private User user() { return users.saveAndFlush(new User(UUID.randomUUID()+"@kattok.test", "unused", Instant.now())); }
	private String auth(User user) { return "Bearer " + tokens.issue(user.getId()).value(); }
	private UserCard card(User user, int index) { return cards.saveAndFlush(new UserCard(user, products.findAll().get(index))); }
	private PaymentRecord record(User user, UserCard card, String at, Long benefit) {
		var merchant = merchants.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		var rec = recommendations.saveAndFlush(new Recommendation(user, merchant, merchant.getCategory(), 5000L,
				Map.of(), OverallStatus.CONFIRMED, null));
		jdbc.update("update recommendations set created_at = ? where id = ?", Timestamp.from(Instant.parse(at)), rec.getId());
		items.saveAndFlush(new RecommendationItem(rec, card, "추천 당시 상품명", "추천 당시 카드사",
				RecommendationItemStatus.CONFIRMED, true, 1, benefit, benefit, Map.of(), Map.of(), Map.of()));
		return payments.saveAndFlush(new PaymentRecord(user, rec));
	}
}
