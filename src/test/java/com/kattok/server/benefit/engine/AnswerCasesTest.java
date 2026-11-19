package com.kattok.server.benefit.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.benefit.engine.BenefitEngine.CardInput;
import com.kattok.server.benefit.engine.CardBenefitResult.Assumption;
import com.kattok.server.benefit.engine.CardBenefitResult.LimitSource;
import com.kattok.server.benefit.engine.CardBenefitResult.Status;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.RecommendationResult.OverallStatus;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.FakeUserCardStateReader;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

/**
 * 「카똑 카드 데이터·계산 기준 (BE 전달용, 11/9)」 4절 정답 케이스 10개. 모두 통과하면 계산 엔진 P0 완료다.
 *
 * <p>카드 상품과 혜택 규칙은 Flyway 시드를 그대로 읽고, 사용자 카드 상태는 가짜 구현으로 넣는다.
 * 문서의 "한도·횟수는 이번 달 첫 사용 기준"은 모든 한도 그룹의 남은 한도·횟수를 전체 값으로 확인한 상태로 넣었다(9번 제외).
 * 시드의 규칙 유효 시작일이 적재한 날이라, 결제일은 오늘 이후의 같은 요일로 잡는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AnswerCasesTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final long USER_ID = 1L;

	private static final String MR_LIFE = "CR001";
	private static final String ID_SELECT_ALL = "CR003";
	private static final String GOOD_DAY = "CR004";
	private static final String MOVING = "CR005";
	private static final String ALPHABET_S = "CR006";

	@Autowired
	private ProductRulesReader productRulesReader;

	@Autowired
	private JdbcTemplate jdbc;

	private final BenefitEngine engine = new BenefitEngine();

	@Test
	@DisplayName("1. 굿데이 60만원 구간, 스타벅스 1만원: 커피전문점 업종 10%로 1,000원 확정")
	void case1() {
		CardBenefitResult result = single(GOOD_DAY, tier(GOOD_DAY, 2), starbucks(10000, wednesday(14)));

		assertThat(result.expectedBenefit()).isEqualTo(1000);
		assertThat(result.status()).isEqualTo(Status.CONFIRMED);
	}

	@Test
	@DisplayName("2. 굿데이 30만원 구간, 스타벅스 1만원: 커피 혜택은 60만원 구간부터라 0원")
	void case2() {
		CardBenefitResult result = single(GOOD_DAY, tier(GOOD_DAY, 1), starbucks(10000, wednesday(14)));

		assertThat(result.expectedBenefit()).isZero();
		assertThat(result.status()).isEqualTo(Status.NO_BENEFIT);
	}

	@Test
	@DisplayName("3. 알파벳카드S 실적 미확인, 스타벅스 1만원: 가장 낮은 40만원 구간으로 가정해 500원 조건부")
	void case3() {
		CardBenefitResult result = single(ALPHABET_S, PerformanceState.unknown(), starbucks(10000, wednesday(14)));

		assertThat(result.expectedBenefit()).isEqualTo(500);
		assertThat(result.status()).isEqualTo(Status.CONDITIONAL);
		assertThat(result.assumptions()).containsExactly(Assumption.PERFORMANCE_ASSUMED_LOWEST_TIER);
	}

	@Test
	@DisplayName("4. 알파벳카드S 40만원 구간, 업종 칩 '카페' 1만원: 브랜드 한정 혜택은 빠져 0원")
	void case4() {
		CardBenefitResult result = single(ALPHABET_S, tier(ALPHABET_S, 1),
				PaymentContext.inCategory(categoryId("CAFE"), 10000, wednesday(14)));

		assertThat(result.expectedBenefit()).isZero();
		assertThat(result.status()).isEqualTo(Status.NO_BENEFIT);
	}

	@Test
	@DisplayName("회귀(V31): 알파벳카드S 40만원 구간, 쿠팡 5만원: 할부 조건 검증 불가해 빼서 0원")
	void regressionAlphabetSCoupangUnverified() {
		CardBenefitResult result = single(ALPHABET_S, tier(ALPHABET_S, 1),
				PaymentContext.atMerchant(merchantId("쿠팡"), categoryId("ONLINE_SHOPPING"), 50000, wednesday(14)));

		assertThat(result.expectedBenefit()).isZero();
		assertThat(result.status()).isEqualTo(Status.NO_BENEFIT);
	}

	@Test
	@DisplayName("회귀(V33): 스타플러스 체크 스타벅스·커피빈 10%는 스타벅스 1만원에 1,000원, 메가MGC커피·업종 칩 '카페'는 0원")
	void regressionBrandLimitedCafeBenefit() {
		String starPlus = "CK057";
		CardBenefitResult atStarbucks = single(starPlus, PerformanceState.unknown(), starbucks(10000, wednesday(14)));
		CardBenefitResult atMegaCoffee = single(starPlus, PerformanceState.unknown(),
				PaymentContext.atMerchant(merchantId("메가MGC커피"), categoryId("CAFE"), 10000, wednesday(14)));
		CardBenefitResult byCafeChip = single(starPlus, PerformanceState.unknown(),
				PaymentContext.inCategory(categoryId("CAFE"), 10000, wednesday(14)));

		assertThat(atStarbucks.expectedBenefit()).isEqualTo(1000);
		assertThat(atMegaCoffee.expectedBenefit()).isZero();
		assertThat(byCafeChip.expectedBenefit()).isZero();
	}

	@Test
	@DisplayName("회귀(V35): BC 바로 KaPick 15만원 구간, 메가MGC커피 1만원은 먹픽 7%로 700원, 대상 밖인 투썸플레이스는 0원")
	void regressionKaPickCoffee() {
		String kaPick = "CT04";
		CardBenefitResult atMegaCoffee = single(kaPick, tier(kaPick, 1),
				PaymentContext.atMerchant(merchantId("메가MGC커피"), categoryId("CAFE"), 10000, wednesday(14)));
		CardBenefitResult atTwosome = single(kaPick, tier(kaPick, 1),
				PaymentContext.atMerchant(merchantId("투썸플레이스"), categoryId("CAFE"), 10000, wednesday(14)));

		assertThat(atMegaCoffee.expectedBenefit()).isEqualTo(700);
		assertThat(atMegaCoffee.status()).isEqualTo(Status.CONFIRMED);
		assertThat(atTwosome.expectedBenefit()).isZero();
	}

	@Test
	@DisplayName("5. Mr.Life 50만원 구간, GS25 2만원: 1회 1만원까지만 할인해 1,000원 확정")
	void case5() {
		// GS25 결제처 데이터가 아직 없어 같은 업종(편의점)으로 계산한다. Mr.Life 편의점 혜택은 업종 전체 대상이라 결과가 같다.
		CardBenefitResult result = single(MR_LIFE, tier(MR_LIFE, 2),
				PaymentContext.inCategory(categoryId("CONVENIENCE_STORE"), 20000, wednesday(14)));

		assertThat(result.expectedBenefit()).isEqualTo(1000);
		assertThat(result.status()).isEqualTo(Status.CONFIRMED);
	}

	@Test
	@DisplayName("6. Mr.Life 30만원 구간, 업종 칩 '음식점' 1만5천원: 14시 0원, 22시 1,000원 (시간대 조건)")
	void case6() {
		long restaurant = categoryId("RESTAURANT");

		CardBenefitResult afternoon = single(MR_LIFE, tier(MR_LIFE, 1),
				PaymentContext.inCategory(restaurant, 15000, wednesday(14)));
		CardBenefitResult night = single(MR_LIFE, tier(MR_LIFE, 1),
				PaymentContext.inCategory(restaurant, 15000, wednesday(22)));

		assertThat(afternoon.expectedBenefit()).isZero();
		assertThat(night.expectedBenefit()).isEqualTo(1000);
		assertThat(night.status()).isEqualTo(Status.CONFIRMED);
	}

	@Test
	@DisplayName("7. Mr.Life 100만원 구간, 이마트 6만원 토요일: 주말 조건 + 1회 5만원까지라 5,000원 확정")
	void case7() {
		// 이마트 결제처 데이터가 아직 없어 같은 업종(마트)으로 계산한다. Mr.Life 마트 혜택은 업종 전체 대상이라 결과가 같다.
		LocalDateTime saturday = upcoming(DayOfWeek.SATURDAY).atTime(11, 0);
		CardBenefitResult result = single(MR_LIFE, tier(MR_LIFE, 3),
				PaymentContext.inCategory(categoryId("MART"), 60000, saturday));

		assertThat(result.expectedBenefit()).isEqualTo(5000);
		assertThat(result.status()).isEqualTo(Status.CONFIRMED);
	}

	@Test
	@DisplayName("8. 삼성 iD SELECT ALL, 업종 칩 '택시'·'편의점' 2만원: 택시는 제외 업종이라 0원, 편의점 0.7%로 140원")
	void case8() {
		CardBenefitResult taxi = single(ID_SELECT_ALL, PerformanceState.unknown(),
				PaymentContext.inCategory(categoryId("TAXI"), 20000, wednesday(14)));
		CardBenefitResult convenience = single(ID_SELECT_ALL, PerformanceState.unknown(),
				PaymentContext.inCategory(categoryId("CONVENIENCE_STORE"), 20000, wednesday(14)));

		assertThat(taxi.expectedBenefit()).isZero();
		assertThat(convenience.expectedBenefit()).isEqualTo(140);
		assertThat(convenience.status()).isEqualTo(Status.CONFIRMED);
	}

	@Test
	@DisplayName("9. 알파벳카드S 40만원 구간, 커피 남은 한도 300원(추정), 스타벅스 1만원: 300원 추정")
	void case9() {
		PaymentContext payment = starbucks(10000, wednesday(14));
		ProductRules product = product(ALPHABET_S);
		long tier1 = tierId(ALPHABET_S, 1);
		Rule starbucksRule = product.rules().stream()
				.filter(rule -> payment.merchantId().equals(rule.merchantId()) && Long.valueOf(tier1).equals(rule.requiredTierId()))
				.findFirst()
				.orElseThrow();
		Map<Long, CapState> caps = firstUseCaps(product);
		caps.put(starbucksRule.capGroupId(), new CapState(300L, null, ConfirmationSource.SYSTEM_ESTIMATED));

		CardBenefitResult result = recommend(List.of(new CardSetup(ALPHABET_S, PerformanceState.confirmed(tier1), caps)),
				payment).cards().getFirst();

		assertThat(result.expectedBenefit()).isEqualTo(300);
		assertThat(result.limitSource()).isEqualTo(LimitSource.ESTIMATED);
	}

	@Test
	@DisplayName("10. MOVING + 굿데이(60만원), 쿠팡 5만원: MOVING 온라인 2%로 1,000원 확정 추천, 차이 1,000원")
	void case10() {
		RecommendationResult result = recommend(List.of(
				new CardSetup(MOVING, PerformanceState.unknown(), null),
				new CardSetup(GOOD_DAY, tier(GOOD_DAY, 2), null)),
				PaymentContext.atMerchant(merchantId("쿠팡"), categoryId("ONLINE_SHOPPING"), 50000, wednesday(14)));

		CardBenefitResult moving = result.cards().getFirst();
		assertThat(result.recommendedUserCardId()).isEqualTo(moving.userCardId());
		assertThat(moving.expectedBenefit()).isEqualTo(1000);
		assertThat(moving.status()).isEqualTo(Status.CONFIRMED);
		assertThat(result.overallStatus()).isEqualTo(OverallStatus.CONFIRMED);
		assertThat(result.differenceFromSecond()).isEqualTo(1000);
		assertThat(result.cards().get(1).expectedBenefit()).isZero();
	}

	private CardBenefitResult single(String productKey, PerformanceState performance, PaymentContext payment) {
		return recommend(List.of(new CardSetup(productKey, performance, null)), payment).cards().getFirst();
	}

	/** 카드들을 가짜 상태 조회에 등록하고, 실제 서비스와 같은 순서로 상태와 상품 정보를 읽어 계산한다. */
	private RecommendationResult recommend(List<CardSetup> setups, PaymentContext payment) {
		FakeUserCardStateReader stateReader = new FakeUserCardStateReader();
		for (int i = 0; i < setups.size(); i++) {
			CardSetup setup = setups.get(i);
			long productId = productId(setup.productKey());
			Map<Long, CapState> caps = setup.caps() == null ? firstUseCaps(product(setup.productKey())) : setup.caps();
			stateReader.add(USER_ID, new UserCardSnapshot((long) i + 1, productId, i + 1, setup.performance(), caps));
		}

		List<UserCardSnapshot> snapshots = stateReader.findForRecommendation(USER_ID, YearMonth.from(payment.paidAt()));
		Map<Long, ProductRules> products = productRulesReader.read(
				snapshots.stream().map(UserCardSnapshot::productId).toList(), payment.paidAt().toLocalDate());
		List<CardInput> inputs = snapshots.stream()
				.map(snapshot -> new CardInput(snapshot, products.get(snapshot.productId())))
				.toList();
		return engine.recommend(inputs, payment);
	}

	/** 이번 달 첫 사용: 모든 한도 그룹의 남은 한도·횟수가 전체 값이다. */
	private static Map<Long, CapState> firstUseCaps(ProductRules product) {
		Map<Long, CapState> caps = new HashMap<>();
		product.caps().values().forEach(cap -> caps.put(cap.id(),
				new CapState(cap.capAmount(), cap.capCount(), ConfirmationSource.USER_CONFIRMED)));
		return caps;
	}

	private ProductRules product(String productKey) {
		long productId = productId(productKey);
		return productRulesReader.read(List.of(productId), LocalDate.now(KST).plusDays(1)).get(productId);
	}

	private PaymentContext starbucks(long amount, LocalDateTime paidAt) {
		return PaymentContext.atMerchant(merchantId("스타벅스"), categoryId("CAFE"), amount, paidAt);
	}

	private PerformanceState tier(String productKey, int level) {
		return PerformanceState.confirmed(tierId(productKey, level));
	}

	private long productId(String productKey) {
		return jdbc.queryForObject("select id from card_products where product_key = ?", Long.class, productKey);
	}

	private long tierId(String productKey, int level) {
		return jdbc.queryForObject("""
				select t.id from card_performance_tiers t join card_products p on p.id = t.product_id
				where p.product_key = ? and t.level = ?
				""", Long.class, productKey, level);
	}

	private long categoryId(String code) {
		return jdbc.queryForObject("select id from merchant_categories where code = ?", Long.class, code);
	}

	private long merchantId(String name) {
		return jdbc.queryForObject("select id from merchants where name = ?", Long.class, name);
	}

	private static LocalDateTime wednesday(int hour) {
		return upcoming(DayOfWeek.WEDNESDAY).atTime(LocalTime.of(hour, 0));
	}

	private static LocalDate upcoming(DayOfWeek day) {
		return LocalDate.now(KST).with(TemporalAdjusters.next(day));
	}

	private record CardSetup(String productKey, PerformanceState performance, Map<Long, CapState> caps) {

		CardSetup {
			caps = caps == null ? null : new HashMap<>(caps);
		}
	}
}
