-- 3차 배치 10장: JADE Classic(CR036), 현대카드ZERO Edition3 할인형(CR039), LOCA LIKIT 1.5(CR040),
-- 네이버 현대카드 Edition3(CR042), Point Plan(CR043), 카드의정석2 SHOPPING+(CR047),
-- THE SHOPPER(CR051), 디지로카 Las Vegas(CR052), 알파벳카드H(CR054), 신한 모두의카드(CR055).
-- 공통 간소화 기준은 1·2차 배치와 동일. 추가로 이번 배치부터: 금액 구간별 차등 요율(결제금액별 0.5~3%형)은
-- 대표 구간 요율 하나로 간소화했고(정답 케이스 카드가 아님), 브랜드 한정 혜택(스타벅스 등)은 동일 업종
-- 카테고리로 일반화해서 반영했다. 일부 상품설명서에 심의필 번호가 없어 product_version을 NULL로 둔 카드도 있다.

-- JADE Classic(CR036): 기본적립1%(전가맹점) + 특별적립(온라인쇼핑1.5%/백화점·마트1.2%, 통합) +
-- 커피(스타벅스→카페 일반화) 50% + 택시 50%. 해외/항공/면세/여행/아울렛/주유/디지털컨텐츠는 업종 밖이라 제외.
update card_products set annual_fee = 120000, product_version = '2026-C1a-10094', data_status = 'CALCULABLE' where product_key = 'CR036';
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_online bigint; v_cat_department bigint; v_cat_mart bigint; v_cat_cafe bigint; v_cat_taxi bigint;
	v_cap_base bigint; v_cap_special bigint; v_cap_coffee bigint; v_cap_taxi bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR036';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본적립 1% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 100000, '특별적립 통합 한도(10만 하나머니)') returning id into v_cap_special;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '커피 50% 적립 한도') returning id into v_cap_coffee;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '택시 50% 적립 한도') returning id into v_cap_taxi;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 하나머니(기본적립)', 'JADE Classic 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_tier_id, v_cap_special, 'POINT_RATE', 0.015, 1.0, '온라인쇼핑 1.5% 하나머니(특별적립)', 'JADE Classic 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_tier_id, v_cap_special, 'POINT_RATE', 0.012, 1.0, '백화점 1.2% 하나머니(특별적립)', 'JADE Classic 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_tier_id, v_cap_special, 'POINT_RATE', 0.012, 1.0, '마트 1.2% 하나머니(특별적립)', 'JADE Classic 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_coffee, 'POINT_RATE', 0.5, 1.0, '커피전문점 50% 하나머니', 'JADE Classic 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_tier_id, v_cap_taxi, 'POINT_RATE', 0.5, 1.0, '택시 50% 하나머니', 'JADE Classic 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드ZERO Edition3(할인형)(CR039): 기본 0.8% 청구할인(전가맹점, 실적·한도 없음). 심의필 미확인(NULL).
update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR039';
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR039';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '전가맹점 0.8% 청구할인 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.008, jsonb_build_object('allMerchants', true), '전가맹점 0.8% 청구할인', '현대카드ZERO Edition3(할인형) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- LOCA LIKIT 1.5(CR040): 국내 가맹점 0.8% 결제일할인(전가맹점, 실적·한도 없음). 해외 1.5%는 제외.
update card_products set annual_fee = 10000, product_version = '2026-C1a-05318', data_status = 'CALCULABLE' where product_key = 'CR040';
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR040';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 가맹점 0.8% 할인 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.008, jsonb_build_object('allMerchants', true), '국내 가맹점 0.8% 결제일할인', 'LOCA LIKIT 1.5 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 네이버 현대카드 Edition3(CR042): 기본혜택만 반영(전가맹점 0.5% 네이버페이 포인트, 전월40만이상, 한도없음).
-- 추가혜택(네이버플러스 멤버십 적립대상 최대5%)은 특정 채널 조건부라 간소화 차원에서 제외. 심의필 미확인(NULL).
update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR042';
do $$
declare v_product_id bigint; v_tier_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR042';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 400000, '40만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본혜택 0.5% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_id, 'POINT_RATE', 0.005, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 0.5% 네이버페이 포인트', '네이버 현대카드 Edition3 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- Point Plan(CR043): 일상생활비 적립은 금액구간별 0.5~3% 중 대표구간(1.0%)으로 간소화,
-- 전월 이용금액 구간별 통합 한도만 그대로 반영. 주말 외식비(음식점만, 배달앱 제외)는 건당 정액 포인트.
-- 정기결제 자동납부(통신/가스전기)는 업종 밖이라 제외.
update card_products set annual_fee = 20000, product_version = '2024-C1a-06663', data_status = 'CALCULABLE' where product_key = 'CR043';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_restaurant bigint; v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint; v_cap_weekend bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR043';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 400000, '40~80만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 800000, '80~120만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1200000, '120만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '일상생활비 적립 한도(40~80만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 30000, '일상생활비 적립 한도(80~120만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, '일상생활비 적립 한도(120만원 이상)') returning id into v_cap_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description) values (v_product_id, 'MONTHLY', 5, '주말 외식비 포인트 적립 월 횟수') returning id into v_cap_weekend;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_t1, v_cap_t1, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 마이신한포인트(일상생활비)', 'Point Plan 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t2, v_cap_t2, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 마이신한포인트(일상생활비)', 'Point Plan 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t3, v_cap_t3, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 마이신한포인트(일상생활비)', 'Point Plan 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, amount, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_restaurant, v_cap_weekend, 'POINT_FIXED', 1000, 20000, jsonb_build_object('daysOfWeek', jsonb_build_array('SAT','SUN')), '음식점 주말 1,000P 적립(건당 2만원 이상)', 'Point Plan 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석2 SHOPPING+(CR047): 온라인쇼핑10%(통합한도)와 오프라인쇼핑10% 중 백화점·마트·편의점만
-- (창고형할인매장·슈퍼마켓·프리미엄아울렛·잡화는 업종 밖), 스타벅스→커피전문점 일반화 10%.
-- 4대PAY 추가할인(결제수단조건부)과 주유(리터당)는 제외.
update card_products set annual_fee = 10000, product_version = '2026-C1a-12079', data_status = 'CALCULABLE' where product_key = 'CR047';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint; v_t_coffee bigint;
	v_cat_online bigint; v_cat_department bigint; v_cat_mart bigint; v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_online_t3 bigint;
	v_cap_offline_t1 bigint; v_cap_offline_t2 bigint; v_cap_offline_t3 bigint; v_cap_coffee bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR047';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 700000, '70만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1200000, '120만원 이상') returning id into v_t3;
	v_t_coffee := v_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '온라인쇼핑 한도(30만원)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 8000, '온라인쇼핑 한도(70만원)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 16000, '온라인쇼핑 한도(120만원)') returning id into v_cap_online_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '오프라인쇼핑 통합 한도(30만원)') returning id into v_cap_offline_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 12000, '오프라인쇼핑 통합 한도(70만원)') returning id into v_cap_offline_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 24000, '오프라인쇼핑 통합 한도(120만원)') returning id into v_cap_offline_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '커피전문점 한도') returning id into v_cap_coffee;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.10, null, '온라인쇼핑 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.10, null, '온라인쇼핑 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_online_t3, 'DISCOUNT_RATE', 0.10, null, '온라인쇼핑 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t1, v_cap_offline_t1, 'DISCOUNT_RATE', 0.10, null, '백화점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t2, v_cap_offline_t2, 'DISCOUNT_RATE', 0.10, null, '백화점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t3, v_cap_offline_t3, 'DISCOUNT_RATE', 0.10, null, '백화점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_offline_t1, 'DISCOUNT_RATE', 0.10, null, '마트 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_offline_t2, 'DISCOUNT_RATE', 0.10, null, '마트 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_offline_t3, 'DISCOUNT_RATE', 0.10, null, '마트 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_offline_t1, 'DISCOUNT_RATE', 0.10, null, '편의점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_offline_t2, 'DISCOUNT_RATE', 0.10, null, '편의점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_offline_t3, 'DISCOUNT_RATE', 0.10, null, '편의점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t_coffee, v_cap_coffee, 'DISCOUNT_RATE', 0.10, 10000, '커피전문점 10% 청구할인', '카드의정석2 SHOPPING+ 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- THE SHOPPER(CR051): 기본적립1%(전가맹점,무한도) + 특별적립(온라인몰5%/럭셔리→백화점만5%/라이프→편의점만3%,
-- 통합12만점 간소화). 패션(브랜드한정)·간편결제·해외·멤버십구독·더라운지·주차할인·무이자전환은 제외.
update card_products set annual_fee = 200000, product_version = '2026-C1a-15365', data_status = 'CALCULABLE' where product_key = 'CR051';
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_online bigint; v_cat_department bigint; v_cat_convenience bigint;
	v_cap_base bigint; v_cap_special bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR051';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본적립 1% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 120000, '특별적립 통합 한도(12만점)') returning id into v_cap_special;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 포인트(기본적립)', 'THE SHOPPER 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_tier_id, v_cap_special, 'POINT_RATE', 0.05, 1.0, '온라인몰 5% 포인트(특별적립)', 'THE SHOPPER 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_tier_id, v_cap_special, 'POINT_RATE', 0.05, 1.0, '백화점 5% 포인트(특별적립)', 'THE SHOPPER 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_special, 'POINT_RATE', 0.03, 1.0, '편의점 3% 포인트(특별적립)', 'THE SHOPPER 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 디지로카 Las Vegas(CR052): 금액구간별 0.5~2.0% 중 대표구간(1.0%)으로 간소화, 통합 월10만원 한도,
-- 실적조건 없음, 전가맹점.
update card_products set annual_fee = 20000, product_version = '2026-C1a-13382', data_status = 'CALCULABLE' where product_key = 'CR052';
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR052';
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 100000, '전가맹점 결제일할인 통합 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.01, jsonb_build_object('allMerchants', true), '전가맹점 1.0% 결제일할인', '디지로카 Las Vegas 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 알파벳카드H(CR054): 기본혜택(교육·병원 10%) 중 병원→약국만 반영(교육·병원은 업종 밖),
-- 영역별+통합 한도는 통합 한도만 반영. 추가혜택(통신·관리비·가스)은 업종 밖이라 제외. 심의필 미확인(NULL).
update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR054';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_pharmacy bigint; v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR054';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 400000, '40~80만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 800000, '80~120만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1200000, '120만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '기본혜택 통합 한도(40~80만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '기본혜택 통합 한도(80~120만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 35000, '기본혜택 통합 한도(120만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_pharmacy, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.10, '약국 10% 청구할인', '알파벳카드H 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.10, '약국 10% 청구할인', '알파벳카드H 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.10, '약국 10% 청구할인', '알파벳카드H 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한 모두의카드(K-패스 신한카드)(CR055): 대중교통(K-패스 교통혜택)은 범위 밖이라 제외.
-- 커피전문점·약국·편의점만 반영(올리브영·OTT·통신·배달앱은 업종 밖, 5개 간편결제 5%는 결제수단조건부).
update card_products set annual_fee = 10000, product_version = '2026-C1a-14743', data_status = 'CALCULABLE' where product_key = 'CR055';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_cafe bigint; v_cat_pharmacy bigint; v_cat_convenience bigint;
	v_cap_t1 bigint; v_cap_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR055';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30~60만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 600000, '60만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 7000, '간편결제+생활 할인 통합 한도(30~60만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 15000, '간편결제+생활 할인 통합 한도(60만원 이상)') returning id into v_cap_t2;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 20000, 60000, '커피전문점 5% 결제일할인', '신한 모두의카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 20000, 60000, '커피전문점 5% 결제일할인', '신한 모두의카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 20000, 60000, '약국 5% 결제일할인', '신한 모두의카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 20000, 60000, '약국 5% 결제일할인', '신한 모두의카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 20000, 60000, '편의점 5% 결제일할인', '신한 모두의카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 20000, 60000, '편의점 5% 결제일할인', '신한 모두의카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;
