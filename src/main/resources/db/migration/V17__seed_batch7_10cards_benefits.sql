-- 7차 배치 10장: 토스뱅크 K-패스 체크카드(CK008), 하나 나라사랑카드(CK010),
-- 토스뱅크 체크카드(CK012), KB K-패스체크카드(CK013), 트래블로그 체크카드(CK016),
-- 신한카드 Deep Dream 체크(CK017), 카드의정석 오하CHECK(CK019),
-- 신한카드 나라사랑카드 체크(CK020), 토심이 첵첵 체크카드(CK021), 노리체크카드(CK022).
-- CK008은 공식 PDF가 없는 상품으로, 카드목록 출처(토스뱅크 공식 홈페이지)에 안내된 대로
-- 동일 발급사의 "토스뱅크 체크카드 스위치 캐시백 어디서나 모드"와 동일한 혜택을 적용한다.
-- 체크카드는 여신금융협회 심의필 대신 각 카드사 준법감시인/준법심의 코드를 product_version에 기록한다.

update card_products set annual_fee = 0 where product_key = 'CK008';
update card_products set annual_fee = 0, product_version = 'L-26-2054', data_status = 'CALCULABLE' where product_key = 'CK010';
update card_products set annual_fee = 0, product_version = '2025-설명서-72', data_status = 'CALCULABLE' where product_key = 'CK012';
update card_products set annual_fee = 0, product_version = '240215-00555-PIF', data_status = 'CALCULABLE' where product_key = 'CK013';
update card_products set annual_fee = 0, product_version = 'L-25-0563', data_status = 'CALCULABLE' where product_key = 'CK016';
update card_products set annual_fee = 0, product_version = '20261008-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK017';
update card_products set annual_fee = 0, product_version = '2023-C01387', data_status = 'CALCULABLE' where product_key = 'CK019';
update card_products set annual_fee = 0, product_version = '20260928-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK020';
update card_products set annual_fee = 0, product_version = '260522-01722-PIF', data_status = 'CALCULABLE' where product_key = 'CK021';
update card_products set annual_fee = 0, product_version = '241119-05862-PIF', data_status = 'CALCULABLE' where product_key = 'CK022';

-- 토스뱅크 K-패스 체크카드(CK008): 토스뱅크 체크카드 스위치 캐시백 "어디서나" 모드와 동일하게
-- 전가맹점 0.3% 캐시백만 반영. 대중교통 환급은 업종 밖이라 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK008';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '전가맹점 0.3% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.003, 334, jsonb_build_object('allMerchants', true), '전가맹점 0.3% 캐시백(어디서나 모드 가정)', '토스뱅크 체크카드 스위치 캐시백 시즌6 안내', 'VERIFIED', now(), 1, current_date);
	update card_products set data_status = 'CALCULABLE' where product_key = 'CK008';
end $$;

-- 하나 나라사랑카드(CK010): Easy(온라인쇼핑 20%) + Desire(군급여이체, 커피·레스토랑·패스트푸드·온라인쇼핑).
-- PX군마트/대중교통/국군콘도/어학시험/서점/광역교통/놀이공원은 업종 밖, 특정기념일 편의점 할인은 1회성이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_online bigint; v_cat_cafe bigint; v_cat_restaurant bigint;
	v_cap_easy_online bigint; v_cap_cafe bigint; v_cap_restaurant_big bigint; v_cap_restaurant_small bigint; v_cap_online_desire bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK010';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 100000, '10만원 이상(Desire)') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '쿠팡/네이버플러스 스토어 20%(Easy)') returning id into v_cap_easy_online;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '커피 20%(Desire)') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '레스토랑 20%(Desire)') returning id into v_cap_restaurant_big;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '패스트푸드 5%(Desire)') returning id into v_cap_restaurant_small;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '온라인쇼핑 10%(Desire)') returning id into v_cap_online_desire;
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_online, v_cap_easy_online, 'DISCOUNT_RATE', 0.20, '쿠팡/네이버플러스 스토어 20% 캐시백(Easy)', '하나 나라사랑카드(체크) 안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'DISCOUNT_RATE', 0.20, 10000, '스타벅스 20% 캐시백(Desire)', '하나 나라사랑카드(체크) 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_restaurant_big, 'DISCOUNT_RATE', 0.20, 10000, '아웃백/VIPS 20% 캐시백(Desire)', '하나 나라사랑카드(체크) 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_restaurant_small, 'DISCOUNT_RATE', 0.05, 10000, '패스트푸드 5% 캐시백(Desire)', '하나 나라사랑카드(체크) 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_tier_id, v_cap_online_desire, 'DISCOUNT_RATE', 0.10, 10000, '온라인쇼핑 10% 캐시백(Desire)', '하나 나라사랑카드(체크) 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 토스뱅크 체크카드(CK012): "어디서나" 모드 기준 전가맹점 0.3% 캐시백만 반영.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK012';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '전가맹점 0.3% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.003, 334, jsonb_build_object('allMerchants', true), '전가맹점 0.3% 캐시백(어디서나 모드)', '토스뱅크 체크카드 스위치 캐시백 시즌6 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- KB K-패스체크카드(CK013): 생활서비스(커피·약국·편의점·패스트푸드) 1%만 반영. 대중교통/이동통신/영화는
-- 업종 밖, KB Pay 추가적립은 결제수단 조건부라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_pharmacy bigint; v_cat_convenience bigint; v_cat_restaurant bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK013';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '생활서비스 적립 통합 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 10000, '커피업종 1% 포인트리', 'KB국민 K-패스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 10000, '약국업종 1% 포인트리', 'KB국민 K-패스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 10000, '편의점업종 1% 포인트리', 'KB국민 K-패스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 10000, '패스트푸드업종 1% 포인트리', 'KB국민 K-패스체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 트래블로그 체크카드(CK016, 하나): 국내 가맹점 0.3% 하나머니 적립만 반영(실적·한도 없음).
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK016';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내 가맹점 0.3% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'POINT_RATE', 0.003, 1.0, jsonb_build_object('allMerchants', true), '국내 가맹점 0.3% 하나머니 적립', '트래블로그 체크카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Deep Dream 체크(CK017): 모두드림 기본 0.2% 마이신한포인트만 반영(실적·한도 없음).
-- 더해드림/챙겨드림(DREAM 영역 자동 최다이용 선정)은 특정 시점 지출 내역에 따라 동적으로 결정되는
-- 구조라 고정 규칙으로 표현할 수 없어 제외.
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK017';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 가맹점 0.2% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'POINT_RATE', 0.002, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.2% 마이신한포인트(모두드림)', '신한카드 Deep Dream 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석 오하CHECK(CK019, 우리): SHOPPING(온라인쇼핑)·EAT(커피) 그룹만 반영. LIFE(대중교통/이동통신)와
-- PLAY(안경·OTT·어학시험·공연티켓)는 업종 밖, 배달/간편결제는 기존 정책대로 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_online bigint; v_cat_cafe bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_online_t3 bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint; v_cap_cafe_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK019';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 700000, '70만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, 'SHOPPING 캐시백 한도(20만원 이상)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, 'SHOPPING 캐시백 한도(50만원 이상)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, 'SHOPPING 캐시백 한도(70만원 이상)') returning id into v_cap_online_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, 'EAT 캐시백 한도(20만원 이상)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, 'EAT 캐시백 한도(50만원 이상)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, 'EAT 캐시백 한도(70만원 이상)') returning id into v_cap_cafe_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.05, '온라인쇼핑 5% 캐시백(SHOPPING)', '카드의정석 오하CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.05, '온라인쇼핑 5% 캐시백(SHOPPING)', '카드의정석 오하CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_online_t3, 'DISCOUNT_RATE', 0.05, '온라인쇼핑 5% 캐시백(SHOPPING)', '카드의정석 오하CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_RATE', 0.05, '커피 5% 캐시백(EAT)', '카드의정석 오하CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_RATE', 0.05, '커피 5% 캐시백(EAT)', '카드의정석 오하CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_cafe_t3, 'DISCOUNT_RATE', 0.05, '커피 5% 캐시백(EAT)', '카드의정석 오하CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 나라사랑카드 체크(CK020): Life서비스 중 카카오T택시·편의점·커피·무신사(온라인쇼핑)만 반영.
-- 대중교통/광역교통/이동통신/도서/스포츠/배달의민족/OTT와 슈퍼쏠저(군 특화)는 업종 밖·제외 정책 적용.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_taxi bigint; v_cat_convenience bigint; v_cat_cafe bigint; v_cat_online bigint;
	v_cap_taxi bigint; v_cap_convenience bigint; v_cap_cafe bigint; v_cap_online bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK020';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 100000, '10만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '카카오T 택시 10% 캐시백 한도') returning id into v_cap_taxi;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '편의점 20% 캐시백 한도') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '커피 5% 캐시백 한도') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '무신사/29CM 10% 캐시백 한도') returning id into v_cap_online;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_taxi, v_tier_id, v_cap_taxi, 'DISCOUNT_RATE', 0.10, '카카오T 택시 10% 캐시백(Life)', '신한카드 나라사랑카드 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_convenience, 'DISCOUNT_RATE', 0.20, '편의점 20% 캐시백(Life)', '신한카드 나라사랑카드 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'DISCOUNT_RATE', 0.05, '커피전문점 5% 캐시백(Life)', '신한카드 나라사랑카드 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_tier_id, v_cap_online, 'DISCOUNT_RATE', 0.10, '무신사/29CM 10% 캐시백(Life)', '신한카드 나라사랑카드 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 토심이 첵첵 체크카드(CK021, KB국민 첵첵 체크카드 캐릭터형): 편의점·커피 건당 고정할인만 반영.
-- 영화/쇼핑(텐바이텐)/간편결제/뷰티/서점/문화/대중교통은 업종 밖·결제수단조건부라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_conv_t1 bigint; v_cap_conv_t2 bigint; v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK021';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상 60만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 600000, '60만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '편의점 영역 한도(1구간)') returning id into v_cap_conv_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '편의점 영역 한도(2구간)') returning id into v_cap_conv_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '커피 영역 한도(1구간)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '커피 영역 한도(2구간)') returning id into v_cap_cafe_t2;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_t1, v_cap_conv_t1, 'DISCOUNT_FIXED', 1000, 10000, '편의점 건당 1,000원 환급할인(1구간)', 'KB국민 첵첵 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_conv_t2, 'DISCOUNT_FIXED', 2000, 10000, '편의점 건당 2,000원 환급할인(2구간)', 'KB국민 첵첵 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_FIXED', 1000, 10000, '커피(스타벅스) 건당 1,000원 환급할인(1구간)', 'KB국민 첵첵 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_FIXED', 2000, 10000, '커피(스타벅스) 건당 2,000원 환급할인(2구간)', 'KB국민 첵첵 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 노리체크카드(CK022, KB국민): 스타벅스·패밀리레스토랑·GS25만 반영. 대중교통/통신/CGV/놀이공원/교보문고/
-- 금융수수료면제는 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_restaurant bigint; v_cat_convenience bigint;
	v_cap_cafe bigint; v_cap_restaurant bigint; v_cap_convenience bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK022';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '스타벅스 20% 환급할인 한도') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '패밀리레스토랑 20% 환급할인 한도') returning id into v_cap_restaurant;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 1000, 'GS25 5% 환급할인 한도') returning id into v_cap_convenience;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'DISCOUNT_RATE', 0.20, 10000, '스타벅스 20% 환급할인', 'KB국민 노리 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_restaurant, 'DISCOUNT_RATE', 0.20, 30000, '패밀리레스토랑(아웃백/VIPS) 20% 환급할인', 'KB국민 노리 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_convenience, 'DISCOUNT_RATE', 0.05, 10000, 'GS25 5% 환급할인', 'KB국민 노리 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;
