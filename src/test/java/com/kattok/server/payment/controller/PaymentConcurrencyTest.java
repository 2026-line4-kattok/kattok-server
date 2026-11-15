package com.kattok.server.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.global.security.TokenProvider;
import tools.jackson.databind.json.JsonMapper;

/** 요청마다 독립 트랜잭션을 사용해야 잠금·커밋 경합을 검증할 수 있어 테스트 외부 트랜잭션을 두지 않는다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
class PaymentConcurrencyTest {
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MockMvc mvc;
	@Autowired private TokenProvider tokens;
	@Autowired private JsonMapper json;

	@Test
	void concurrentResendsApplyOnce() throws Exception {
		Fixture f = fixture();
		try {
			Long rec = recommendation(f);
			concurrently(f, List.of(rec, rec));
			assertThat(jdbc.queryForObject("select remaining_amount from card_cap_balances where user_card_id=?", Long.class, f.card()))
					.isEqualTo(700L);
			assertThat(jdbc.queryForObject("select count(*) from payment_cap_effects where active and payment_record_id in "
					+ "(select id from payment_records where user_id=?)", Long.class, f.user())).isEqualTo(1L);
		} finally { cleanup(f); }
	}

	@Test
	void concurrentDifferentPaymentsPreserveBothDebits() throws Exception {
		Fixture f = fixture();
		try {
			concurrently(f, List.of(recommendation(f), recommendation(f)));
			assertThat(jdbc.queryForObject("select remaining_amount from card_cap_balances where user_card_id=?", Long.class, f.card()))
					.isEqualTo(400L);
			assertThat(jdbc.queryForObject("select sum(saved_benefit) from payment_records where user_id=?", Long.class, f.user()))
					.isEqualTo(600L);
		} finally { cleanup(f); }
	}

	@Test
	void failedCardChangeRollsBackOldRestorationAndKeepsOriginalRecord() throws Exception {
		Fixture f = fixture();
		try {
			Long rec = recommendation(f);
			mvc.perform(put("/api/v1/recommendations/{id}/payment",rec)
					.header(HttpHeaders.AUTHORIZATION,"Bearer "+tokens.issue(f.user()).value())
					.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"PAID_RECOMMENDED\"}"))
					.andExpect(status().isOk());
			Long product = jdbc.queryForObject("select min(id) from card_products where id<>(select product_id from user_cards where id=?)",
					Long.class,f.card());
			Long other = jdbc.queryForObject("insert into user_cards(user_id,product_id) values(?,?) returning id",Long.class,f.user(),product);
			jdbc.update("""
				insert into recommendation_items(recommendation_id,user_card_id,product_name_snapshot,issuer_name_snapshot,status,
				is_recommended,rank_no,expected_benefit_min,expected_benefit_max,applied_rules,unknown_conditions,excluded_reasons)
				select recommendation_id,?,'다른 카드','카드사',status,false,2,300,300,applied_rules,'{}','{}'
				from recommendation_items where recommendation_id=? and is_recommended
				""",other,rec);
			mvc.perform(put("/api/v1/recommendations/{id}/payment",rec)
					.header(HttpHeaders.AUTHORIZATION,"Bearer "+tokens.issue(f.user()).value())
					.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"PAID_OTHER\",\"userCardId\":%s}".formatted(other)))
					.andExpect(status().isBadRequest());
			assertThat(jdbc.queryForObject("select status from payment_records where recommendation_id=?",String.class,rec))
					.isEqualTo("PAID_RECOMMENDED");
			assertThat(jdbc.queryForObject("select remaining_amount from card_cap_balances where user_card_id=?",Long.class,f.card()))
					.isEqualTo(700L);
		} finally { cleanup(f); }
	}

	private void concurrently(Fixture f, List<Long> recs) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = recs.stream().map(rec -> executor.submit(() -> {
				if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
				mvc.perform(put("/api/v1/recommendations/{id}/payment", rec)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(f.user()).value())
						.contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"PAID_RECOMMENDED\"}"))
						.andExpect(status().isOk());
				return true;
			})).toList();
			start.countDown();
			for (var future : futures) assertThat(future.get(25, TimeUnit.SECONDS)).isTrue();
		}
	}

	private Fixture fixture() {
		Long user = jdbc.queryForObject("insert into users(email,password_hash,terms_agreed_at) values(?,'unused',now()) returning id",
				Long.class, UUID.randomUUID()+"@kattok.test");
		Long product = jdbc.queryForObject("select min(id) from card_products", Long.class);
		Long card = jdbc.queryForObject("insert into user_cards(user_id,product_id) values(?,?) returning id", Long.class,user,product);
		Long cap = jdbc.queryForObject("insert into benefit_cap_groups(product_id,cap_period,cap_amount,cap_count,description) "
				+ "values(?,'MONTHLY',1000,10,'동시성 한도') returning id",Long.class,product);
		return new Fixture(user,card,cap);
	}

	private Long recommendation(Fixture f) {
		var merchant = jdbc.queryForMap("select id,category_id from merchants where name='스타벅스' limit 1");
		Long rec = jdbc.queryForObject("""
			insert into recommendations(user_id,merchant_id,merchant_name_snapshot,category_id,amount,input_snapshot,overall_status)
			values(?,?,'스타벅스',?,5000,'{}','CONFIRMED') returning id
			""", Long.class,f.user(),merchant.get("id"),merchant.get("category_id"));
		var rules = Map.of("rules",List.of(Map.of("ruleId",1,"description","혜택","benefitAmount",300,
				"calculation",Map.of("rate",6,"baseAmount",5000,"capApplied",false),"capGroupIds",List.of(f.cap()))),
				"remainingLimitsAfter",List.of(Map.ofEntries(Map.entry("capGroupId",f.cap()),Map.entry("description","한도"),
						Map.entry("capPeriod","MONTHLY"),Map.entry("capAmount",1000),Map.entry("capCount",10),
						Map.entry("remainingLimitBefore",1000),Map.entry("remainingLimitAmount",700),
						Map.entry("remainingCountBefore",10),Map.entry("remainingCount",9))));
		jdbc.update("""
			insert into recommendation_items(recommendation_id,user_card_id,product_name_snapshot,issuer_name_snapshot,status,
			is_recommended,rank_no,expected_benefit_min,expected_benefit_max,applied_rules,unknown_conditions,excluded_reasons)
			values(?,?,'당시 카드','카드사','CONFIRMED',true,1,300,300,?::jsonb,'{}','{}')
			""",rec,f.card(),json.writeValueAsString(rules));
		jdbc.update("insert into payment_records(user_id,recommendation_id,status) values(?,?,'PENDING')",f.user(),rec);
		return rec;
	}

	private void cleanup(Fixture f) {
		jdbc.update("delete from payment_cap_effects where balance_id in(select id from card_cap_balances where user_card_id=?)",f.card());
		jdbc.update("delete from card_cap_balances where user_card_id=?",f.card());
		jdbc.update("delete from payment_records where user_id=?",f.user());
		jdbc.update("delete from recommendation_items where recommendation_id in(select id from recommendations where user_id=?)",f.user());
		jdbc.update("delete from recommendations where user_id=?",f.user());
		jdbc.update("delete from user_card_cap_usages where user_card_id=?",f.card());
		jdbc.update("delete from user_cards where user_id=?",f.user());
		jdbc.update("delete from benefit_cap_groups where id=?",f.cap());
		jdbc.update("delete from users where id=?",f.user());
	}
	private record Fixture(Long user,Long card,Long cap) {}
}
