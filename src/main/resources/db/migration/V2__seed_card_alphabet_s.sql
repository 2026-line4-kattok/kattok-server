-- 데모 카드 1: 알파벳카드S(현대카드).
-- 출처: 알파벳카드S 상품 가이드북(2025-09-08, 여신금융협회 심의필 2025-C1a-13175)
-- 간소화: PDF의 가맹점 화이트리스트(기본혜택 11개+, 추가혜택 다수) 중 대표 가맹점만 시딩했다.
-- "영역별 고정 한도 + 통합 한도" 이중 한도는 통합 한도만 반영했다 (docs/domain-model.md 5번 미해결 항목 1).

do $$
declare
	v_issuer_id bigint;
	v_product_id bigint;
	v_tier1_id bigint;
	v_tier2_id bigint;
	v_tier3_id bigint;
	v_category_shopping_id bigint;
	v_category_cafe_id bigint;
	v_merchant_coupang_id bigint;
	v_merchant_starbucks_id bigint;
	v_cap_basic_t1_id bigint;
	v_cap_basic_t2_id bigint;
	v_cap_basic_t3_id bigint;
	v_cap_extra_t1_id bigint;
	v_cap_extra_t2_id bigint;
	v_cap_extra_t3_id bigint;
begin
	insert into card_issuers (name, code)
		values ('현대카드', 'HYUNDAI')
		on conflict (code) do update set name = excluded.name
		returning id into v_issuer_id;

	insert into card_products (issuer_id, name, card_type, product_version, data_status, annual_fee)
		values (v_issuer_id, '알파벳카드S', 'CREDIT', '2025-C1a-13175', 'CALCULABLE', 15000)
		returning id into v_product_id;

	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 1, 400000, '40만원 이상')
		returning id into v_tier1_id;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 2, 800000, '80만원 이상')
		returning id into v_tier2_id;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 3, 1200000, '120만원 이상')
		returning id into v_tier3_id;

	insert into merchant_categories (name, code)
		values ('온라인쇼핑', 'ONLINE_SHOPPING')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_shopping_id;
	insert into merchant_categories (name, code)
		values ('커피전문점', 'CAFE')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_cafe_id;

	-- category_id는 기존 값을 유지한다(다른 카드 시드가 같은 결제처를 다른 업종으로 덮어쓰지 않게).
	insert into merchants (name, channel, category_id)
		values ('쿠팡', 'ONLINE', v_category_shopping_id)
		on conflict (name, channel) do update set name = excluded.name
		returning id into v_merchant_coupang_id;
	insert into merchants (name, channel, category_id)
		values ('스타벅스', 'OFFLINE', v_category_cafe_id)
		on conflict (name, channel) do update set name = excluded.name
		returning id into v_merchant_starbucks_id;

	-- 기본 혜택 10%: 온라인몰·마트·백화점 등 화이트리스트 중 쿠팡만 대표로 시딩
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 10000, '기본혜택 통합 한도 (40만원대 구간)')
		returning id into v_cap_basic_t1_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 20000, '기본혜택 통합 한도 (80만원대 구간)')
		returning id into v_cap_basic_t2_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 35000, '기본혜택 통합 한도 (120만원 이상 구간)')
		returning id into v_cap_basic_t3_id;

	insert into benefit_rules (
		product_id, merchant_id, required_tier_id, cap_group_id, benefit_type, rate,
		min_payment_amount, conditions, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_merchant_coupang_id, v_tier1_id, v_cap_basic_t1_id, 'DISCOUNT_RATE', 0.10,
			null, '{"channel":"ONLINE","installmentExcluded":true}'::jsonb,
			'알파벳카드S 상품 가이드북 p.2 기본혜택', 'VERIFIED', now(), 1, date '2025-09-08'),
		(v_product_id, v_merchant_coupang_id, v_tier2_id, v_cap_basic_t2_id, 'DISCOUNT_RATE', 0.10,
			null, '{"channel":"ONLINE","installmentExcluded":true}'::jsonb,
			'알파벳카드S 상품 가이드북 p.2 기본혜택', 'VERIFIED', now(), 1, date '2025-09-08'),
		(v_product_id, v_merchant_coupang_id, v_tier3_id, v_cap_basic_t3_id, 'DISCOUNT_RATE', 0.10,
			null, '{"channel":"ONLINE","installmentExcluded":true}'::jsonb,
			'알파벳카드S 상품 가이드북 p.2 기본혜택', 'VERIFIED', now(), 1, date '2025-09-08');

	-- 추가 혜택 5%: 커피전문점·편의점·대중교통 중 스타벅스만 대표로 시딩
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 5000, '추가혜택 통합 한도 (40만원대 구간)')
		returning id into v_cap_extra_t1_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 10000, '추가혜택 통합 한도 (80만원대 구간)')
		returning id into v_cap_extra_t2_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 15000, '추가혜택 통합 한도 (120만원 이상 구간)')
		returning id into v_cap_extra_t3_id;

	insert into benefit_rules (
		product_id, merchant_id, required_tier_id, cap_group_id, benefit_type, rate,
		min_payment_amount, conditions, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_merchant_starbucks_id, v_tier1_id, v_cap_extra_t1_id, 'DISCOUNT_RATE', 0.05,
			null, '{"channel":"OFFLINE"}'::jsonb,
			'알파벳카드S 상품 가이드북 p.3 추가혜택', 'VERIFIED', now(), 1, date '2025-09-08'),
		(v_product_id, v_merchant_starbucks_id, v_tier2_id, v_cap_extra_t2_id, 'DISCOUNT_RATE', 0.05,
			null, '{"channel":"OFFLINE"}'::jsonb,
			'알파벳카드S 상품 가이드북 p.3 추가혜택', 'VERIFIED', now(), 1, date '2025-09-08'),
		(v_product_id, v_merchant_starbucks_id, v_tier3_id, v_cap_extra_t3_id, 'DISCOUNT_RATE', 0.05,
			null, '{"channel":"OFFLINE"}'::jsonb,
			'알파벳카드S 상품 가이드북 p.3 추가혜택', 'VERIFIED', now(), 1, date '2025-09-08');
end $$;
