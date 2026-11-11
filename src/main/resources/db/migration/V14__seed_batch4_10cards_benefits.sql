-- 4차 배치 10장: 알파벳카드B(CR056), IBK포인트(신용)(CR059), 네이버페이 taptap(CR065),
-- the Green Edition4(CR066), THE iD. PLATINUM(포인트)(CR070), 티머니 Pay & GO(CR071),
-- 삼성 iD SIMPLE(CR072), The CLASSIC-Y(CR073), 현대카드 Summit(CR075), WE:SH Travel(CR076).
-- 간소화 기준은 이전 배치와 동일. WE:SH Travel은 핵심 혜택이 전부 KB Pay 결제조건부라 반영할
-- 혜택이 없어 UNVERIFIED로 유지한다.

update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR056';
update card_products set annual_fee = 21000, product_version = '2024-C1a-13919', data_status = 'CALCULABLE' where product_key = 'CR059';
update card_products set annual_fee = 15000, product_version = '2017-C1a-12219', data_status = 'CALCULABLE' where product_key = 'CR065';
update card_products set annual_fee = 200000, data_status = 'CALCULABLE' where product_key = 'CR066';
update card_products set annual_fee = 220000, product_version = '2026-C1a-13506', data_status = 'CALCULABLE' where product_key = 'CR070';
update card_products set annual_fee = 15000, product_version = '2024-C1a-06864', data_status = 'CALCULABLE' where product_key = 'CR071';
update card_products set annual_fee = 7000, product_version = '2026-C1a-08052', data_status = 'CALCULABLE' where product_key = 'CR072';
update card_products set annual_fee = 100000, product_version = '2026-C1a-10345', data_status = 'CALCULABLE' where product_key = 'CR073';
update card_products set annual_fee = 200000, data_status = 'CALCULABLE' where product_key = 'CR075';
update card_products set annual_fee = 25000, product_version = '2024-C1a-11690' where product_key = 'CR076';

-- 알파벳카드B(CR056): 추가혜택(커피·편의점·택시) 5%만 반영. 기본혜택(뷰티·헬스·패션)은 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_cafe bigint; v_cat_convenience bigint; v_cat_taxi bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR056';
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
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '편의점 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '편의점 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '편의점 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인', '알파벳카드B 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- IBK포인트(신용)(CR059): 건당 결제금액별 0.6~3.3% 중 대표구간(1.1%)으로 간소화, 통합 월5만P(실적50만이상).
do $$
declare v_product_id bigint; v_tier_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR059';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, '국내외 가맹점 적립 통합 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_id, 'POINT_RATE', 0.011, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1.1% IBK포인트 적립', 'IBK포인트(신용) 카드혜택 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 네이버페이 taptap(CR065): 공통 0.5% 적립만 반영. 라이프스타일 패키지(일상/온라인, 전가맹점 기본값 없음)는 전체 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR065';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 가맹점 0.5% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'POINT_RATE', 0.005, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 0.5% 네이버페이 포인트', '네이버페이 taptap 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- the Green Edition4(CR066): 기본혜택(전가맹점1.5%, 50만이상) + 추가혜택 중 다이닝(음식점)만 5%(100만이상).
-- 여행·해외는 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_restaurant bigint; v_cap_base bigint; v_cap_dining bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR066';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 1000000, '100만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본혜택 1.5% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '다이닝(음식점) 추가혜택 한도') returning id into v_cap_dining;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_t1, v_cap_base, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1.5% M포인트(기본혜택)', 'the Green Edition4 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_restaurant, v_t2, v_cap_dining, 'POINT_RATE', 0.05, 1.0, '음식점 5% M포인트(추가혜택)', 'the Green Edition4 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- THE iD. PLATINUM(포인트)(CR070): 국내가맹점1% + 생활편의(온라인쇼핑·마트·백화점)1.2% + 스타벅스 3천원할인.
-- 특별가맹점(해외·면세·항공·여행·공연)과 배달앱, 디지털콘텐츠, 공항라운지는 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_online bigint; v_cat_mart bigint; v_cat_department bigint; v_cat_cafe bigint;
	v_cap_base bigint; v_cap_life bigint; v_cap_starbucks bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR070';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 가맹점 1% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '생활 편의 영역 1.2% — 한도 없음') returning id into v_cap_life;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description) values (v_product_id, 'MONTHLY', 1, '스타벅스 3천원 할인 월 1회') returning id into v_cap_starbucks;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '국내 가맹점 1% 빅포인트', 'THE iD. PLATINUM(포인트) 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_cap_life, 'POINT_RATE', 0.012, 1.0, '온라인쇼핑 1.2% 빅포인트(생활편의)', 'THE iD. PLATINUM(포인트) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_life, 'POINT_RATE', 0.012, 1.0, '마트 1.2% 빅포인트(생활편의)', 'THE iD. PLATINUM(포인트) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_cap_life, 'POINT_RATE', 0.012, 1.0, '백화점 1.2% 빅포인트(생활편의)', 'THE iD. PLATINUM(포인트) 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_cafe, v_cap_starbucks, 'DISCOUNT_FIXED', 3000, 10000, '스타벅스 3,000원 할인', 'THE iD. PLATINUM(포인트) 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 티머니 Pay & GO(CR071): 생활서비스(백화점·편의점·커피) 5%만 반영. 교통서비스는 특정 앱 결제 조건부,
-- 이동통신은 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_department bigint; v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR071';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30~50만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50~100만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1000000, '100만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '생활서비스 통합 한도(30~50만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '생활서비스 통합 한도(50~100만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 15000, '생활서비스 통합 한도(100만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_department, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '백화점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '백화점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '백화점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '편의점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '편의점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '편의점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 결제일할인', '티머니 Pay & GO 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 삼성 iD SIMPLE(CR072): 건별 10만원 미만 0.7% / 10만원 이상 1%, 둘 다 전가맹점·실적무관·한도없음.
-- 온라인쇼핑몰 멤버십(구독료 자체 할인)과 영화는 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR072';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '전가맹점 결제일할인 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.007, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.7% 할인(건당 10만원 미만)', '삼성 iD SIMPLE 카드 안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.01, 100000, jsonb_build_object('allMerchants', true), '국내외 가맹점 1% 할인(건당 10만원 이상)', '삼성 iD SIMPLE 카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- The CLASSIC-Y(CR073): 기본적립 0.7%(전가맹점) + Life할인(커피·택시) 5%. 특별적립(해외·아울렛·면세·골프),
-- 제과·화장품·영화, Gift Option, 문화·주유·면세점·항공 할인은 업종 밖/1회성이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_taxi bigint;
	v_cap_base bigint; v_cap_life bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR073';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본적립 0.7% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, 'Life 할인(커피·택시) 통합 한도') returning id into v_cap_life;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.007, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 0.7% 마이신한포인트(기본적립)', 'The CLASSIC-Y 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_life, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 결제일할인(Life)', 'The CLASSIC-Y 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_tier_id, v_cap_life, 'DISCOUNT_RATE', 0.05, '택시 5% 결제일할인(Life)', 'The CLASSIC-Y 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드 Summit(CR075): 기본혜택(전가맹점1.5%, 50만이상)만 반영. 추가혜택(교육·병원·여행·골프)은 업종 밖이라 제외.
do $$
declare v_product_id bigint; v_tier_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR075';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본혜택 1.5% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_id, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1.5% M포인트', '현대카드 Summit 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- WE:SH Travel(CR076): 핵심 혜택(일상영역 10%, 온라인항공/면세점/여행자보험)이 전부 KB Pay 결제조건부이고,
-- 철도·고속버스는 업종 밖이라 검토 결과 반영할 혜택이 없다. UNVERIFIED로 유지한다.
