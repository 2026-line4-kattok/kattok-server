-- 8차 배치 10장: 카카오뱅크 프렌즈 체크카드(CK023), 신한카드 Hey Young 체크(CK026),
-- IBK나라사랑카드(CK027), 신한 모두의카드 체크(CK030), 위비트래블 체크카드(CK031),
-- 현대카드M CHECK(CK034), 카드의정석 EVERY CHECK(CK035), 신한카드 SOL트래블J 체크(CK037),
-- 달달 하나 체크카드(CK040), K-패스 삼성체크카드(CK043).
-- 여러 레이어(기본+추가/All in One 등)가 중첩된 혜택은 대표 레이어 하나로 간소화해 반영한다.

update card_products set annual_fee = 0, product_version = '26-1376', data_status = 'CALCULABLE' where product_key = 'CK023';
update card_products set annual_fee = 0, product_version = '20260729-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK026';
update card_products set annual_fee = 0, product_version = '2026-0088', data_status = 'CALCULABLE' where product_key = 'CK027';
update card_products set annual_fee = 0, product_version = '20260922-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK030';
update card_products set annual_fee = 0, product_version = '2024-C02926', data_status = 'CALCULABLE' where product_key = 'CK031';
update card_products set annual_fee = 2000, data_status = 'CALCULABLE' where product_key = 'CK034';
update card_products set annual_fee = 0, product_version = '2023-C01499', data_status = 'CALCULABLE' where product_key = 'CK035';
update card_products set annual_fee = 0, product_version = '20260821-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK037';
update card_products set annual_fee = 0, product_version = 'L-26-0413', data_status = 'CALCULABLE' where product_key = 'CK040';
update card_products set annual_fee = 0, product_version = 'S3893-24-1103', data_status = 'CALCULABLE' where product_key = 'CK043';

-- 카카오뱅크 프렌즈 체크카드(CK023): 기본 0.2% + 주말 추가 0.2%(합산 0.4%로 표현).
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK023';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 가맹점 캐시백 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.002, jsonb_build_object('allMerchants', true, 'daysOfWeek', jsonb_build_array('MON','TUE','WED','THU','FRI')), '국내외 가맹점 0.2% 캐시백(평일)', '카카오뱅크 프렌즈 체크카드 상품 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.004, jsonb_build_object('allMerchants', true, 'daysOfWeek', jsonb_build_array('SAT','SUN')), '국내외 가맹점 0.4% 캐시백(주말, 기본+추가)', '카카오뱅크 프렌즈 체크카드 상품 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Hey Young 체크(CK026): 일상 서비스 중 편의점·커피·패스트푸드·온라인쇼핑만 반영.
-- 대중교통/통신/영화/배달/생활가맹점/OTT/간편결제/해외는 업종 밖·결제수단조건부·구독이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_convenience bigint; v_cat_cafe bigint; v_cat_restaurant bigint; v_cat_online bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK026';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '일상 서비스 통합 캐시백 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_id, 'DISCOUNT_FIXED', 1000, 10000, '편의점 1,000원 캐시백', '신한카드 Hey Young 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'DISCOUNT_FIXED', 1000, 10000, '카페 1,000원 캐시백', '신한카드 Hey Young 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_id, 'DISCOUNT_FIXED', 1000, 10000, '패스트푸드 1,000원 캐시백', '신한카드 Hey Young 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_tier_id, v_cap_id, 'DISCOUNT_FIXED', 1000, 20000, '온라인쇼핑 1,000원 캐시백', '신한카드 Hey Young 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- IBK나라사랑카드(CK027): '나라'(편의점)/'사랑'(외식·택시)/파워업 All in One(병원약국·카페) 서비스 중
-- 8개 업종에 해당하는 항목만 반영. 대중교통/이동통신/영화/구독/쇼핑(간편결제)은 업종 밖·결제수단조건부,
-- 특별 프로모션 쿠폰류(카카오T/맘스터치 등)는 1회성/분기 1회라 제외.
do $$
declare
	v_product_id bigint; v_tier_nara bigint; v_tier_sarang bigint; v_tier_allinone bigint;
	v_cat_convenience bigint; v_cat_taxi bigint; v_cat_restaurant bigint; v_cat_pharmacy bigint; v_cat_cafe bigint;
	v_cap_convenience bigint; v_cap_taxi bigint; v_cap_restaurant bigint; v_cap_pharmacy bigint; v_cap_cafe bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK027';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 80000, '8만원 이상(나라)') returning id into v_tier_nara;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 200000, '20만원 이상(사랑)') returning id into v_tier_sarang;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 250000, '25만원 이상(All in One)') returning id into v_tier_allinone;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 1000, 2, '편의점 청구할인 한도(나라)') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 2000, 2, '택시 청구할인 한도(사랑)') returning id into v_cap_taxi;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 40000, 1, '외식(아웃백) 현장할인 한도(사랑)') returning id into v_cap_restaurant;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description) values (v_product_id, 'MONTHLY', 2, '병원/약국 할인 한도(All in One)') returning id into v_cap_pharmacy;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description) values (v_product_id, 'MONTHLY', 2, '카페 할인 한도(All in One)') returning id into v_cap_cafe;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_tier_nara, v_cap_convenience, 'DISCOUNT_RATE', 0.10, '편의점(CU/GS25/Emart24) 10% 청구할인(나라)', 'IBK나라사랑카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_tier_sarang, v_cap_taxi, 'DISCOUNT_RATE', 0.10, '택시(카카오T) 10% 청구할인(사랑)', 'IBK나라사랑카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_sarang, v_cap_restaurant, 'DISCOUNT_RATE', 0.20, '외식(아웃백스테이크하우스) 20% 현장할인(사랑)', 'IBK나라사랑카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_tier_allinone, v_cap_pharmacy, 'DISCOUNT_RATE', 0.10, '병원/약국 10% 청구할인(All in One)', 'IBK나라사랑카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_allinone, v_cap_cafe, 'DISCOUNT_RATE', 0.10, '카페 10% 청구할인(All in One)', 'IBK나라사랑카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한 모두의카드 체크(CK030): 생활 부문(편의점·커피·병원약국) 2%만 반영. 대중교통/간편결제/올리브영/OTT/배달앱은
-- 업종 밖·결제수단조건부·제외 정책 적용.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_convenience bigint; v_cat_cafe bigint; v_cat_pharmacy bigint;
	v_cap_t1 bigint; v_cap_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK030';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상 50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '생활 부문 통합 할인한도(20~50만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '생활 부문 통합 할인한도(50만원 이상)') returning id into v_cap_t2;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.02, 10000, '편의점 2% 결제일 할인', '신한 모두의카드 체크 서비스 유의사항', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.02, 10000, '편의점 2% 결제일 할인', '신한 모두의카드 체크 서비스 유의사항', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.02, 10000, '커피전문점 2% 결제일 할인', '신한 모두의카드 체크 서비스 유의사항', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.02, 10000, '커피전문점 2% 결제일 할인', '신한 모두의카드 체크 서비스 유의사항', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.02, 10000, '병원/약국 2% 결제일 할인', '신한 모두의카드 체크 서비스 유의사항', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.02, 10000, '병원/약국 2% 결제일 할인', '신한 모두의카드 체크 서비스 유의사항', 'VERIFIED', now(), 1, current_date);
end $$;

-- 위비트래블 체크카드(CK031): 푸드(커피)·일상(온라인쇼핑) 캐시백만 반영. 해외/쇼핑(배달·간편결제)/대중교통/
-- 이동통신은 해외·결제수단조건부·업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_cafe bigint; v_cat_online bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint; v_cap_cafe_t3 bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_online_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK031';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 700000, '70만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '푸드(커피) 캐시백 한도(20만원 이상)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '푸드(커피) 캐시백 한도(50만원 이상)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 9000, '푸드(커피) 캐시백 한도(70만원 이상)') returning id into v_cap_cafe_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '일상(온라인쇼핑) 캐시백 한도(20만원 이상)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '일상(온라인쇼핑) 캐시백 한도(50만원 이상)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 9000, '일상(온라인쇼핑) 캐시백 한도(70만원 이상)') returning id into v_cap_online_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_RATE', 0.05, '스타벅스 사이렌오더 5% 캐시백(푸드)', '위비트래블 체크카드 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_RATE', 0.05, '스타벅스 사이렌오더 5% 캐시백(푸드)', '위비트래블 체크카드 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_cafe_t3, 'DISCOUNT_RATE', 0.05, '스타벅스 사이렌오더 5% 캐시백(푸드)', '위비트래블 체크카드 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.05, '쿠팡/무신사/29CM 5% 캐시백(일상)', '위비트래블 체크카드 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.05, '쿠팡/무신사/29CM 5% 캐시백(일상)', '위비트래블 체크카드 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_online_t3, 'DISCOUNT_RATE', 0.05, '쿠팡/무신사/29CM 5% 캐시백(일상)', '위비트래블 체크카드 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드M CHECK(CK034): 당월 100만원 미만 0.5% / 100만원 이상 1% M포인트 적립(allMerchants, 중복 적용).
do $$
declare v_product_id bigint; v_tier_id bigint; v_cap_low bigint; v_cap_high bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK034';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 1000000, '100만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 0.5% — 한도 없음') returning id into v_cap_low;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 1.0%(100만원 이상) — 한도 없음') returning id into v_cap_high;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_low, 'POINT_RATE', 0.005, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.5% M포인트(당월 100만원 미만)', '현대카드M CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_tier_id, v_cap_high, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 1% M포인트(당월 100만원 이상)', '현대카드M CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석 EVERY CHECK(CK035, 우리): 기본적립 0.2% + 전월실적별 추가적립(고정금액). 특별적립(4대 일상영역 중
-- 최다이용 1곳 자동선정)은 동적 산정 방식이라 고정 규칙으로 표현할 수 없어 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cap_base bigint; v_cap_add bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK035';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 600000, '60만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 900000, '90만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 0.2% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '추가 적립 — 한도 없음') returning id into v_cap_add;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.002, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.2% 적립(기본)', '카드의정석 EVERY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_t1, v_cap_add, 'POINT_FIXED', 1000, jsonb_build_object('allMerchants', true), '전월실적 30만원 이상 1,000원 추가 적립', '카드의정석 EVERY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t2, v_cap_add, 'POINT_FIXED', 2000, jsonb_build_object('allMerchants', true), '전월실적 60만원 이상 2,000원 추가 적립', '카드의정석 EVERY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t3, v_cap_add, 'POINT_FIXED', 3000, jsonb_build_object('allMerchants', true), '전월실적 90만원 이상 3,000원 추가 적립', '카드의정석 EVERY CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 SOL트래블J 체크(CK037): 국내 4개 편의점 5%만 반영. 대중교통과 일본여행서비스(돈키호테/스타벅스)는
-- 업종 밖·해외 특정 매장이라 제외.
do $$
declare v_product_id bigint; v_tier_id bigint; v_cat_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK037';
	select id into v_cat_id from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'YEARLY', 40000, '편의점 할인 연 한도(반기별 2만원)') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_id, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, '국내 4개 편의점 5% 결제일 할인', '신한카드 SOL트래블J 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 달달 하나 체크카드(CK040): 쇼핑(백화점·대형마트·편의점)·교통(택시)·커피전문점만 반영. 간편결제/딜리버리/
-- 주유·EV충전/대중교통/생활비/구독은 결제수단조건부·제외정책·업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_department bigint; v_cat_mart bigint; v_cat_convenience bigint; v_cat_taxi bigint; v_cat_cafe bigint;
	v_cap_shop_t1 bigint; v_cap_shop_t2 bigint; v_cap_shop_t3 bigint;
	v_cap_taxi_t1 bigint; v_cap_taxi_t2 bigint; v_cap_taxi_t3 bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint; v_cap_cafe_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK040';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 700000, '70만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1000000, '100만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '쇼핑 영역 통합 한도(30만원 이상)') returning id into v_cap_shop_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '쇼핑 영역 통합 한도(70만원 이상)') returning id into v_cap_shop_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '쇼핑 영역 통합 한도(100만원 이상)') returning id into v_cap_shop_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '교통(택시) 영역 한도(30만원 이상)') returning id into v_cap_taxi_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '교통(택시) 영역 한도(70만원 이상)') returning id into v_cap_taxi_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '교통(택시) 영역 한도(100만원 이상)') returning id into v_cap_taxi_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '커피전문점 영역 한도(30만원 이상)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '커피전문점 영역 한도(70만원 이상)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '커피전문점 영역 한도(100만원 이상)') returning id into v_cap_cafe_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_department, v_t1, v_cap_shop_t1, 'POINT_RATE', 0.05, '백화점 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t2, v_cap_shop_t2, 'POINT_RATE', 0.05, '백화점 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_t3, v_cap_shop_t3, 'POINT_RATE', 0.05, '백화점 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_shop_t1, 'POINT_RATE', 0.05, '대형마트 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_shop_t2, 'POINT_RATE', 0.05, '대형마트 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_shop_t3, 'POINT_RATE', 0.05, '대형마트 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_shop_t1, 'POINT_RATE', 0.05, '편의점 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_shop_t2, 'POINT_RATE', 0.05, '편의점 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_shop_t3, 'POINT_RATE', 0.05, '편의점 5% 하나머니(쇼핑)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_taxi_t1, 'POINT_RATE', 0.05, '택시(카카오T) 5% 하나머니(교통)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_taxi_t2, 'POINT_RATE', 0.05, '택시(카카오T) 5% 하나머니(교통)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t3, v_cap_taxi_t3, 'POINT_RATE', 0.05, '택시(카카오T) 5% 하나머니(교통)', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'POINT_RATE', 0.05, '커피전문점 5% 하나머니', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'POINT_RATE', 0.05, '커피전문점 5% 하나머니', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_cafe_t3, 'POINT_RATE', 0.05, '커피전문점 5% 하나머니', '달달 하나 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- K-패스 삼성체크카드(CK043): 편의점·커피전문점 고정 캐시백만 반영(제과는 간소화를 위해 제외).
-- 대중교통/이동통신/CGV는 업종 밖.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK043';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 2500, 3, '편의점/커피전문점/제과 통합 캐시백 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_id, 'DISCOUNT_FIXED', 1000, 10000, '편의점 1,000원 캐시백', 'K-패스 삼성체크카드 안내사항', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'DISCOUNT_FIXED', 1000, 10000, '커피전문점 1,000원 캐시백', 'K-패스 삼성체크카드 안내사항', 'VERIFIED', now(), 1, current_date);
end $$;
