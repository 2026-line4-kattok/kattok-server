package com.kattok.server.merchant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.global.security.TokenProvider;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantChannel;
import com.kattok.server.merchant.repository.MerchantCategoryRepository;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.payment.entity.PaymentRecord;
import com.kattok.server.payment.entity.PaymentRecordStatus;
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
class RecentPaidMerchantsTest {
	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private UserCardRepository cards;
	@Autowired private CardProductRepository products;
	@Autowired private MerchantRepository merchants;
	@Autowired private MerchantCategoryRepository categories;
	@Autowired private RecommendationRepository recommendations;
	@Autowired private RecommendationItemRepository items;
	@Autowired private PaymentRecordRepository payments;
	@Autowired private TokenProvider tokens;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager em;

	@Test
	void includesOnlyOwnPaidMerchantRecordsAndUsesRecommendationTime() throws Exception {
		Fixture f = fixture();
		Merchant a=merchant("A"), b=merchant("B"), c=merchant("C");
		record(f,a,"2026-11-01T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		record(f,b,"2026-11-02T00:00:00Z",PaymentRecordStatus.PAID_OTHER);
		record(f,c,"2026-11-03T00:00:00Z",PaymentRecordStatus.PAID_UNREGISTERED);
		record(f,merchant("pending"),"2026-11-06T00:00:00Z",PaymentRecordStatus.PENDING);
		record(f,merchant("notPaid"),"2026-11-07T00:00:00Z",PaymentRecordStatus.NOT_PAID);
		record(fixture(),merchant("foreign"),"2026-11-09T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		f.card().delete(); cards.flush(); // 삭제된 카드의 과거 결제도 최근 결제처에 남긴다.
		flush();
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(c.getId())).andExpect(jsonPath("$[1].id").value(b.getId()))
				.andExpect(jsonPath("$[2].id").value(a.getId()))
				.andExpect(jsonPath("$[0].lastRecommendedAt").value("2026-11-03T09:00:00+09:00"));
	}

	@Test
	void includesCategoryOnlyPaymentsGroupedByCategoryAlongsideMerchants() throws Exception {
		Fixture f=fixture(); Merchant a=merchant("A");
		var category=categories.findAll().getFirst();
		record(f,a,"2026-11-01T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		record(f,null,"2026-11-02T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		record(f,null,"2026-11-03T00:00:00Z",PaymentRecordStatus.PAID_OTHER);
		record(f,null,"2026-11-05T00:00:00Z",PaymentRecordStatus.NOT_PAID);
		flush();
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].type").value("CATEGORY"))
				.andExpect(jsonPath("$[0].id").doesNotExist())
				.andExpect(jsonPath("$[0].category.id").value(category.getId()))
				.andExpect(jsonPath("$[0].name").isNotEmpty())
				.andExpect(jsonPath("$[0].lastRecommendedAt").value("2026-11-03T09:00:00+09:00"))
				.andExpect(jsonPath("$[1].type").value("MERCHANT"))
				.andExpect(jsonPath("$[1].id").value(a.getId()));
	}

	@Test
	void aggregatesBeforeLimitEvenWhenLatestFiftyRecordsShareOneMerchant() throws Exception {
		Fixture f=fixture(); Merchant a=merchant("A"), b=merchant("B"), c=merchant("C"), d=merchant("D");
		record(f,b,"2026-11-09T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		record(f,c,"2026-11-08T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		record(f,d,"2026-11-07T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		for(int i=0;i<60;i++) record(f,a,"2026-11-10T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		flush();
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].id").value(a.getId())).andExpect(jsonPath("$[1].id").value(b.getId()))
				.andExpect(jsonPath("$[2].id").value(c.getId()));
	}

	@Test
	void sameRecommendationTimeOrdersByMerchantIdAndSerializesKstDateBoundary() throws Exception {
		Fixture f=fixture(); Merchant first=merchant("Z"), second=merchant("A");
		record(f,first,"2026-10-31T15:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		record(f,second,"2026-10-31T15:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		flush();
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(first.getId())).andExpect(jsonPath("$[1].id").value(second.getId()))
				.andExpect(jsonPath("$[0].lastRecommendedAt").value("2026-11-01T00:00:00+09:00"));
	}

	@Test
	void realPaymentCorrectionFallsBackThenRemovesMerchantWithoutReorderingOnCardChange() throws Exception {
		Fixture f=fixture(); Merchant a=merchant("A"), b=merchant("B");
		Long oldA=record(f,a,"2026-11-01T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		Long recB=record(f,b,"2026-11-02T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		Long latestA=record(f,a,"2026-11-03T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		change(f,latestA,"{\"result\":\"NOT_PAID\"}");
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(b.getId()))
				.andExpect(jsonPath("$[1].id").value(a.getId()))
				.andExpect(jsonPath("$[1].lastRecommendedAt").value("2026-11-01T09:00:00+09:00"));
		change(f,recB,"{\"result\":\"PAID_OTHER\",\"userCardId\":%s}".formatted(f.otherCard().getId()));
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(b.getId()))
				.andExpect(jsonPath("$[0].lastRecommendedAt").value("2026-11-02T09:00:00+09:00"));
		change(f,oldA,"{\"result\":\"NOT_PAID\"}");
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		change(f,recB,"{\"result\":\"NOT_PAID\"}");
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void returnsCurrentMerchantMetadataAndProtectsAuthentication() throws Exception {
		Fixture f=fixture(); Merchant merchant=merchant("original");
		record(f,merchant,"2026-11-01T00:00:00Z",PaymentRecordStatus.PAID_RECOMMENDED);
		var category=categories.findAll().stream().filter(c->!c.getId().equals(merchant.getCategory().getId())).findFirst().orElseThrow();
		jdbc.update("update merchants set name='현재 이름',category_id=? where id=?",category.getId(),merchant.getId()); flush();
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,f.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("현재 이름"))
				.andExpect(jsonPath("$[0].channel").value("OFFLINE"))
				.andExpect(jsonPath("$[0].category.id").value(category.getId()));
		mvc.perform(get("/api/v1/merchants/recent")).andExpect(status().isUnauthorized());
		Fixture empty=fixture();
		mvc.perform(get("/api/v1/merchants/recent").header(HttpHeaders.AUTHORIZATION,empty.auth()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	private void change(Fixture f,Long rec,String body) throws Exception {
		flush();
		mvc.perform(put("/api/v1/recommendations/{id}/payment",rec).header(HttpHeaders.AUTHORIZATION,f.auth())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		flush();
	}
	private void flush(){em.flush();em.clear();}
	private Fixture fixture(){
		User user=users.saveAndFlush(new User(UUID.randomUUID()+"@kattok.test","unused",Instant.now()));
		var all=products.findAll();
		UserCard card=cards.saveAndFlush(new UserCard(user,all.get(0)));
		UserCard other=cards.saveAndFlush(new UserCard(user,all.get(1)));
		return new Fixture(user,card,other,"Bearer "+tokens.issue(user.getId()).value());
	}
	private Merchant merchant(String label){return merchants.saveAndFlush(new Merchant(label+UUID.randomUUID(),MerchantChannel.OFFLINE,categories.findAll().getFirst()));}
	private Long record(Fixture f,Merchant merchant,String at,PaymentRecordStatus status){
		var category=merchant==null?categories.findAll().getFirst():merchant.getCategory();
		var rec=recommendations.saveAndFlush(new Recommendation(f.user(),merchant,category,5000L,Map.of(),OverallStatus.CONFIRMED,null));
		rec.confirm();
		items.saveAndFlush(new RecommendationItem(rec,f.card(),"당시 카드","당시 카드사",RecommendationItemStatus.CONFIRMED,true,1,500L,500L,
				Map.of("rules",List.of(Map.of("ruleId",1,"description","혜택","benefitAmount",500,"calculation",Map.of("rate",10,"baseAmount",5000,"capApplied",false),
						"capGroupIds",List.of())),"remainingLimitsAfter",List.of(),"uncappedCapGroupIds",List.of()),Map.of(),Map.of()));
		PaymentRecord payment=new PaymentRecord(f.user(),rec);
		if(status!=PaymentRecordStatus.PENDING) payment.applyConfirmation(status,
				status==PaymentRecordStatus.PAID_RECOMMENDED?f.card():status==PaymentRecordStatus.PAID_OTHER?f.otherCard():null,
				"당시 카드",status==PaymentRecordStatus.PAID_OTHER?null:Long.valueOf(status==PaymentRecordStatus.PAID_RECOMMENDED?500:0),Instant.now());
		payments.saveAndFlush(payment);
		jdbc.update("update recommendations set created_at=? where id=?",Timestamp.from(Instant.parse(at)),rec.getId());
		return rec.getId();
	}
	private record Fixture(User user,UserCard card,UserCard otherCard,String auth){}
}
