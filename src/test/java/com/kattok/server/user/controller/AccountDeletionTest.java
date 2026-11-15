package com.kattok.server.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.user.service.UserMutationGuard;
import tools.jackson.databind.json.JsonMapper;

/** 실제 커밋·롤백·경합을 검증하므로 테스트 외부 트랜잭션을 두지 않는다. 테스트컨테이너의 임의 계정만 사용한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
class AccountDeletionTest {
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TokenProvider tokens;
	@Autowired private JsonMapper json;
	@Autowired private org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
	@MockitoSpyBean private UserMutationGuard guard;
	private final List<Long> ownedUsers=new ArrayList<>();
	private final List<Long> testCaps=new ArrayList<>();
	private final List<String> failureTriggers=new ArrayList<>();
	private final List<Runnable> repairs=new ArrayList<>();

	@Test
	void summaryUsesAllPeriodsActiveCardsAndStoredOrLegacyBenefits() throws Exception {
		Fixture f=fixture(); fixture();
		mvc.perform(get("/api/v1/users/me/deletion-summary").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ownedCardCount").value(1))
				.andExpect(jsonPath("$.paymentRecordCount").value(9))
				.andExpect(jsonPath("$.savedBenefitTotal").value(650));
		long empty=user();
		mvc.perform(get("/api/v1/users/me/deletion-summary").header(HttpHeaders.AUTHORIZATION,auth(empty)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ownedCardCount").value(0))
				.andExpect(jsonPath("$.paymentRecordCount").value(0))
				.andExpect(jsonPath("$.savedBenefitTotal").value(0));
	}

	@Test
	void deletesEntireOwnedGraphPreservesOthersAndInvalidatesTokenEverywhere() throws Exception {
		Fixture f=fixture(), other=fixture();
		Map<String,Long> otherBefore=counts(other.user());
		Map<String,Long> sharedBefore=sharedCounts();
		mvc.perform(get("/api/v1/merchants").param("keyword","스타").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk());
		mvc.perform(delete("/api/v1/users/me").param("userId",String.valueOf(other.user()))
				.header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isNoContent());
		assertThat(counts(f.user()).values()).allMatch(count->count==0);
		assertThat(counts(other.user())).isEqualTo(otherBefore);
		assertThat(sharedCounts()).isEqualTo(sharedBefore);
		for(String path:List.of("/api/v1/users/me","/api/v1/users/me/deletion-summary","/api/v1/merchants?keyword=스타",
				"/api/v1/merchant-categories","/api/v1/payment-records/pending")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}
		mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isUnauthorized());
		String body=mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"password1234\",\"termsAgreed\":true}".formatted(f.email())))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long newId=json.readTree(body).get("user").get("id").asLong(); ownedUsers.add(newId);
		assertThat(newId).isNotEqualTo(f.user());
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,"Bearer "+json.readTree(body).get("accessToken").asString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(newId));
		mvc.perform(get("/api/v1/merchants?keyword=스타").header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isUnauthorized());
	}

	@Test
	void demoDeletionIsBlockedAndUnauthenticatedRequestsAreRejected() throws Exception {
		long demo=jdbc.queryForObject("select id from users where email='demo@kattok.app'",Long.class);
		var before=counts(demo);
		mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,auth(demo)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_NOT_DELETABLE"));
		assertThat(counts(demo)).isEqualTo(before);
		mvc.perform(get("/api/v1/users/me/deletion-summary").header(HttpHeaders.AUTHORIZATION,auth(demo))).andExpect(status().isOk());
		mvc.perform(delete("/api/v1/users/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/me/deletion-summary")).andExpect(status().isUnauthorized());
	}

	@Test
	void failureAtFinalUserDeleteRollsBackEveryPriorDeleteIncludingInactiveEffects() throws Exception {
		Fixture f=fixture(); var before=counts(f.user());
		assertThat(jdbc.queryForObject("select count(*) from payment_cap_effects where payment_record_id in "
				+"(select id from payment_records where user_id=?) and not active",Long.class,f.user())).isEqualTo(1);
		String name="reject_account_"+UUID.randomUUID().toString().replace("-","");
		failureTriggers.add(name);
		jdbc.execute("create function "+name+"() returns trigger language plpgsql as $$ begin raise exception 'forced test failure'; end $$");
		jdbc.execute("create trigger "+name+" before delete on users for each row when (old.id="+f.user()+") execute function "+name+"()");
		mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isInternalServerError());
		assertThat(counts(f.user())).isEqualTo(before);
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings={"item","effect"})
	void crossOwnerReferenceCausesRollbackInsteadOfDeletingOtherUserData(String type) throws Exception {
		Fixture f=fixture(), other=fixture();
		if(type.equals("item")) {
			Long id=jdbc.queryForObject("""
				insert into recommendation_items(recommendation_id,user_card_id,product_name_snapshot,issuer_name_snapshot,status,
				is_recommended,rank_no,expected_benefit_min,expected_benefit_max,applied_rules,unknown_conditions,excluded_reasons)
				values(?,?,'잘못된 참조','카드사','CONFIRMED',false,2,0,0,'{}','{}','{}') returning id
				""",Long.class,other.recs().getFirst(),f.activeCard());
			repairs.add(()->jdbc.update("delete from recommendation_items where id=?",id));
		} else {
			Long id=jdbc.queryForObject("""
				insert into payment_cap_effects(payment_record_id,balance_id,amount_delta,count_delta,amount_revision,count_revision)
				values(?,?,5,0,0,0) returning id
				""",Long.class,other.payments().getFirst(),f.balance());
			repairs.add(()->jdbc.update("delete from payment_cap_effects where id=?",id));
		}
		var before=counts(f.user()); var otherBefore=counts(other.user());
		mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isInternalServerError());
		assertThat(counts(f.user())).isEqualTo(before);
		assertThat(counts(other.user())).isEqualTo(otherBefore);
	}

	@Test
	void simultaneousDeletionReturnsOneSuccessAndOneUnauthorized() throws Exception {
		Fixture f=fixture(); CountDownLatch ready=new CountDownLatch(2);
		UserMutationGuard target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(guard);
		doAnswer(invocation->{
			if(invocation.getArgument(0,Long.class).equals(f.user())) {
				ready.countDown(); if(!ready.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("guard timeout");
			}
			return invocation.callRealMethod();
		}).when(target).lock(f.user());
		try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
			var a=executor.submit(()->mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth())).andReturn().getResponse().getStatus());
			var b=executor.submit(()->mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth())).andReturn().getResponse().getStatus());
			assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,401);
		}
		assertThat(counts(f.user()).values()).allMatch(count->count==0);
	}

	@Test
	void registrationThatAlreadyAuthenticatedCannotWriteAfterDeletionWins() throws Exception {
		Fixture f=fixture(); CountDownLatch ready=new CountDownLatch(1), deleted=new CountDownLatch(1);
		UserMutationGuard target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(guard);
		doAnswer(invocation->{
			if(Thread.currentThread().getName().equals("registration-race")) {
				ready.countDown(); if(!deleted.await(15,TimeUnit.SECONDS))throw new IllegalStateException("delete timeout");
			}
			return invocation.callRealMethod();
		}).when(target).lock(f.user());
		long product=jdbc.queryForList("select id from card_products order by id limit 3",Long.class).get(2);
		try(var executor=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("registration-race").factory())) {
			var registration=executor.submit(()->mvc.perform(post("/api/v1/owned-cards")
					.header(HttpHeaders.AUTHORIZATION,f.auth()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":%s}".formatted(product))).andReturn().getResponse().getStatus());
			try {
				assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
				mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,f.auth())).andExpect(status().isNoContent());
			} finally { deleted.countDown(); }
			assertThat(registration.get(20,TimeUnit.SECONDS)).isEqualTo(401);
		}
		assertThat(counts(f.user()).values()).allMatch(count->count==0);
	}

	@ParameterizedTest
	@ValueSource(strings={"missing","not-a-number","0","-1","9223372036854775808","nonexistent"})
	void protectedSharedQueriesRejectInvalidOrMissingAccountSubjects(String subject) throws Exception {
		var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("kattok")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
		if(!subject.equals("missing"))claims.subject(subject.equals("nonexistent")?Long.toString(Long.MAX_VALUE):subject);
		var header=org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
		String token=jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header,claims.build())).getTokenValue();
		mvc.perform(get("/api/v1/merchants?keyword=스타").header(HttpHeaders.AUTHORIZATION,"Bearer "+token))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private long user() {
		long id=jdbc.queryForObject("insert into users(email,password_hash,terms_agreed_at) values(?,'unused',now()) returning id",
				Long.class,UUID.randomUUID()+"@kattok.test"); ownedUsers.add(id); return id;
	}
	private String auth(long id){return "Bearer "+tokens.issue(id).value();}
	private Fixture fixture() {
		long user=user(); String email=jdbc.queryForObject("select email from users where id=?",String.class,user);
		var products=jdbc.queryForList("select id from card_products order by id limit 2",Long.class);
		long active=jdbc.queryForObject("insert into user_cards(user_id,product_id) values(?,?) returning id",Long.class,user,products.get(0));
		long deleted=jdbc.queryForObject("insert into user_cards(user_id,product_id,deleted_at) values(?,?,now()) returning id",Long.class,user,products.get(1));
		List<Long> capIds=new ArrayList<>();
		for(int i=0;i<2;i++) {
			long card=i==0?active:deleted;
			long cap=jdbc.queryForObject("insert into benefit_cap_groups(product_id,cap_period,cap_amount,cap_count,description) "
					+"values(?,'MONTHLY',1000,10,'탈퇴 검증') returning id",Long.class,products.get(i));testCaps.add(cap);capIds.add(cap);
			jdbc.update("insert into user_card_states(user_card_id,period_month,source,confirmed_at,performance_status) values(?,'2026-11-01','USER_CONFIRMED',now(),'UNKNOWN')",card);
			jdbc.update("insert into user_card_cap_usages(user_card_id,cap_group_id,period_month,source,confirmed_at) values(?,?,'2026-11-01','USER_CONFIRMED',now())",card,cap);
			jdbc.update("insert into user_card_benefit_selections(user_card_id,select_group,selected_option,effective_from) values(?,'TEST','ONE','2026-11-01')",card);
		}
		List<Long> recs=new ArrayList<>(), records=new ArrayList<>();
		String[] statuses={"PAID_RECOMMENDED","PAID_OTHER","PAID_OTHER","PAID_RECOMMENDED","PENDING","NOT_PAID","PAID_UNREGISTERED","PAID_RECOMMENDED","PAID_OTHER"};
		Long[] benefits={300L,200L,null,0L,null,0L,0L,null,null};
		long merchant=jdbc.queryForObject("select min(id) from merchants",Long.class);
		long category=jdbc.queryForObject("select category_id from merchants where id=?",Long.class,merchant);
		for(int i=0;i<10;i++) {
			long rec=jdbc.queryForObject("""
				insert into recommendations(user_id,merchant_id,merchant_name_snapshot,category_id,amount,input_snapshot,overall_status,created_at,confirmed_only_top_user_card_id)
				values(?,?,'당시 결제처',?,5000,'{}','CONFIRMED',?::timestamptz,?) returning id
				""",Long.class,user,merchant,category,i%2==0?"2025-01-01T00:00:00Z":"2026-11-01T00:00:00Z",i==9?deleted:null);recs.add(rec);
			long expected=i==7?150:i==0?300:i==3?0:500;
			item(rec,active,true,"CONFIRMED",expected);
			if(i==1)item(rec,deleted,false,"CONFIRMED",200);
			if(i==8)item(rec,deleted,false,"NOT_CALCULABLE",999);
			if(i==9)continue; // 대기 기록조차 없는 draft 추천도 삭제되어야 하지만 표시 기록 수에는 포함하지 않는다.
			Long actual=statuses[i].equals("PAID_RECOMMENDED")&&i<7?Long.valueOf(active):statuses[i].equals("PAID_OTHER")?Long.valueOf(deleted):null;
			long payment=jdbc.queryForObject("""
				insert into payment_records(user_id,recommendation_id,status,actual_user_card_id,confirmed_at,saved_benefit,actual_card_name_snapshot,confirmation_version)
				values(?,?,?,?,case when ?='PENDING' then null else now() end,?,'실제 카드',?) returning id
				""",Long.class,user,rec,statuses[i],actual,statuses[i],benefits[i],i<7&&!statuses[i].equals("PENDING")?1:0);records.add(payment);
		}
		long balance=jdbc.queryForObject("""
			insert into card_cap_balances(user_card_id,cap_group_id,cap_period,period_start,description,amount_limited,count_limited,
			remaining_amount,remaining_count,amount_source,count_source,assumed_amount,assumed_count)
			values(?,?,'MONTHLY','2026-11-01','탈퇴 검증',true,true,700,9,'ESTIMATED','ESTIMATED',1000,10) returning id
			""",Long.class,active,capIds.get(0));
		jdbc.update("insert into payment_cap_effects(payment_record_id,balance_id,amount_delta,count_delta,amount_revision,count_revision) values(?,?,300,1,0,0)",records.get(0),balance);
		jdbc.update("insert into payment_cap_effects(payment_record_id,balance_id,amount_delta,count_delta,amount_revision,count_revision,active) values(?,?,150,1,0,0,false)",records.get(7),balance);
		long deletedBalance=jdbc.queryForObject("insert into card_cap_balances(user_card_id,cap_group_id,cap_period,period_start,description,amount_limited,count_limited) "
				+"values(?,?,'YEARLY','2026-01-01','삭제 카드 검증',true,true) returning id",Long.class,deleted,capIds.get(1));
		jdbc.update("insert into payment_cap_effects(payment_record_id,balance_id,amount_delta,count_delta,amount_revision,count_revision) values(?,?,100,1,0,0)",records.get(1),deletedBalance);
		return new Fixture(user,email,active,deleted,balance,recs,records,auth(user));
	}
	private void item(long rec,long card,boolean recommended,String status,long benefit){
		jdbc.update("""
			insert into recommendation_items(recommendation_id,user_card_id,product_name_snapshot,issuer_name_snapshot,status,
			is_recommended,rank_no,expected_benefit_min,expected_benefit_max,applied_rules,unknown_conditions,excluded_reasons)
			values(?,?,'당시 카드','카드사',?,?,?, ?,?,'{}','{}','{}')
			""",rec,card,status,recommended,recommended?1:2,benefit,benefit);
	}
	private Map<String,Long> counts(long user){
		Map<String,Long> out=new LinkedHashMap<>();
		out.put("users",jdbc.queryForObject("select count(*) from users where id=?",Long.class,user));
		for(String table:List.of("user_cards","recommendations","payment_records"))
			out.put(table,jdbc.queryForObject("select count(*) from "+table+" where user_id=?",Long.class,user));
		out.put("items",jdbc.queryForObject("select count(*) from recommendation_items where recommendation_id in(select id from recommendations where user_id=?)",Long.class,user));
		out.put("effects",jdbc.queryForObject("select count(*) from payment_cap_effects where payment_record_id in(select id from payment_records where user_id=?)",Long.class,user));
		for(String table:List.of("card_cap_balances","user_card_states","user_card_cap_usages","user_card_benefit_selections"))
			out.put(table,jdbc.queryForObject("select count(*) from "+table+" where user_card_id in(select id from user_cards where user_id=?)",Long.class,user));
		return out;
	}
	private Map<String,Long> sharedCounts(){
		Map<String,Long> out=new LinkedHashMap<>();
		for(String table:List.of("card_issuers","card_products","card_performance_tiers","merchant_categories","merchants","merchant_aliases","benefit_cap_groups","benefit_rules"))
			out.put(table,jdbc.queryForObject("select count(*) from "+table,Long.class));
		return out;
	}
	@AfterEach
	void cleanup(){
		for(String name:failureTriggers){jdbc.execute("drop trigger if exists "+name+" on users");jdbc.execute("drop function if exists "+name+"()");}
		repairs.forEach(Runnable::run);
		for(long user:ownedUsers){
			jdbc.update("delete from payment_cap_effects where payment_record_id in(select id from payment_records where user_id=?)",user);
			jdbc.update("delete from payment_records where user_id=?",user);
			jdbc.update("delete from recommendation_items where recommendation_id in(select id from recommendations where user_id=?)",user);
			jdbc.update("delete from recommendations where user_id=?",user);
			for(String table:List.of("card_cap_balances","user_card_states","user_card_cap_usages","user_card_benefit_selections"))
				jdbc.update("delete from "+table+" where user_card_id in(select id from user_cards where user_id=?)",user);
			jdbc.update("delete from user_cards where user_id=?",user);jdbc.update("delete from users where id=?",user);
		}
		for(long cap:testCaps)jdbc.update("delete from benefit_cap_groups where id=?",cap);
	}
	private record Fixture(long user,String email,long activeCard,long deletedCard,long balance,List<Long> recs,List<Long> payments,String auth){}
}
