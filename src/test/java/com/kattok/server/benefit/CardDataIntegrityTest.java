package com.kattok.server.benefit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.benefit.entity.VerificationStatus;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.PerformanceType;

/**
 * 상품설명서에서 옮긴 카드 혜택 데이터 전체를 배포 전에 자동으로 검사한다.
 *
 * <p>사람(또는 AI)이 데이터를 옮길 때 생기기 쉬운 실수를 규칙으로 막는다. 허용되지 않은 값, 범위를 벗어난 할인율,
 * 다른 카드의 한도·실적구간을 잘못 연결한 경우, 존재하지 않는 결제처·업종을 가리키는 조건, 출처 없는 검증 규칙 등이다.
 * 실패하면 어느 카드의 어느 규칙이 왜 틀렸는지 목록으로 보여준다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CardDataIntegrityTest {

	/** 서비스가 다루는 8개 업종(카드 데이터 명세 v2). */
	private static final Set<String> SERVICE_CATEGORIES = Set.of("CONVENIENCE_STORE", "CAFE", "RESTAURANT", "MART",
			"ONLINE_SHOPPING", "PHARMACY", "TAXI", "DEPARTMENT_STORE");
	/** 허용하는 조건 키. 계산 엔진(RuleConditions)이 읽는 키와 기록용 키뿐이고, 이 밖의 키는 오타이거나 엔진이 모르는 조건이다. */
	private static final Set<String> CONDITION_KEYS = Set.of("allMerchants", "excludedCategoryIds", "excludedMerchantIds",
			"daysOfWeek", "timeRange", "maxPaymentAmount", "merchantIds",
			// 계산에 쓰지 않고 원문 조건을 기록만 하는 키(알파벳카드S, V2). channel은 결제처 지정 규칙이라 결제처 채널로 이미 정해지고,
			// 할부 제외는 서비스가 할부 여부를 받지 않아 계산하지 않기로 했다(카드 데이터 명세 v2).
			"channel", "installmentExcluded");
	private static final Set<String> DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("상태·유형 값은 코드의 enum에 있는 값만 쓴다")
	void enumValuesAreKnown() {
		List<String> violations = new ArrayList<>();
		violations.addAll(unknown("select id, benefit_type from benefit_rules", BenefitType.class, "rule", "benefit_type"));
		violations.addAll(unknown("select id, verification_status from benefit_rules", VerificationStatus.class, "rule",
				"verification_status"));
		violations.addAll(unknown("select id, cap_period from benefit_cap_groups", CapPeriod.class, "cap group", "cap_period"));
		violations.addAll(unknown("select id, data_status from card_products", DataStatus.class, "product", "data_status"));
		violations.addAll(unknown("select id, performance_type from card_products where data_status = 'CALCULABLE'",
				PerformanceType.class, "product", "performance_type"));

		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("비율 혜택은 0% 초과 100% 이하, 정액 혜택은 금액이 0원보다 크다")
	void benefitValuesAreInRange() {
		assertThat(violations("""
				select rule_label(r.id) || ': ' || r.benefit_type || ' rate=' || coalesce(r.rate::text, 'null')
				from benefit_rules r
				where r.benefit_type in ('DISCOUNT_RATE', 'POINT_RATE') and (r.rate is null or r.rate <= 0 or r.rate > 1)
				union all
				select rule_label(r.id) || ': MILE_RATE rate=' || coalesce(r.rate::text, 'null')
				from benefit_rules r
				where r.benefit_type = 'MILE_RATE' and (r.rate is null or r.rate <= 0)
				union all
				select rule_label(r.id) || ': ' || r.benefit_type || ' amount=' || coalesce(r.amount::text, 'null')
				from benefit_rules r
				where r.benefit_type in ('DISCOUNT_FIXED', 'POINT_FIXED') and (r.amount is null or r.amount <= 0)
				union all
				select rule_label(r.id) || ': point_krw_ratio=' || r.point_krw_ratio
				from benefit_rules r where r.point_krw_ratio is not null and r.point_krw_ratio <= 0
				union all
				select rule_label(r.id) || ': min_payment_amount=' || r.min_payment_amount
				from benefit_rules r where r.min_payment_amount is not null and r.min_payment_amount <= 0
				union all
				select rule_label(r.id) || ': max_eligible_txn_amount=' || r.max_eligible_txn_amount
				from benefit_rules r where r.max_eligible_txn_amount is not null and r.max_eligible_txn_amount <= 0
				union all
				select rule_label(r.id) || ': valid_from > valid_to'
				from benefit_rules r where r.valid_to is not null and r.valid_from > r.valid_to
				""")).isEmpty();
	}

	@Test
	@DisplayName("규칙이 가리키는 한도·실적구간은 같은 카드의 것이다")
	void ruleReferencesBelongToSameProduct() {
		assertThat(violations("""
				select rule_label(r.id) || ': 다른 카드의 한도 그룹 ' || c.id
				from benefit_rules r join benefit_cap_groups c on c.id = r.cap_group_id
				where c.product_id <> r.product_id
				union all
				select rule_label(r.id) || ': 다른 카드의 실적구간 ' || t.id
				from benefit_rules r join card_performance_tiers t on t.id = r.required_tier_id
				where t.product_id <> r.product_id
				union all
				select 'cap group ' || c.id || ': 다른 카드의 상위 한도 ' || p.id
				from benefit_cap_groups c join benefit_cap_groups p on p.id = c.parent_cap_group_id
				where p.product_id <> c.product_id
				""")).isEmpty();
	}

	@Test
	@DisplayName("한도는 0보다 크고, 상위 한도는 한 단계까지만 이어진다")
	void capGroupsAreSane() {
		assertThat(violations("""
				select 'cap group ' || c.id || ': cap_amount=' || c.cap_amount
				from benefit_cap_groups c where c.cap_amount is not null and c.cap_amount <= 0
				union all
				select 'cap group ' || c.id || ': cap_count=' || c.cap_count
				from benefit_cap_groups c where c.cap_count is not null and c.cap_count <= 0
				union all
				select 'cap group ' || c.id || ': 상위 한도의 상위 한도가 또 있음'
				from benefit_cap_groups c join benefit_cap_groups p on p.id = c.parent_cap_group_id
				where p.parent_cap_group_id is not null
				union all
				select 'cap group ' || c.id || ': 하위 한도(' || c.cap_amount || ')가 상위 한도(' || p.cap_amount || ')보다 큼'
				from benefit_cap_groups c join benefit_cap_groups p on p.id = c.parent_cap_group_id
				where c.cap_amount is not null and p.cap_amount is not null and c.cap_amount > p.cap_amount
				""")).isEmpty();
	}

	@Test
	@DisplayName("모든 규칙은 대상(업종·결제처·전 가맹점)이 있고, 업종은 서비스 8개 업종 안이다")
	void everyRuleHasServiceTarget() {
		List<String> violations = new ArrayList<>(violations("""
				select rule_label(r.id) || ': 대상 없음'
				from benefit_rules r
				where r.category_id is null and r.merchant_id is null
					and coalesce((r.conditions ->> 'allMerchants')::boolean, false) = false
				"""));
		jdbc.queryForList(labeled("""
				select rule_label(r.id) as label, c.code
				from benefit_rules r join merchant_categories c on c.id = r.category_id
				""")).stream()
				.filter(row -> !SERVICE_CATEGORIES.contains((String) row.get("code")))
				.forEach(row -> violations.add(row.get("label") + ": 서비스 밖 업종 " + row.get("code")));

		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("조건은 엔진이 아는 키만 쓰고, 가리키는 결제처·업종이 실제로 있다")
	void conditionsAreWellFormed() {
		List<String> violations = new ArrayList<>(violations("""
				select rule_label(r.id) || ': 모르는 조건 키 ' || k
				from benefit_rules r, jsonb_object_keys(coalesce(r.conditions, '{}'::jsonb)) k
				where k <> all (?)
				""", (Object) CONDITION_KEYS.toArray(String[]::new)));
		violations.addAll(violations("""
				select rule_label(r.id) || ': 없는 결제처 ' || target.merchant_id
				from benefit_rules r, jsonb_array_elements_text(coalesce(r.conditions -> 'merchantIds', '[]')
					|| coalesce(r.conditions -> 'excludedMerchantIds', '[]')) as target(merchant_id)
				where not exists (select 1 from merchants m where m.id = target.merchant_id::bigint)
				union all
				select rule_label(r.id) || ': 없는 업종 ' || target.category_id
				from benefit_rules r, jsonb_array_elements_text(coalesce(r.conditions -> 'excludedCategoryIds', '[]')) as target(category_id)
				where not exists (select 1 from merchant_categories c where c.id = target.category_id::bigint)
				union all
				select rule_label(r.id) || ': merchantIds가 빈 목록'
				from benefit_rules r where jsonb_typeof(r.conditions -> 'merchantIds') = 'array'
					and jsonb_array_length(r.conditions -> 'merchantIds') = 0
				"""));
		violations.addAll(violations("""
				select rule_label(r.id) || ': 요일 값 ' || d
				from benefit_rules r, jsonb_array_elements_text(coalesce(r.conditions -> 'daysOfWeek', '[]')) d
				where d <> all (?)
				""", (Object) DAYS.toArray(String[]::new)));

		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("브랜드 한정(merchantIds) 규칙의 결제처는 규칙 업종에 속한다")
	void brandLimitedMerchantsMatchRuleCategory() {
		assertThat(violations("""
				select rule_label(r.id) || ': ' || m.name || '(' || mc.code || ')는 규칙 업종 ' || rc.code || '이 아님'
				from benefit_rules r
				join merchant_categories rc on rc.id = r.category_id
				cross join jsonb_array_elements_text(r.conditions -> 'merchantIds') as target(merchant_id)
				join merchants m on m.id = target.merchant_id::bigint
				join merchant_categories mc on mc.id = m.category_id
				where m.category_id <> r.category_id
				""")).isEmpty();
	}

	@Test
	@DisplayName("검증된 규칙에는 원문 출처와 검증 시각이 있다")
	void verifiedRulesHaveProvenance() {
		assertThat(violations("""
				select rule_label(r.id) || ': 출처 없음'
				from benefit_rules r
				where r.verification_status = 'VERIFIED' and (r.source_ref is null or btrim(r.source_ref) = '')
				union all
				select rule_label(r.id) || ': 검증 시각 없음'
				from benefit_rules r where r.verification_status = 'VERIFIED' and r.verified_at is null
				""")).isEmpty();
	}

	@Test
	@DisplayName("계산 가능한 카드는 검증된 규칙이 있고, 실적 유형이 실적구간 수와 맞으며 구간 금액이 오른다")
	void calculableProductsAreConsistent() {
		assertThat(violations("""
				select p.name || ': 검증된 규칙 없음'
				from card_products p
				where p.data_status = 'CALCULABLE'
					and not exists (select 1 from benefit_rules r where r.product_id = p.id and r.verification_status = 'VERIFIED')
				union all
				select p.name || ': performance_type=' || p.performance_type || ', 실적구간 ' || count(t.id) || '개'
				from card_products p left join card_performance_tiers t on t.product_id = p.id
				where p.data_status = 'CALCULABLE'
				group by p.id, p.name, p.performance_type
				having (p.performance_type = 'NONE' and count(t.id) <> 0)
					or (p.performance_type = 'SINGLE' and count(t.id) <> 1)
					or (p.performance_type = 'MULTIPLE' and count(t.id) < 2)
				union all
				select p.name || ': 실적구간 ' || a.level || '(' || a.min_amount || ') >= ' || b.level || '(' || b.min_amount || ')'
				from card_performance_tiers a
				join card_performance_tiers b on b.product_id = a.product_id and b.level > a.level
				join card_products p on p.id = a.product_id
				where a.min_amount >= b.min_amount
				union all
				select p.name || ': 실적구간 level ' || t.level || ' 중복'
				from card_performance_tiers t join card_products p on p.id = t.product_id
				group by p.name, t.product_id, t.level having count(*) > 1
				""")).isEmpty();
	}

	@Test
	@DisplayName("검사 범위: 계산 가능한 카드와 규칙이 실제로 적재되어 있다")
	void reportsCoverage() {
		Map<String, Object> counts = jdbc.queryForMap("""
				select (select count(*) from card_products where data_status = 'CALCULABLE') as products,
					(select count(*) from benefit_rules) as rules,
					(select count(*) from benefit_rules where verification_status = 'VERIFIED') as verified,
					(select count(*) from benefit_cap_groups) as caps
				""");
		System.out.printf("카드 데이터 정합성 검사 범위: 계산 가능 카드 %s장, 규칙 %s개(검증 %s개), 한도 그룹 %s개%n",
				counts.get("products"), counts.get("rules"), counts.get("verified"), counts.get("caps"));

		assertThat(((Number) counts.get("products")).longValue()).isPositive();
		assertThat(((Number) counts.get("rules")).longValue()).isPositive();
	}

	/** 위반 목록 조회. {@code rule_label(r.id)}는 규칙을 사람이 찾을 수 있게 "카드 이름 / rule id"로 바꿔 보여준다. */
	private List<String> violations(String sql, Object... args) {
		return jdbc.queryForList(labeled(sql), String.class, args);
	}

	private static String labeled(String sql) {
		return sql.replace("rule_label(r.id)",
				"(select lp.name || ' / rule ' || r.id from card_products lp where lp.id = r.product_id)");
	}

	private <E extends Enum<E>> List<String> unknown(String sql, Class<E> type, String label, String column) {
		Set<String> known = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
		return jdbc.queryForList(sql).stream()
				.filter(row -> !known.contains((String) row.get(column)))
				.map(row -> label + " " + row.get("id") + ": " + column + "=" + row.get(column))
				.toList();
	}
}
