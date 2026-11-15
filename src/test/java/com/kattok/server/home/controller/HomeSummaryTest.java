package com.kattok.server.home.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import com.kattok.server.payment.service.PaymentQueryService;
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
class HomeSummaryTest {
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
	@MockitoBean(name = "homeClock") private Clock clock;
	@MockitoSpyBean private PaymentQueryService queries;

	@BeforeEach
	void kstMonthBoundary() {
		// UTC는 10월, KST는 11월. 호스트 시간대와 실제 실행 날짜에 의존하지 않는다.
		when(clock.instant()).thenReturn(Instant.parse("2026-10-31T15:00:00Z"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/home")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void emptyHomeReturnsNullAndZerosWithoutRegisteringCards() throws Exception {
		User user = user();
		mvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION,auth(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingPayment").isEmpty())
				.andExpect(jsonPath("$.pendingCount").value(0))
				.andExpect(jsonPath("$.savedBenefit.month").value("2026-11"))
				.andExpect(jsonPath("$.savedBenefit.amount").value(0))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(0));
	}

	@Test
	void matchesPaymentApisAtKstBoundaryAndKeepsPastPending() throws Exception {
		User user = user(); UserCard card = card(user,0); UserCard otherCard = card(user,1);
		record(user,card,"2026-10-31T14:59:59Z",900).confirmPaidWithRecommended();
		record(user,card,"2026-10-31T15:00:00Z",250).confirmPaidWithRecommended();
		record(user,card,"2026-10-31T15:00:00Z",0).confirmPaidWithRecommended();
		record(user,card,"2026-10-31T15:00:00Z",500).confirmPaidWithOther(otherCard);
		record(user,card,"2026-10-31T15:00:00Z",500).confirmNotPaid();
		PaymentRecord unregistered = record(user,card,"2026-10-31T15:00:00Z",500);
		PaymentRecord older = record(user,card,"2026-09-01T00:00:00Z",500);
		PaymentRecord newer = record(user,card,"2026-10-31T14:00:00Z",500);
		User foreign = user(); UserCard foreignCard = card(foreign,0);
		record(foreign,foreignCard,"2026-10-31T15:00:00Z",999).confirmPaidWithRecommended();
		record(foreign,foreignCard,"2026-10-31T15:00:00Z",999);
		payments.flush();
		jdbc.update("update payment_records set status='PAID_UNREGISTERED',confirmed_at=now() where id=?",unregistered.getId());
		card.delete(); cards.flush(); em.clear();
		mvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION,auth(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pendingPayment.recommendationId").value(newer.getRecommendation().getId()))
				.andExpect(jsonPath("$.pendingPayment.result").value("PENDING"))
				.andExpect(jsonPath("$.pendingPayment.recommendedCard.deleted").value(true))
				.andExpect(jsonPath("$.pendingPayment.recommendedCard.productName").value("당시 카드"))
				.andExpect(jsonPath("$.pendingCount").value(2))
				.andExpect(jsonPath("$.savedBenefit.month").value("2026-11"))
				.andExpect(jsonPath("$.savedBenefit.amount").value(250))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(2));
		mvc.perform(get("/api/v1/payment-records/pending").header(HttpHeaders.AUTHORIZATION,auth(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].recommendationId").value(newer.getRecommendation().getId()))
				.andExpect(jsonPath("$[1].recommendationId").value(older.getRecommendation().getId()));
		mvc.perform(get("/api/v1/payment-records").param("month","2026-11").header(HttpHeaders.AUTHORIZATION,auth(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(2))
				.andExpect(jsonPath("$.savedBenefit.amount").value(250))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(2));
	}

	@Test
	void confirmationRefreshesNextPendingAndCurrentMonthTotal() throws Exception {
		User user = user(); UserCard card = card(user,0);
		PaymentRecord older = record(user,card,"2026-10-31T15:00:00Z",250);
		PaymentRecord newer = record(user,card,"2026-10-31T15:00:00Z",500);
		em.flush(); em.clear();
		mvc.perform(put("/api/v1/recommendations/{id}/payment",newer.getRecommendation().getId())
				.header(HttpHeaders.AUTHORIZATION,auth(user)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"result\":\"PAID_RECOMMENDED\"}")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION,auth(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.pendingCount").value(1))
				.andExpect(jsonPath("$.pendingPayment.recommendationId").value(older.getRecommendation().getId()))
				.andExpect(jsonPath("$.savedBenefit.amount").value(500))
				.andExpect(jsonPath("$.savedBenefit.paymentCount").value(1));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentConfirmationDoesNotMixPendingCardAndCount() throws Exception {
		User user = user(); UserCard card = card(user,0);
		PaymentRecord pending = record(user,card,"2026-10-31T15:00:00Z",500);
		Long recId = pending.getRecommendation().getId();
		try {
			doAnswer(invocation -> {
				Object records = invocation.callRealMethod();
				// 별도 연결에서 커밋하여 대기 목록과 합계 조회 사이에 확인 완료가 발생하도록 한다.
				try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
					executor.submit(() -> jdbc.update("""
						update payment_records set status='PAID_RECOMMENDED',confirmed_at=now(),
						saved_benefit=500,actual_user_card_id=?,actual_card_name_snapshot='당시 카드',confirmation_version=1 where id=?
						""",card.getId(),pending.getId())).get(10,java.util.concurrent.TimeUnit.SECONDS);
				}
				return records;
			}).when(queries).pending();
			mvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION,auth(user)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.pendingPayment.recommendationId").value(recId))
					.andExpect(jsonPath("$.pendingCount").value(1))
					.andExpect(jsonPath("$.savedBenefit.amount").value(0));
			assertThat(jdbc.queryForObject("select status from payment_records where id=?",String.class,pending.getId()))
					.isEqualTo("PAID_RECOMMENDED");
		} finally {
			jdbc.update("delete from payment_records where id=?",pending.getId());
			jdbc.update("delete from recommendation_items where recommendation_id=?",recId);
			jdbc.update("delete from recommendations where id=?",recId);
			jdbc.update("delete from user_cards where id=?",card.getId());
			jdbc.update("delete from users where id=?",user.getId());
		}
	}

	private User user() { return users.saveAndFlush(new User(UUID.randomUUID()+"@kattok.test","unused",Instant.now())); }
	private String auth(User user) { return "Bearer "+tokens.issue(user.getId()).value(); }
	private UserCard card(User user,int index) { return cards.saveAndFlush(new UserCard(user,products.findAll().get(index))); }
	private PaymentRecord record(User user,UserCard card,String at,long benefit) {
		var merchant = merchants.findByNameContainingIgnoreCaseOrderByName("스타벅스").getFirst();
		var rec = recommendations.saveAndFlush(new Recommendation(user,merchant,merchant.getCategory(),5000L,
				Map.of(),OverallStatus.CONFIRMED,null));
		items.saveAndFlush(new RecommendationItem(rec,card,"당시 카드","당시 카드사",RecommendationItemStatus.CONFIRMED,
				true,1,benefit,benefit,Map.of("rules",List.of(Map.of("ruleId",1,"description","혜택","benefitAmount",benefit,
						"calculation",Map.of("rate",10,"baseAmount",5000,"capApplied",false),"capGroupIds",List.of())),
						"remainingLimitsAfter",List.of(),"uncappedCapGroupIds",List.of()),Map.of(),Map.of()));
		jdbc.update("update recommendations set created_at=? where id=?",Timestamp.from(Instant.parse(at)),rec.getId());
		return payments.saveAndFlush(new PaymentRecord(user,rec));
	}
}
