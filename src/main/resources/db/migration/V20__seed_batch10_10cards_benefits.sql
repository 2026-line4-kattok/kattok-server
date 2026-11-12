-- 10차 배치 10장: 그랑블루체크(CK061), 카카오페이 체크카드(CK062), K-패스 하나 체크카드(CK063),
-- SOL메이트 신한카드 SOL Plan 체크(CK065), 카드의정석2 원더라이프 CHECK(CK066),
-- 청춘대로 싱글 체크카드(CK070), K-패스(체크)(CK071), 케이뱅크 플러스 체크카드(CK074),
-- 스타체크카드(CK077), 삼성체크카드 & CASHBACK(CK079).
-- 체크카드는 여신금융협회 심의필 대신 각 카드사 준법감시인/준법심의 코드를 product_version에 기록한다.
-- CK070, CK077은 상품설명서에 연회비 항목이 보이지 않아 동일 데이터셋의 다른 KB 체크카드 관례대로
-- 0으로 기록했다(불확실).

update card_products set annual_fee = 100000, product_version = '2026-C02549', data_status = 'CALCULABLE' where product_key = 'CK061';
update card_products set annual_fee = 0, product_version = 'L-26-0135', data_status = 'CALCULABLE' where product_key = 'CK062';
update card_products set annual_fee = 0, product_version = 'L-25-0390', data_status = 'CALCULABLE' where product_key = 'CK063';
update card_products set annual_fee = 0, product_version = '20260907-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK065';
update card_products set annual_fee = 0, product_version = '2026-C00497', data_status = 'CALCULABLE' where product_key = 'CK066';
update card_products set annual_fee = 0, product_version = '260522-01722-PIF', data_status = 'CALCULABLE' where product_key = 'CK070';
update card_products set annual_fee = 0, product_version = '2024-2062', data_status = 'CALCULABLE' where product_key = 'CK071';
update card_products set annual_fee = 0, product_version = '2025-2526', data_status = 'CALCULABLE' where product_key = 'CK074';
update card_products set annual_fee = 0, product_version = '260522-01722-PIF', data_status = 'CALCULABLE' where product_key = 'CK077';
update card_products set annual_fee = 0, product_version = 'P3121-16-1464', data_status = 'CALCULABLE' where product_key = 'CK079';

-- 그랑블루체크(CK061, 우리): 모아포인트 국내가맹점 0.3%(백화점 제외) + 백화점 1.0% + 치킨/피자(외식) 10%만
-- 반영. 병의원/이동통신/면세점/해외는 업종 밖이라 모아포인트에서 제외, 결제건당 50만원이상 0.8% 적립은
-- 업종 적립과 중복 불가한 대체 규칙이라 간소화 차원에서 생략. 데이터로밍/주차장/영화/주유/골프/투어/
-- 공항라운지는 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_department bigint; v_cat_restaurant bigint;
	v_cap_moa bigint; v_cap_restaurant bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK061';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 100000, '모아포인트 적립 통합 한도') returning id into v_cap_moa;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 5000, 1, '치킨/피자 캐시백 한도(프리미엄 서비스)') returning id into v_cap_restaurant;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_moa, 'POINT_RATE', 0.003, 1.0, 10000, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_department)), '국내가맹점 0.3% 모아포인트 적립', '그랑블루체크 포인트 서비스 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_department, v_cap_moa, 'POINT_RATE', 0.01, 1.0, 10000, '백화점 1.0% 모아포인트 적립', '그랑블루체크 포인트 서비스 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_restaurant, v_tier_id, v_cap_restaurant, 'DISCOUNT_RATE', 0.10, 50000, '치킨/피자 프랜차이즈 10% 캐시백', '그랑블루체크 프리미엄 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카카오페이 체크카드(CK062, 하나): 스타벅스/카페베네/파스쿠치 5% 캐시백만 반영. 카카오페이결제(간편결제
-- 전용)/통신요금자동이체/대중교통은 결제수단조건부·업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_cafe bigint;
	v_cap_t1 bigint; v_cap_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK062';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 250000, '25만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 400000, '40만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 7000, '통합 캐쉬백 한도(25만원 이상)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '통합 캐쉬백 한도(40만원 이상)') returning id into v_cap_t2;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 10000, '스타벅스/카페베네/파스쿠치 5% 캐쉬백', '카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 10000, '스타벅스/카페베네/파스쿠치 5% 캐쉬백', '카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- K-패스 하나 체크카드(CK063): 커피(스타벅스/커피빈) 1% 캐쉬백만 반영. 대중교통(K-패스)은 업종 밖,
-- 드럭스토어(다이소/올리브영)는 8개 업종에 대응되지 않아 제외.
do $$
declare v_product_id bigint; v_tier_id bigint; v_cat_cafe bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK063';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '커피 캐쉬백 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.01, '스타벅스/커피빈 1% 캐쉬백', 'K-패스 하나 체크카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- SOL메이트 신한카드 SOL Plan 체크(CK065): 기본적립(전가맹점) + 특별적립(마트/카페)만 반영. 병원은 8개
-- 업종 밖, 담을수록 서비스(OTT/멤버십)는 구독이라 제외. 기본·특별 적립은 통합 3만P 한도를 공유한다.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_mart bigint; v_cat_cafe bigint;
	v_cap_t1 bigint; v_cap_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK065';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상 60만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 600000, '60만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 30000, '쓸수록 SOLSOL 통합 적립 한도(20~60만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 30000, '쓸수록 SOLSOL 통합 적립 한도(60만원 이상)') returning id into v_cap_t2;
	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_t1, v_cap_t1, 'POINT_RATE', 0.002, 1.0, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_mart, v_cat_cafe)), '국내외 가맹점 0.2% 기본 적립', 'SOL메이트 신한카드 SOL Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t2, v_cap_t2, 'POINT_RATE', 0.004, 1.0, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_mart, v_cat_cafe)), '국내외 가맹점 0.4% 기본 적립', 'SOL메이트 신한카드 SOL Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_mart, v_t1, v_cap_t1, 'POINT_RATE', 0.01, 1.0, '마트(이마트/트레이더스/롯데마트/농협하나로마트) 1% 특별 적립', 'SOL메이트 신한카드 SOL Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_t2, 'POINT_RATE', 0.02, 1.0, '마트(이마트/트레이더스/롯데마트/농협하나로마트) 2% 특별 적립', 'SOL메이트 신한카드 SOL Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'POINT_RATE', 0.01, 1.0, '카페(스타벅스/이디야/커피빈 등) 1% 특별 적립', 'SOL메이트 신한카드 SOL Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'POINT_RATE', 0.02, 1.0, '카페(스타벅스/이디야/커피빈 등) 2% 특별 적립', 'SOL메이트 신한카드 SOL Plan 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석2 원더라이프 CHECK(CK066, 우리): 기본캐시백 0.2%(전가맹점) + 특별캐시백(온라인쇼핑/생활
-- 편의(편의점+마트)/건강(약국)). TV홈쇼핑/보험/레저/주유는 업종 밖이라 제외. 기본캐시백은 특별캐시백
-- 카테고리에도 그대로 적용되며(특별캐시백이 한도 소진 시 기본캐시백으로 대체), 특별캐시백 한도 소진 여부는
-- 계산 엔진이 cap_group 잔여 한도로 판단한다.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_online bigint; v_cat_convenience bigint; v_cat_mart bigint; v_cat_pharmacy bigint;
	v_cap_base bigint; v_cap_life bigint; v_cap_health bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK066';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본캐시백 0.2% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '온라인쇼핑/생활편의 특별캐시백 통합 한도') returning id into v_cap_life;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '건강/의료 특별캐시백 통합 한도') returning id into v_cap_health;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'DISCOUNT_RATE', 0.002, jsonb_build_object('allMerchants', true), '국내외 가맹점 0.2% 기본캐시백', '카드의정석2 원더라이프 CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_tier_id, v_cap_life, 'DISCOUNT_RATE', 0.005, '온라인쇼핑(쿠팡/G마켓/옥션) 0.5% 특별캐시백', '카드의정석2 원더라이프 CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_life, 'DISCOUNT_RATE', 0.005, '편의점 0.5% 특별캐시백(생활/편의)', '카드의정석2 원더라이프 CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_tier_id, v_cap_life, 'DISCOUNT_RATE', 0.005, '대형마트 0.5% 특별캐시백(생활/편의)', '카드의정석2 원더라이프 CHECK 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_tier_id, v_cap_health, 'DISCOUNT_RATE', 0.01, '약국업종 1% 특별캐시백(건강/의료)', '카드의정석2 원더라이프 CHECK 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 청춘대로 싱글 체크카드(CK070, KB국민): 편의점/소셜커머스(쿠팡 등)/택시만 반영. 다이소/해외/뷰티·펫/
-- 서점성 여행은 업종 밖이라 제외. 싱글데이(매월 1/11/21/31일) 추가 5%는 날짜 조건부 보너스라 간소화해
-- 기본 할인율만 반영.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_convenience bigint; v_cat_online bigint; v_cat_taxi bigint;
	v_cap_conv_t1 bigint; v_cap_conv_t2 bigint; v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_taxi bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK070';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상 50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '편의점 할인 한도(1구간)') returning id into v_cap_conv_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '편의점 할인 한도(2구간)') returning id into v_cap_conv_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '쇼핑 할인 한도(1구간)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '쇼핑 할인 한도(2구간)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '택시 할인 한도') returning id into v_cap_taxi;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_t1, v_cap_conv_t1, 'DISCOUNT_RATE', 0.05, 10000, 20000, '편의점 업종 5% 환급할인', '청춘대로 싱글 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_conv_t2, 'DISCOUNT_RATE', 0.05, 10000, 20000, '편의점 업종 5% 환급할인', '청춘대로 싱글 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.05, 30000, 50000, '소셜커머스(쿠팡/티몬/위메프) 5% 환급할인', '청춘대로 싱글 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.05, 30000, 50000, '소셜커머스(쿠팡/티몬/위메프) 5% 환급할인', '청춘대로 싱글 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_taxi, 'DISCOUNT_RATE', 0.05, 5000, 20000, '택시 업종 5% 환급할인', '청춘대로 싱글 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- K-패스(체크)(CK071, IBK): 커피/쇼핑(쿠팡·티몬·위메프)/편의점만 반영. 대중교통은 업종 밖이라 제외.
-- 영역별 개별 한도("1일1회,월2회,1만원한도")는 통합 월한도("30만원이상 5천원/50만원이상 1만원")로
-- 간소화해 세 업종이 공유하는 단일 한도로 반영한다.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_cafe bigint; v_cat_online bigint; v_cat_convenience bigint;
	v_cap_t1 bigint; v_cap_t2 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK071';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '할인 서비스 통합 월한도(30만원 이상)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '할인 서비스 통합 월한도(50만원 이상)') returning id into v_cap_t2;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.10, 10000, '커피(스타벅스 등) 10% 할인', 'K-패스(체크) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.10, 10000, '커피(스타벅스 등) 10% 할인', 'K-패스(체크) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.10, 10000, '쇼핑(쿠팡/티몬/위메프) 10% 할인', 'K-패스(체크) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.10, 10000, '쇼핑(쿠팡/티몬/위메프) 10% 할인', 'K-패스(체크) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, 10000, '편의점(GS25/CU/세븐일레븐) 5% 할인', 'K-패스(체크) 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, 10000, '편의점(GS25/CU/세븐일레븐) 5% 할인', 'K-패스(체크) 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 케이뱅크 플러스 체크카드(CK074): 국내외 전가맹점 0.3% 캐시백만 반영(실적·한도 없음).
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK074';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 전가맹점 0.3% — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.003, jsonb_build_object('allMerchants', true), '국내외 전가맹점 0.3% 캐시백', '케이뱅크 플러스 체크카드 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 스타체크카드(CK077, KB국민): 외식(아웃백/VIPS)/온라인쇼핑(G마켓/옥션)만 반영. 영화/주유/놀이공원/뷰티/
-- 서점/이동통신/대중교통은 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint; v_t4 bigint;
	v_cat_restaurant bigint; v_cat_online bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint; v_cap_t4 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK077';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
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
		(v_product_id, v_cat_restaurant, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스/VIPS 10% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스/VIPS 10% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스/VIPS 10% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t4, v_cap_t4, 'DISCOUNT_RATE', 0.10, 30000, 100000, '아웃백스테이크하우스/VIPS 10% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.03, 30000, 100000, 'G마켓/옥션 3% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.03, 30000, 100000, 'G마켓/옥션 3% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.03, 30000, 100000, 'G마켓/옥션 3% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t4, v_cap_t4, 'DISCOUNT_RATE', 0.03, 30000, 100000, 'G마켓/옥션 3% 환급할인', 'KB국민 스타체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 삼성체크카드 & CASHBACK(CK079): 일반가맹점 0.2%(음식점/할인점 제외) + 음식점/할인점 0.3% + 편의점·커피
-- 1,000원 캐시백만 반영. 연누적 300만원 이상 시 요율이 0.6%로 오르는 조건은 월 단위 전월실적 구조가
-- 아니라 모델링할 수 없어 기본(낮은) 요율만 반영. 주유/통신/놀이공원/영화는 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_restaurant bigint; v_cat_mart bigint; v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_base bigint; v_cap_food_mart bigint; v_cap_conv_cafe bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK079';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '일반가맹점 0.2% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '음식점/할인점 0.3% — 한도 없음') returning id into v_cap_food_mart;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 3000, 3, '편의점/커피 1,000원 캐시백 한도(통합 월3회)') returning id into v_cap_conv_cafe;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'DISCOUNT_RATE', 0.002, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_restaurant, v_cat_mart)), '일반가맹점 0.2% 캐시백', '삼성체크카드 & CASHBACK 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_cap_food_mart, 'DISCOUNT_RATE', 0.003, '음식점 0.3% 캐시백', '삼성체크카드 & CASHBACK 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_food_mart, 'DISCOUNT_RATE', 0.003, '할인점(이마트/홈플러스/롯데마트) 0.3% 캐시백', '삼성체크카드 & CASHBACK 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_conv_cafe, 'DISCOUNT_FIXED', 1000, 10000, '편의점 1,000원 캐시백', '삼성체크카드 & CASHBACK 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_conv_cafe, 'DISCOUNT_FIXED', 1000, 10000, '커피전문점 1,000원 캐시백', '삼성체크카드 & CASHBACK 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;
