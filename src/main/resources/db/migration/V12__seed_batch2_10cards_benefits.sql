-- 2차 배치 10장: K-패스(신용)(CR025), THE iD. 1st(CR026), Deep Oil(CR027), 알파벳카드R(CR028),
-- KB YOU Prime(CR029), iD SELECT ON(CR030), KB ALL(CR031), 쿠팡 와우 카드(CR032),
-- 디지로카 London(CR033), LOCA LIKIT 2.0(CR034).
-- 공통 간소화/제외 기준은 1차 배치와 동일(선택형 기본값 없으면 전체 제외, 업종 밖/결제수단 조건부 제외,
-- 일 단위 세부 횟수 한도·1회성 웰컴 혜택 생략). KB YOU Prime은 선택형 팩 2종 다 기본값이 없어 전체 제외되어
-- 반영할 혜택이 없다(UNVERIFIED 유지).

update card_products set annual_fee = 2000, product_version = '2024-C1a-03983', data_status = 'CALCULABLE' where product_key = 'CR025';
update card_products set annual_fee = 150000, product_version = '2025-C1a-14033', data_status = 'CALCULABLE' where product_key = 'CR026';
update card_products set annual_fee = 10000, product_version = '2026-C1a-11879', data_status = 'CALCULABLE' where product_key = 'CR027';
update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR028';
update card_products set annual_fee = 30000, product_version = '2026-C1a-00712' where product_key = 'CR029';
update card_products set annual_fee = 20000, product_version = '2025-C1a-09518', data_status = 'CALCULABLE' where product_key = 'CR030';
update card_products set annual_fee = 20000, product_version = '2025-C1a-17515', data_status = 'CALCULABLE' where product_key = 'CR031';
update card_products set annual_fee = 20000, product_version = '2025-C1a-07945', data_status = 'CALCULABLE' where product_key = 'CR032';
update card_products set annual_fee = 20000, product_version = '2026-C1a-13383', data_status = 'CALCULABLE' where product_key = 'CR033';
update card_products set annual_fee = 20000, product_version = '2026-C1a-05322', data_status = 'CALCULABLE' where product_key = 'CR034';

-- CR025: 일반교통(철도/고속버스/택시) 5% 중 택시만 반영. 대중교통(버스/지하철)은 K-패스 교통혜택이라 범위 밖.
-- 주유(리터당)·통신·외식(아웃백/빕스 브랜드 한정)·영화·놀이공원은 제외/간소화.
do $$
declare v_product_id bigint; v_tier_id bigint; v_cat_taxi bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR025';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '일반교통(택시) 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_taxi, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, '택시 5% 할인', 'K-패스(신용) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR026: 빅포인트 적립(실적 50만원 이상 단일 조건). 프리미엄 영역 연 기프트(연 2~3회, 50% 상당)는
-- 연 단위 카운트 기반이라 간소화 차원에서 제외. 공항라운지는 업종 밖.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_restaurant bigint; v_cat_convenience bigint; v_cat_cafe bigint; v_cat_department bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR026';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '빅포인트 적립 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 빅포인트(그 외 가맹점)', 'THE iD. 1st 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_id, 'POINT_RATE', 0.015, 1.0, '음식점 1.5% 빅포인트', 'THE iD. 1st 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_id, 'POINT_RATE', 0.015, 1.0, '편의점 1.5% 빅포인트', 'THE iD. 1st 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'POINT_RATE', 0.015, 1.0, '커피전문점 1.5% 빅포인트', 'THE iD. 1st 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_tier_id, v_cap_id, 'POINT_RATE', 0.015, 1.0, '백화점 1.5% 빅포인트', 'THE iD. 1st 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR027: 생활서비스 5%(편의점·커피·택시, 통합 한도, 2단계 실적구간). 주유(선택정유사)·정비소/주차장·영화는 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_convenience bigint; v_cat_cafe bigint; v_cat_taxi bigint;
	v_cap_t1 bigint; v_cap_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR027';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30~70만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 700000, '70만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 150000, '생활서비스 통합 한도(30~70만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 300000, '생활서비스 통합 한도(70만원 이상)') returning id into v_cap_t2;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '편의점 5% 할인', 'Deep Oil 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '편의점 5% 할인', 'Deep Oil 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 할인', 'Deep Oil 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 할인', 'Deep Oil 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '택시 5% 할인', 'Deep Oil 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '택시 5% 할인', 'Deep Oil 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR028: 추가혜택(커피·편의점·대중교통 중 택시만) 5%, 영역별+통합 한도는 통합 한도만 반영(간소화).
-- 기본혜택(생활요금·통신·디지털콘텐츠·온라인멤버십)은 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_cafe bigint; v_cat_convenience bigint; v_cat_taxi bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR028';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 400000, '40~80만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 800000, '80~120만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1200000, '120만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '추가혜택 통합 한도(40~80만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '추가혜택 통합 한도(80~120만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 15000, '추가혜택 통합 한도(120만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '편의점 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '편의점 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '편의점 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인', '알파벳카드R 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR029: 서비스팩 2종(일상팩/가족팩) 전부 전가맹점 기본값이 없는 택1이라 전체 제외.
-- 검토 결과 반영할 혜택이 없어 UNVERIFIED로 유지한다.

-- CR030: SELECT 서비스(택1, 전가맹점 기본값 없음)는 제외. 생활 편의 영역 5% 중 커피·택시만 반영.
do $$
declare
	v_product_id bigint; v_tier_id bigint; v_cat_cafe bigint; v_cat_taxi bigint;
	v_cap_cafe bigint; v_cap_taxi bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR030';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '생활편의(커피) 한도') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '생활편의(택시) 한도') returning id into v_cap_taxi;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 할인', 'iD SELECT ON 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_tier_id, v_cap_taxi, 'DISCOUNT_RATE', 0.05, '택시 5% 할인', 'iD SELECT ON 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR031: 국내 가맹점 1% 청구할인(전가맹점, 실적·한도 없음). 쇼핑멤버십/OTT/이동통신은 업종 밖.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR031';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 가맹점 1% 청구할인 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.01, jsonb_build_object('allMerchants', true), '국내 가맹점 1% 청구할인', 'KB ALL 카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR032: 쿠팡(브랜드 한정) 2% 적립 + 쿠팡 외 전가맹점 0.2% 적립(쿠팡 제외). 쿠팡이츠/쿠팡플레이는 업종 밖.
do $$
declare v_product_id bigint; v_merchant_coupang bigint; v_cap_coupang bigint; v_cap_rest bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR032';
	select id into v_merchant_coupang from merchants where name = '쿠팡' and channel = 'ONLINE';
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '쿠팡 2% 적립 한도') returning id into v_cap_coupang;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '쿠팡 외 전가맹점 0.2% 적립 한도') returning id into v_cap_rest;
	insert into benefit_rules (product_id, merchant_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_merchant_coupang, v_cap_coupang, 'POINT_RATE', 0.02, 1.0, '쿠팡 2% 쿠팡캐시 적립', '쿠팡 와우 카드 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_rest, 'POINT_RATE', 0.002, 1.0,
		jsonb_build_object('allMerchants', true, 'excludedMerchantIds', jsonb_build_array(v_merchant_coupang)),
		'쿠팡 외 전가맹점 0.2% 쿠팡캐시 적립', '쿠팡 와우 카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR033: 국내외 가맹점 0.7% 캐시백(전가맹점, 실적·한도 없음). 즉시결제/Weekly자동결제 추가캐시백은
-- 결제 행동 조건부라 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR033';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '전가맹점 0.7% 캐시백 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.007, jsonb_build_object('allMerchants', true), '전가맹점 0.7% 캐시백', '디지로카 London 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CR034: 국내 가맹점 1.2% 결제일할인(전가맹점, 실적·한도 없음). 해외 2%는 제외(해외).
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR034';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 가맹점 1.2% 할인 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.012, jsonb_build_object('allMerchants', true), '국내 가맹점 1.2% 결제일할인', 'LOCA LIKIT 2.0 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;
