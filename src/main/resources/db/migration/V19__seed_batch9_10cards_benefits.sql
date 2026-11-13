-- 9차 배치 10장: 직장인보너스체크카드(CK044), 신한카드 Point Plan 체크 캐릭터형(짱구)(CK045),
-- I-포켓몬(체크)(CK047), 현대카드X CHECK(CK050), 신한카드 Pick I 체크 캐릭터형(한교동)(CK053),
-- 트래블GO 체크카드(CK055), 스타플러스 체크카드(CK057), 카드의정석 EVERYDAY CHECK(CK058),
-- QWERTY 우리카드 체크(CK059), 모두의 일상 체크카드(CK060).
-- 체크카드는 여신금융협회 심의필 대신 각 카드사 준법감시인/준법심의 코드를 product_version에 기록한다.
-- 일부 상품설명서에 심의필 번호가 없어 product_version을 NULL로 둔 카드도 있다(CK050).

update card_products set annual_fee = 0, product_version = '241119-05862-PIF', data_status = 'CALCULABLE' where product_key = 'CK044';
update card_products set annual_fee = 0, product_version = '20241101-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK045';
update card_products set annual_fee = 0, product_version = '2026-5410', data_status = 'CALCULABLE' where product_key = 'CK047';
update card_products set annual_fee = 2000, data_status = 'CALCULABLE' where product_key = 'CK050';
update card_products set annual_fee = 0, product_version = '20260619-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK053';
update card_products set annual_fee = 0, product_version = 'L-25-0564', data_status = 'CALCULABLE' where product_key = 'CK055';
update card_products set annual_fee = 0, product_version = '230608-02049-PIF', data_status = 'CALCULABLE' where product_key = 'CK057';
update card_products set annual_fee = 0, product_version = '2026-C01488', data_status = 'CALCULABLE' where product_key = 'CK058';
update card_products set annual_fee = 0, product_version = '2026-C01865', data_status = 'CALCULABLE' where product_key = 'CK059';
update card_products set annual_fee = 0, product_version = 'L-25-0423', data_status = 'CALCULABLE' where product_key = 'CK060';

-- 직장인보너스체크카드(CK044, KB국민): 아웃백(음식점) 10% + 백화점 5%만 반영. 국세/지방세/보험료/이동통신/
-- 대중교통/주유/렌터카/놀이공원은 업종 밖, 해피포인트(SPC 가맹점) 적립은 8개 업종에 깔끔히 대응되지 않아 제외.
-- 두 혜택은 전월실적 구간별 "월간 통합할인한도"를 공유한다(parent 없이 같은 cap_group_id로 표현).
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint; v_t4 bigint;
	v_cat_restaurant bigint; v_cat_department bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint; v_cap_t4 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK044';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 300000, '30만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 500000, '50만원 이상') returning id into v_t3;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 4, 1000000, '100만원 이상') returning id into v_t4;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '월간 통합할인한도(20만원 이상)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '월간 통합할인한도(30만원 이상)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '월간 통합할인한도(50만원 이상)') returning id into v_cap_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, '월간 통합할인한도(100만원 이상)') returning id into v_cap_t4;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스 10% 환급할인', '직장인보너스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스 10% 환급할인', '직장인보너스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스 10% 환급할인', '직장인보너스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t4, v_cap_t4, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스 10% 환급할인', '직장인보너스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, 50000, 100000, '롯데/신세계/현대백화점 5% 환급할인', '직장인보너스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t4, v_cap_t4, 'DISCOUNT_RATE', 0.05, 50000, 100000, '롯데/신세계/현대백화점 5% 환급할인', '직장인보너스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Point Plan 체크 캐릭터형(짱구)(CK045): 편의점 5% 적립 + 일상 생활비 적립(전가맹점, 편의점 제외)만 반영.
-- 일상 생활비 적립은 건당 결제금액별 0.2~1% 차등 요율을 대표 구간(2만~10만원, 0.4%) 하나로 간소화했다.
-- 주말 배달앱 포인트 적립은 배달앱이라 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_convenience bigint;
	v_cap_convenience bigint; v_cap_all_t1 bigint; v_cap_all_t2 bigint; v_cap_all_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK045';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상 50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상 80만원 미만') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 800000, '80만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '편의점 포인트 적립 한도') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '일상 생활비 적립 통합 한도(20~50만원)') returning id into v_cap_all_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '일상 생활비 적립 통합 한도(50~80만원)') returning id into v_cap_all_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 15000, '일상 생활비 적립 통합 한도(80만원 이상)') returning id into v_cap_all_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_convenience, v_t1, v_cap_convenience, 'POINT_RATE', 0.05, 1.0, '편의점(CU/GS25/세븐일레븐) 5% 포인트 적립', '신한카드 Point Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_t1, v_cap_all_t1, 'POINT_RATE', 0.004, 1.0, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_convenience)), '일상 생활비 적립(건당 2만~10만원 구간 요율 대표 적용)', '신한카드 Point Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t2, v_cap_all_t2, 'POINT_RATE', 0.004, 1.0, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_convenience)), '일상 생활비 적립(건당 2만~10만원 구간 요율 대표 적용)', '신한카드 Point Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t3, v_cap_all_t3, 'POINT_RATE', 0.004, 1.0, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_convenience)), '일상 생활비 적립(건당 2만~10만원 구간 요율 대표 적용)', '신한카드 Point Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- I-포켓몬(체크)(CK047): 기본 0.2% 청구할인(전가맹점) + 온라인쇼핑/커피/택시 0.6%(기본할인과 중복불가,
-- 대중교통은 업종 밖이라 제외)만 반영. 실적·한도 조건 없음.
do $$
declare
	v_product_id bigint;
	v_cat_online bigint; v_cat_cafe bigint; v_cat_taxi bigint;
	v_cap_base bigint; v_cap_online bigint; v_cap_cafe bigint; v_cap_taxi bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK047';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 청구할인 0.2% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '온라인쇼핑 0.6% — 한도 없음') returning id into v_cap_online;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '커피 0.6% — 한도 없음') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '택시 0.6% — 한도 없음') returning id into v_cap_taxi;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'DISCOUNT_RATE', 0.002, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_online, v_cat_cafe, v_cat_taxi)), '국내외 가맹점 0.2% 청구할인(기본)', 'IBK I-포켓몬(체크) 안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_cap_online, 'DISCOUNT_RATE', 0.006, '온라인쇼핑(쿠팡/G마켓/옥션/11번가) 0.6% 청구할인', 'IBK I-포켓몬(체크) 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_cap_cafe, 'DISCOUNT_RATE', 0.006, '커피전문점 0.6% 청구할인', 'IBK I-포켓몬(체크) 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_cap_taxi, 'DISCOUNT_RATE', 0.006, '택시 0.6% 청구할인', 'IBK I-포켓몬(체크) 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드X CHECK(CK050): 당월 100만원 미만 0.3% / 100만원 이상 0.6% X캐시백 적립(allMerchants, 중복 적용).
-- CK034(현대카드M CHECK)와 동일 구조.
do $$
declare v_product_id bigint; v_tier_id bigint; v_cap_low bigint; v_cap_high bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK050';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 1000000, '100만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 0.3% — 한도 없음') returning id into v_cap_low;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 0.6%(100만원 이상) — 한도 없음') returning id into v_cap_high;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_low, 'POINT_RATE', 0.003, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.3% X캐시백(당월 100만원 미만)', '현대카드X CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_high, 'POINT_RATE', 0.006, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.6% X캐시백(당월 100만원 이상)', '현대카드X CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Pick I 체크 캐릭터형(한교동)(CK053): 공통서비스(편의점·커피 10%)만 반영. 해외이용 적립은 해외라
-- 제외, 디지털구독(Pick I)과 One Pick 온라인쇼핑몰(최다이용 가맹점 자동선정)은 각각 콘텐츠·동적 산정이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_convenience bigint; v_cap_cafe bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK053';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 3000, 3, '편의점 포인트 적립 한도(일1회 월3회)') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 3000, 3, '커피 포인트 적립 한도(일1회 월3회)') returning id into v_cap_cafe;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_convenience, 'POINT_RATE', 0.10, '편의점 10% 포인트 적립(공통서비스)', '신한카드 Pick I 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'POINT_RATE', 0.10, '커피전문점 10% 포인트 적립(공통서비스)', '신한카드 Pick I 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 트래블GO 체크카드(CK055, 하나): 편의점·커피/베이커리 하나머니 적립 5%만 반영. 대중교통/구독/생활비(통신·공과금)는
-- 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_convenience bigint; v_cap_cafe bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK055';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 5000, 5, '편의점 하나머니 적립 한도(월5회)') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 5000, 5, '커피/베이커리 하나머니 적립 한도(월5회)') returning id into v_cap_cafe;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_convenience, 'POINT_RATE', 0.05, 1.0, 10000, '편의점(CU/GS25/세븐일레븐/이마트24) 5% 하나머니 적립', '트래블GO 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'POINT_RATE', 0.05, 1.0, 7000, '커피/베이커리(스타벅스/커피빈/파리바게뜨/뚜레쥬르) 5% 하나머니 적립', '트래블GO 체크카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 스타플러스 체크카드(CK057, KB국민): 커피 10% + 편의점 5%만 반영. 모바일/문화/뷰티/구독/배달/이동통신/영화/
-- 놀이공원/해외/공항라운지는 업종 밖·배달앱이라 제외. 월 한도는 "월 N원 이용분까지"를 요율로 환산해 저장.
-- 커피 할인은 전월실적 조건 없이 제공되고, 편의점만 전월실적 20만원 이상을 요구한다.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_convenience bigint;
	v_cap_cafe bigint; v_cap_convenience bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK057';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '커피 할인 한도(월 3만원 이용분)') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '편의점 할인 한도(월 4만원 이용분)') returning id into v_cap_convenience;
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_cafe, v_cap_cafe, 'DISCOUNT_RATE', 0.10, '스타벅스(사이렌오더 포함)/커피빈 10% 할인(실적조건 없음)', 'KB국민 스타플러스 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_convenience, v_tier_id, v_cap_convenience, 'DISCOUNT_RATE', 0.05, 'GS25/CU 5% 할인', 'KB국민 스타플러스 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석 EVERYDAY CHECK(CK058, 우리): 기본적립 0.2%(전가맹점) + 생활소비 1% 특별적립(커피/쇼핑/교통(택시)).
-- 주유는 업종 밖, 우리WON페이 추가적립은 간편결제 전용이라 제외. 특별적립은 통합 월한도(parent)와 영역별
-- 월한도(child)를 함께 반영하며, 두 한도 모두 제외된 영역(우리WON페이·주유)과 공유된다.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_cafe bigint; v_cat_mart bigint; v_cat_taxi bigint;
	v_cap_base bigint;
	v_parent_t1 bigint; v_cap_cafe_t1 bigint; v_cap_mart_t1 bigint; v_cap_taxi_t1 bigint;
	v_parent_t2 bigint; v_cap_cafe_t2 bigint; v_cap_mart_t2 bigint; v_cap_taxi_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK058';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 600000, '60만원 이상') returning id into v_t2;

	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 0.2% — 한도 없음') returning id into v_cap_base;

	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '특별적립 통합 월한도(30만원 이상)') returning id into v_parent_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 3000, v_parent_t1, '커피 월한도(30만원 이상)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 3000, v_parent_t1, '쇼핑 월한도(30만원 이상)') returning id into v_cap_mart_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 3000, v_parent_t1, '교통(택시) 월한도(30만원 이상)') returning id into v_cap_taxi_t1;

	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '특별적립 통합 월한도(60만원 이상)') returning id into v_parent_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 6000, v_parent_t2, '커피 월한도(60만원 이상)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 6000, v_parent_t2, '쇼핑 월한도(60만원 이상)') returning id into v_cap_mart_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 6000, v_parent_t2, '교통(택시) 월한도(60만원 이상)') returning id into v_cap_taxi_t2;

	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.002, jsonb_build_object('allMerchants', true), '국내/해외 가맹점 0.2% 적립(기본)', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);

	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'POINT_RATE', 0.01, 10000, '커피(스타벅스/폴바셋/투썸플레이스/매머드커피) 1% 특별적립', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'POINT_RATE', 0.01, 10000, '커피(스타벅스/폴바셋/투썸플레이스/매머드커피) 1% 특별적립', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_mart_t1, 'POINT_RATE', 0.01, 10000, '쇼핑(이마트/롯데마트/홈플러스/GS더프레시/롯데슈퍼) 1% 특별적립', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_mart_t2, 'POINT_RATE', 0.01, 10000, '쇼핑(이마트/롯데마트/홈플러스/GS더프레시/롯데슈퍼) 1% 특별적립', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_taxi_t1, 'POINT_RATE', 0.01, 10000, '택시 1% 특별적립(교통)', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_taxi_t2, 'POINT_RATE', 0.01, 10000, '택시 1% 특별적립(교통)', '카드의정석 EVERYDAY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- QWERTY 우리카드 체크(CK059): SHOPPING(온라인쇼핑 쿠팡/무신사/지그재그)·EAT(커피 스타벅스 사이렌오더)만
-- 반영. 간편결제·안경/렌즈는 업종 밖/결제수단조건부, 배달/주문은 배달앱, PLAY·LIFE·해외·어학시험은 업종
-- 밖이라 제외. "매출 건당 최대 1천원까지 캐시백"은 5% 기준 건당 한도 2만원으로 환산해 저장.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_online bigint; v_cat_cafe bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_online_t3 bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint; v_cap_cafe_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK059';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상 50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상 70만원 미만') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 700000, '70만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, 'SHOPPING 캐시백 한도(20~50만원)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, 'SHOPPING 캐시백 한도(50~70만원)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, 'SHOPPING 캐시백 한도(70만원 이상)') returning id into v_cap_online_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, 'EAT 캐시백 한도(20~50만원)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, 'EAT 캐시백 한도(50~70만원)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, 'EAT 캐시백 한도(70만원 이상)') returning id into v_cap_cafe_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.05, 20000, '온라인쇼핑(쿠팡/무신사/지그재그) 5% 캐시백(SHOPPING)', 'QWERTY 우리카드 체크 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.05, 20000, '온라인쇼핑(쿠팡/무신사/지그재그) 5% 캐시백(SHOPPING)', 'QWERTY 우리카드 체크 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_online_t3, 'DISCOUNT_RATE', 0.05, 20000, '온라인쇼핑(쿠팡/무신사/지그재그) 5% 캐시백(SHOPPING)', 'QWERTY 우리카드 체크 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_RATE', 0.05, 20000, '스타벅스 사이렌오더 5% 캐시백(EAT)', 'QWERTY 우리카드 체크 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_RATE', 0.05, 20000, '스타벅스 사이렌오더 5% 캐시백(EAT)', 'QWERTY 우리카드 체크 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_cafe_t3, 'DISCOUNT_RATE', 0.05, 20000, '스타벅스 사이렌오더 5% 캐시백(EAT)', 'QWERTY 우리카드 체크 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 모두의 일상 체크카드(CK060, 하나): 일반결제 기준 국내 전가맹점 0.3% 하나머니 적립만 반영(실적·횟수 제한 없음).
-- 하나페이/각종 페이 등록결제 0.4%는 간편결제 전용 우대라 제외, 해외 전가맹점 0.4%는 해외라 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK060';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 전가맹점 0.3% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'POINT_RATE', 0.003, 1.0, jsonb_build_object('allMerchants', true), '국내 전가맹점 0.3% 하나머니 적립(일반결제)', '모두의 일상 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;
