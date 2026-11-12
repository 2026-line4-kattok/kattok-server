-- 5차 배치 10장: 알파벳카드T(CR077), LOCA LIKIT Eat(CR080), IBK포인트3.8(신용)(CR081),
-- 현대카드 ZERO Up(할인형)(CR083), BC 바로 모두의카드(CR084), 카드의정석2 7CORE(CR087),
-- 현대카드 MX Black Edition2(CR088), I-ALL(CR089), JADE Prime(CR090), LOCA Professional(CR091).
-- 배달앱(배달의민족/쿠팡이츠/요기요)은 음식점과 별도 가맹점 분류(MCC)를 쓰므로 8개 업종 중
-- 어느 카테고리로도 정확히 매칭되지 않아 계속 제외한다. 결제수단(즉시결제/Weekly자동결제)
-- 조건부 추가 캐시백, 연속이용 우대 조건, 멤버십/OTT 구독 할인도 기존 정책대로 제외한다.

update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR077';
update card_products set annual_fee = 10000, data_status = 'CALCULABLE' where product_key = 'CR080';
update card_products set annual_fee = 38000, product_version = '2024-C1a-14861', data_status = 'CALCULABLE' where product_key = 'CR081';
update card_products set annual_fee = 30000, data_status = 'CALCULABLE' where product_key = 'CR083';
update card_products set annual_fee = 6000, product_version = '2026-C1a-16535', data_status = 'CALCULABLE' where product_key = 'CR084';
update card_products set annual_fee = 50000, product_version = '2026-C1a-09560', data_status = 'CALCULABLE' where product_key = 'CR087';
update card_products set annual_fee = 200000, data_status = 'CALCULABLE' where product_key = 'CR088';
update card_products set annual_fee = 15000, product_version = '2023-C1a-00122', data_status = 'CALCULABLE' where product_key = 'CR089';
update card_products set annual_fee = 300000, product_version = '2024-C1a-06599', data_status = 'CALCULABLE' where product_key = 'CR090';
update card_products set annual_fee = 300000, product_version = '2026-C1a-13591', data_status = 'CALCULABLE' where product_key = 'CR091';

-- 알파벳카드T(CR077): 국내 가맹점 0.7% 청구할인(전가맹점, 실적/한도 없음). 해외 온오프라인 2%는 업종 밖.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR077';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 가맹점 0.7% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.007, jsonb_build_object('allMerchants', true), '국내 가맹점 0.7% 청구할인', '알파벳카드T 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- LOCA LIKIT Eat(CR080): 음식점·커피 60%만 반영. 배달앱·멤버십(쿠팡로켓와우 등)은 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_restaurant bigint; v_cat_cafe bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR080';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 400000, '40만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 13000, '모든 혜택 통합 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.60, '음식점 60% 결제일 할인', 'LOCA LIKIT Eat 상품안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.60, '커피전문점 60% 결제일 할인', 'LOCA LIKIT Eat 상품안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- IBK포인트3.8(신용)(CR081): 기본포인트 1.5% + 온라인쇼핑 0.5%. 해외 특별포인트는 업종 밖.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_online bigint; v_cap_base bigint; v_cap_online bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR081';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 포인트 1.5% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '온라인쇼핑 특별포인트 한도') returning id into v_cap_online;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_base, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 1.5% IBK포인트(기본)', 'IBK포인트3.8(신용) 상품안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_online, v_tier_id, v_cap_online, 'POINT_RATE', 0.005, 1.0, '온라인쇼핑 0.5% IBK포인트(특별)', 'IBK포인트3.8(신용) 상품안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드 ZERO Up(할인형)(CR083): 기본 0.8% + 온라인몰·대형마트 1.6%(10만원 이상). 교육/주유/이동통신은 업종 밖.
do $$
declare
	v_product_id bigint; v_cat_online bigint; v_cat_mart bigint; v_cap_base bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR083';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 가맹점 0.8% — 한도 없음') returning id into v_cap_base;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'DISCOUNT_RATE', 0.008, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.8% 청구할인(기본)', '현대카드 ZERO Up(할인형) 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_cap_base, 'DISCOUNT_RATE', 0.016, 100000, '온라인몰 1.6% 청구할인(추가, 10만원 이상)', '현대카드 ZERO Up(할인형) 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_base, 'DISCOUNT_RATE', 0.016, 100000, '대형마트 1.6% 청구할인(추가, 10만원 이상)', '현대카드 ZERO Up(할인형) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- BC 바로 모두의카드(CR084): 카페·편의점 5%만 반영. 대중교통/OTT/이동통신/해외는 업종 밖·구독 결제.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_cafe bigint; v_cat_convenience bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR084';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30~60만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 600000, '60~100만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1000000, '100만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '결제일 할인 통합한도(30~60만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '결제일 할인 통합한도(60~100만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 30000, '결제일 할인 통합한도(100만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 3000, '카페 5% 결제일 할인', 'BC 바로 모두의카드 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 3000, '카페 5% 결제일 할인', 'BC 바로 모두의카드 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, 3000, '카페 5% 결제일 할인', 'BC 바로 모두의카드 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 5000, '편의점 5% 결제일 할인', 'BC 바로 모두의카드 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 5000, '편의점 5% 결제일 할인', 'BC 바로 모두의카드 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, 5000, '편의점 5% 결제일 할인', 'BC 바로 모두의카드 혜택 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석2 7CORE(CR087): 7대 생활영역 중 온라인쇼핑·대형마트·커피 10%만 반영. 배달앱/교육/병원/주유는 제외.
-- 영역별 할인한도(전월실적 3구간)를 그대로 쓰고, 영역 간 통합 월 최대 한도는 간소화를 위해 생략.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_online bigint; v_cat_mart bigint; v_cat_cafe bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_online_t3 bigint;
	v_cap_mart_t1 bigint; v_cap_mart_t2 bigint; v_cap_mart_t3 bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint; v_cap_cafe_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR087';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상 100만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 1000000, '100만원 이상 200만원 미만') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 2000000, '200만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '온라인쇼핑 영역별 한도(50~100만원)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '온라인쇼핑 영역별 한도(100~200만원)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 12000, '온라인쇼핑 영역별 한도(200만원 이상)') returning id into v_cap_online_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '대형마트 영역별 한도(50~100만원)') returning id into v_cap_mart_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '대형마트 영역별 한도(100~200만원)') returning id into v_cap_mart_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 12000, '대형마트 영역별 한도(200만원 이상)') returning id into v_cap_mart_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '커피 영역별 한도(50~100만원)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '커피 영역별 한도(100~200만원)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 12000, '커피 영역별 한도(200만원 이상)') returning id into v_cap_cafe_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.10, '온라인쇼핑 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.10, '온라인쇼핑 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_online_t3, 'DISCOUNT_RATE', 0.10, '온라인쇼핑 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_mart_t1, 'DISCOUNT_RATE', 0.10, '대형마트 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_mart_t2, 'DISCOUNT_RATE', 0.10, '대형마트 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_mart_t3, 'DISCOUNT_RATE', 0.10, '대형마트 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_RATE', 0.10, '커피 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_RATE', 0.10, '커피 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_cafe_t3, 'DISCOUNT_RATE', 0.10, '커피 10% 청구할인', '카드의정석2 7CORE 혜택 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드 MX Black Edition2(CR088): 기본 1% M포인트 + 온라인쇼핑·백화점·커피(베이커리 포함)·편의점 10% 할인.
-- 주유·대중교통 영역은 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_tier_base bigint; v_t1 bigint; v_t2 bigint;
	v_cat_online bigint; v_cat_department bigint; v_cat_cafe bigint; v_cat_convenience bigint;
	v_cap_base bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint;
	v_cap_dept_t1 bigint; v_cap_dept_t2 bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint;
	v_cap_conv_t1 bigint; v_cap_conv_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR088';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_base;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 1000000, '100만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 2000000, '200만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 혜택 1% M포인트 — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '온라인쇼핑 영역별 한도(100만원 이상)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '온라인쇼핑 영역별 한도(200만원 이상)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '백화점 영역별 한도(100만원 이상)') returning id into v_cap_dept_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '백화점 영역별 한도(200만원 이상)') returning id into v_cap_dept_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '커피·베이커리 영역별 한도(100만원 이상)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '커피·베이커리 영역별 한도(200만원 이상)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '편의점 영역별 한도(100만원 이상)') returning id into v_cap_conv_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '편의점 영역별 한도(200만원 이상)') returning id into v_cap_conv_t2;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_base, v_cap_base, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 1% M포인트(기본)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.10, '온라인쇼핑 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.10, '온라인쇼핑 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t1, v_cap_dept_t1, 'DISCOUNT_RATE', 0.10, '백화점 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t2, v_cap_dept_t2, 'DISCOUNT_RATE', 0.10, '백화점 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_RATE', 0.10, '커피·베이커리 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_RATE', 0.10, '커피·베이커리 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_conv_t1, 'DISCOUNT_RATE', 0.10, '편의점 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_conv_t2, 'DISCOUNT_RATE', 0.10, '편의점 10% 청구할인(추가)', '현대카드 MX Black Edition2 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- I-ALL(CR089): ALL 0.5% 전가맹점 + LIVING(온라인쇼핑·대형마트·커피·편의점) 1%. 홈쇼핑/배달앱/교통/주유/통신/
-- 반려동물은 업종 밖·배달앱 정책, MEMBERSHIP(OTT 등 구독)은 제외. 연속이용 우대(1.5%/7%)는 간소화해 일반 요율만 사용.
do $$
declare
	v_product_id bigint; v_tier_all bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_online bigint; v_cat_mart bigint; v_cat_cafe bigint; v_cat_convenience bigint;
	v_cap_all bigint; v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR089';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 700000, '70만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1000000, '100만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', 'ALL 0.5% — 한도 없음') returning id into v_cap_all;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, 'LIVING+MEMBERSHIP 통합 한도(50만원 이상)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 30000, 'LIVING+MEMBERSHIP 통합 한도(70만원 이상)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 40000, 'LIVING+MEMBERSHIP 통합 한도(100만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_all, 'DISCOUNT_RATE', 0.005, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.5% 할인(ALL)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.01, '온라인쇼핑 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.01, '온라인쇼핑 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.01, '온라인쇼핑 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.01, '대형마트 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.01, '대형마트 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.01, '대형마트 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.01, '커피 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.01, '커피 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.01, '커피 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.01, '편의점 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.01, '편의점 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.01, '편의점 1% 할인(LIVING)', 'I-ALL 혜택 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- JADE Prime(CR090): 기본적립 1.0%/1.5%(200만원 기준 금액대별 중복 규칙으로 표현) + 택시 50%.
-- 해외/항공/면세/여행/주유/EV충전/디지털컨텐츠는 업종 밖.
do $$
declare
	v_product_id bigint; v_tier_high bigint; v_tier_taxi bigint;
	v_cat_taxi bigint; v_cap_base_low bigint; v_cap_base_high bigint; v_cap_taxi bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR090';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 2000000, '200만원 이상') returning id into v_tier_high;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_tier_taxi;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본적립 1.0% — 한도 없음') returning id into v_cap_base_low;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 100000, '기본적립 1.5% 한도(200만원 이상)') returning id into v_cap_base_high;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '택시 50% 적립 한도') returning id into v_cap_taxi;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base_low, 'POINT_RATE', 0.010, 1.0, jsonb_build_object('allMerchants', true), '국내 가맹점 1.0% 하나머니(기본적립)', 'JADE Prime 상품안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_high, v_cap_base_high, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('allMerchants', true), '국내 가맹점 1.5% 하나머니(기본적립, 200만원 이상)', 'JADE Prime 상품안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_taxi, v_tier_taxi, v_cap_taxi, 'POINT_RATE', 0.50, 1.0, '택시 50% 하나머니 적립', 'JADE Prime 상품안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- LOCA Professional(CR091): 기본 캐시백 국내외 1%만 반영. 즉시결제/Weekly자동결제 추가 1%는 결제수단
-- 조건부라 제외, SPECIAL GIFT(분기 실적 1000만원 상품권)는 1회성 보상이라 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR091';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 캐시백 1% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.01, jsonb_build_object('allMerchants', true), '국내외 가맹점 1% 캐시백(기본)', 'LOCA Professional 카드 혜택 안내', 'VERIFIED', now(), 1, current_date);
end $$;
