-- 2차 배치 10장: My WE:SH(CR008), 카드의정석2 SUPER(CR009), 신한카드 처음(CR011),
-- LOCA 365(CR014), 삼성카드 taptap O(CR017), Discount Plan+(CR019),
-- 삼성 iD SELECT UP(CR020), Hi-Point Plan+(CR021), Simple Plan+(CR022), 삼성 iD GLOBAL(CR023).
-- 정답 케이스 카드가 아니라서 "일 1회" 등 세부 횟수 한도, 결제수단 조건부 혜택, 선택형 옵션(기본값 없는 경우),
-- 공과금/보험/통신/주유 등 업종 밖 혜택, 1회성 웰컴 캐시백·연 1회 보너스는 공통적으로 간소화/제외했다.

-- My WE:SH(CR008): 선택형 '더욱진심' 3팩(택1, 전가맹점 기본값 없음) 전부 제외.
-- '나한테진심'(고정) 중 KB Pay/통신/OTT는 결제수단·업종 밖이라 제외, 음식점·편의점 10%만 반영.
update card_products set annual_fee = 15000, product_version = '2024-C1a-15690', data_status = 'CALCULABLE'
	where product_key = 'CR008';
do $$
declare
	v_product_id bigint; v_tier_id bigint; v_cat_restaurant bigint; v_cat_convenience bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR008';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 1, 400000, '40만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 5000, '나한테진심 음식점·편의점 통합 한도')
		returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.10, 2500,
			'음식점 10% 할인(나한테진심)', 'My WE:SH 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.10, 2500,
			'편의점 10% 할인(나한테진심)', 'My WE:SH 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석2 SUPER(CR009): 국내외 가맹점 2.0% 청구할인, 월한도 10만원, 실적조건 없음.
update card_products set annual_fee = 30000, product_version = '2026-C1a-03759', data_status = 'CALCULABLE'
	where product_key = 'CR009';
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR009';
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 100000, '전가맹점 2% 청구할인 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.02, jsonb_build_object('allMerchants', true),
		'전가맹점 2% 청구할인', '카드의정석2 SUPER 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 처음(CR011): 오늘도5%(음식점·카페·편의점·온라인쇼핑, 실적구간별 통합한도)
-- + 일상속5% 중 택시만 반영(생활/여행 나머지·패션은 업종 밖). 정기결제·소비관리보너스는 업종 밖/행동기반이라 제외.
update card_products set annual_fee = 15000, product_version = '2024-C1a-03987', data_status = 'CALCULABLE'
	where product_key = 'CR011';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_restaurant bigint; v_cat_cafe bigint; v_cat_convenience bigint; v_cat_online bigint; v_cat_taxi bigint;
	v_cap_today_t1 bigint; v_cap_today_t2 bigint; v_cap_today_t3 bigint;
	v_cap_life_t1 bigint; v_cap_life_t2 bigint; v_cap_life_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR011';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';

	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 1, 300000, '30~50만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 2, 500000, '50~100만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 3, 1000000, '100만원 이상') returning id into v_t3;

	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 5000, '오늘도5% 통합 한도(30~50만원)') returning id into v_cap_today_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 10000, '오늘도5% 통합 한도(50~100만원)') returning id into v_cap_today_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 20000, '오늘도5% 통합 한도(100만원 이상)') returning id into v_cap_today_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 5000, '일상속5% 한도(30~50만원)') returning id into v_cap_life_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 10000, '일상속5% 한도(50~100만원)') returning id into v_cap_life_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 20000, '일상속5% 한도(100만원 이상)') returning id into v_cap_life_t3;

	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_t1, v_cap_today_t1, 'POINT_RATE', 0.05, 10000, '음식점 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t2, v_cap_today_t2, 'POINT_RATE', 0.05, 10000, '음식점 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t3, v_cap_today_t3, 'POINT_RATE', 0.05, 10000, '음식점 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_today_t1, 'POINT_RATE', 0.05, 10000, '카페 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_today_t2, 'POINT_RATE', 0.05, 10000, '카페 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_today_t3, 'POINT_RATE', 0.05, 10000, '카페 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_today_t1, 'POINT_RATE', 0.05, 10000, '편의점 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_today_t2, 'POINT_RATE', 0.05, 10000, '편의점 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_today_t3, 'POINT_RATE', 0.05, 10000, '편의점 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_today_t1, 'POINT_RATE', 0.05, 10000, '온라인쇼핑 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_today_t2, 'POINT_RATE', 0.05, 10000, '온라인쇼핑 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_today_t3, 'POINT_RATE', 0.05, 10000, '온라인쇼핑 5% 포인트(오늘도5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_life_t1, 'POINT_RATE', 0.05, 50000, '택시 5% 포인트(일상속5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_life_t2, 'POINT_RATE', 0.05, 50000, '택시 5% 포인트(일상속5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t3, v_cap_life_t3, 'POINT_RATE', 0.05, 50000, '택시 5% 포인트(일상속5%)', '신한카드 처음 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- LOCA 365(CR014): 아파트관리비·공과금·이동통신·대중교통(택시 제외)·보험료·학습지·배달앱·스트리밍 전부
-- 카똑 지원 8개 업종 밖이라, 검토 결과 반영할 혜택이 없다. 메타데이터만 갱신하고 UNVERIFIED로 둔다.
update card_products set annual_fee = 20000, product_version = '2026-C1a-13602'
	where product_key = 'CR014';

-- 삼성카드 taptap O(CR017): 교통할인 중 택시만 반영(대중교통은 업종 밖). 이동통신·영화·해외적립은 업종 밖/제외.
-- 라이프스타일 옵션 패키지(택1, 전가맹점 기본값 없음)는 전체 제외.
update card_products set annual_fee = 10000, product_version = '2026-C1a-08163', data_status = 'CALCULABLE'
	where product_key = 'CR017';
do $$
declare v_product_id bigint; v_tier_id bigint; v_cat_taxi bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR017';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 5000, '교통할인(택시) 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_taxi, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.10,
		'택시 10% 청구할인', '삼성카드 taptap O 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Discount Plan+(CR019): Time Plan(카페·음식점 DAY, 편의점 NIGHT)과
-- Daily Plan(마트·온라인쇼핑 쇼핑10%, 택시 이동5%)만 반영. 배달앱·주유·카쉐어링·주차·해외·병원약국·
-- 미용실·온라인서점·공과금·디지털구독·피트니스·영화·장보기캐시백은 업종 밖/행동기반이라 제외.
-- "일 1회" 영역별 횟수 제한은 간소화(월 통합 한도만 반영).
update card_products set annual_fee = 50000, product_version = '2026-C1a-14748', data_status = 'CALCULABLE'
	where product_key = 'CR019';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint; v_t4 bigint;
	v_cat_cafe bigint; v_cat_restaurant bigint; v_cat_convenience bigint; v_cat_mart bigint; v_cat_online bigint; v_cat_taxi bigint;
	v_cap_time_t1 bigint; v_cap_time_t2 bigint; v_cap_time_t3 bigint; v_cap_time_t4 bigint;
	v_cap_daily_t1 bigint; v_cap_daily_t2 bigint; v_cap_daily_t3 bigint; v_cap_daily_t4 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR019';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';

	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 400000, '40~80만원') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 800000, '80~120만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1200000, '120~180만원') returning id into v_t3;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 4, 1800000, '180만원 이상') returning id into v_t4;

	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, 'Time Plan 한도(40~80만원)') returning id into v_cap_time_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, 'Time Plan 한도(80~120만원)') returning id into v_cap_time_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 15000, 'Time Plan 한도(120~180만원)') returning id into v_cap_time_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, 'Time Plan 한도(180만원 이상)') returning id into v_cap_time_t4;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 15000, 'Daily Plan 한도(40~80만원)') returning id into v_cap_daily_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 25000, 'Daily Plan 한도(80~120만원)') returning id into v_cap_daily_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 35000, 'Daily Plan 한도(120~180만원)') returning id into v_cap_daily_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, 'Daily Plan 한도(180만원 이상)') returning id into v_cap_daily_t4;

	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		max_eligible_txn_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_time_t1, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '카페 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_time_t2, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '카페 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_time_t3, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '카페 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t4, v_cap_time_t4, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '카페 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t1, v_cap_time_t1, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '음식점 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t2, v_cap_time_t2, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '음식점 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t3, v_cap_time_t3, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '음식점 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t4, v_cap_time_t4, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','07:00','to','15:00')), '음식점 10% 할인(DAY)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_time_t1, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','18:00','to','22:00')), '편의점 10% 할인(NIGHT)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_time_t2, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','18:00','to','22:00')), '편의점 10% 할인(NIGHT)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_time_t3, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','18:00','to','22:00')), '편의점 10% 할인(NIGHT)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t4, v_cap_time_t4, 'DISCOUNT_RATE', 0.10, 10000, jsonb_build_object('timeRange', jsonb_build_object('from','18:00','to','22:00')), '편의점 10% 할인(NIGHT)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_daily_t1, 'DISCOUNT_RATE', 0.10, 50000, null, '마트 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_daily_t2, 'DISCOUNT_RATE', 0.10, 50000, null, '마트 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_daily_t3, 'DISCOUNT_RATE', 0.10, 50000, null, '마트 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t4, v_cap_daily_t4, 'DISCOUNT_RATE', 0.10, 50000, null, '마트 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_daily_t1, 'DISCOUNT_RATE', 0.10, 50000, null, '온라인쇼핑 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_daily_t2, 'DISCOUNT_RATE', 0.10, 50000, null, '온라인쇼핑 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_daily_t3, 'DISCOUNT_RATE', 0.10, 50000, null, '온라인쇼핑 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t4, v_cap_daily_t4, 'DISCOUNT_RATE', 0.10, 50000, null, '온라인쇼핑 10% 할인(쇼핑)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_daily_t1, 'DISCOUNT_RATE', 0.05, 50000, null, '택시 5% 할인(이동)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_daily_t2, 'DISCOUNT_RATE', 0.05, 50000, null, '택시 5% 할인(이동)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t3, v_cap_daily_t3, 'DISCOUNT_RATE', 0.05, 50000, null, '택시 5% 할인(이동)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t4, v_cap_daily_t4, 'DISCOUNT_RATE', 0.05, 50000, null, '택시 5% 할인(이동)', 'Discount Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 삼성 iD SELECT UP(CR020): 국내가맹점0.5%(전가맹점) + 쇼핑1%(백화점·할인점(마트)·온라인쇼핑몰만 반영,
-- 홈쇼핑·신선식품쇼핑몰·프리미엄아울렛은 업종 밖). SELECT서비스(의료20%/생활10%, 전가맹점 기본값 없음)와
-- 여가2%(골프·항공·면세점·철도·공연)는 업종 밖/선택형이라 제외.
update card_products set annual_fee = 18000, product_version = '2026-C1a-05797', data_status = 'CALCULABLE'
	where product_key = 'CR020';
do $$
declare
	v_product_id bigint; v_cat_department bigint; v_cat_mart bigint; v_cat_online bigint;
	v_cap_all bigint; v_cap_shopping bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR020';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', '국내 가맹점 0.5% — 한도 없음') returning id into v_cap_all;
	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', '쇼핑 1% — 한도 없음') returning id into v_cap_shopping;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_all, 'DISCOUNT_RATE', 0.005, jsonb_build_object('allMerchants', true),
		'국내 가맹점 0.5% 할인', 'iD SELECT UP 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_department, v_cap_shopping, 'DISCOUNT_RATE', 0.01, '백화점 1% 할인(쇼핑)', 'iD SELECT UP 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_shopping, 'DISCOUNT_RATE', 0.01, '마트 1% 할인(쇼핑)', 'iD SELECT UP 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_cap_shopping, 'DISCOUNT_RATE', 0.01, '온라인쇼핑 1% 할인(쇼핑)', 'iD SELECT UP 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Hi-Point Plan+(CR021): 기본적립(전가맹점, 실적구간별 요율) + 특별적립(마트·카페만 반영,
-- 생활·주유·통신은 업종 밖), 기본+특별 통합 월 7만P 한도. 포인트리필·웰컴포인트는 행동기반/1회성이라 제외.
update card_products set annual_fee = 50000, product_version = '2026-C1a-10742', data_status = 'CALCULABLE'
	where product_key = 'CR021';
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint; v_t4 bigint;
	v_cat_mart bigint; v_cat_cafe bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint; v_cap_t4 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR021';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 0, '50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50~100만원') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1000000, '100~150만원') returning id into v_t3;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 4, 1500000, '150만원 이상') returning id into v_t4;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 70000, '기본+특별 적립 통합 한도(50만원 미만)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 70000, '기본+특별 적립 통합 한도(50~100만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 70000, '기본+특별 적립 통합 한도(100~150만원)') returning id into v_cap_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 70000, '기본+특별 적립 통합 한도(150만원 이상)') returning id into v_cap_t4;

	insert into benefit_rules (product_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_t1, v_cap_t1, 'POINT_RATE', 0.005, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 0.5% 포인트(기본적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t2, v_cap_t2, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1% 포인트(기본적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t3, v_cap_t3, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 1.5% 포인트(기본적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_t4, v_cap_t4, 'POINT_RATE', 0.02, 1.0, jsonb_build_object('allMerchants', true), '전가맹점 2% 포인트(기본적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_mart, v_t1, v_cap_t1, 'POINT_RATE', 0.02, 1.0, '마트 2% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_t2, 'POINT_RATE', 0.02, 1.0, '마트 2% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_t3, 'POINT_RATE', 0.04, 1.0, '마트 4% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t4, v_cap_t4, 'POINT_RATE', 0.06, 1.0, '마트 6% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'POINT_RATE', 0.02, 1.0, '카페 2% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'POINT_RATE', 0.02, 1.0, '카페 2% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'POINT_RATE', 0.04, 1.0, '카페 4% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t4, v_cap_t4, 'POINT_RATE', 0.06, 1.0, '카페 6% 포인트(특별적립)', 'Hi-Point Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 Simple Plan+(CR022): 국내 이용금액 1.5% 결제일할인, 실적조건·한도 없음. 해외2%는 제외(해외).
update card_products set annual_fee = 47000, product_version = '2026-C1a-01695', data_status = 'CALCULABLE'
	where product_key = 'CR022';
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR022';
	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', '국내 1.5% 할인 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.015, jsonb_build_object('allMerchants', true),
		'국내 1.5% 결제일할인', 'Simple Plan+ 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 삼성 iD GLOBAL(CR023): 국내가맹점0.5%(전가맹점) + 일상필수영역1% 중 온라인쇼핑몰·백화점·편의점·커피전문점만 반영
-- (온라인간편결제·항공·여행사·면세점·리셀·홈쇼핑·주유는 결제수단 조건부/업종 밖). 해외 관련 혜택·인앱결제/
-- 디지털콘텐츠/멤버십·공항라운지는 전부 제외.
update card_products set annual_fee = 20000, product_version = '2024-C1a-01197', data_status = 'CALCULABLE'
	where product_key = 'CR023';
do $$
declare
	v_product_id bigint; v_cat_online bigint; v_cat_department bigint; v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_all bigint; v_cap_daily bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR023';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', '국내 가맹점 0.5% — 한도 없음') returning id into v_cap_all;
	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', '일상 필수 영역 1% — 한도 없음') returning id into v_cap_daily;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_all, 'DISCOUNT_RATE', 0.005, jsonb_build_object('allMerchants', true),
		'국내 가맹점 0.5% 할인', 'iD GLOBAL 상품안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_cap_daily, 'DISCOUNT_RATE', 0.01, '온라인쇼핑 1% 할인(일상필수)', 'iD GLOBAL 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_cap_daily, 'DISCOUNT_RATE', 0.01, '백화점 1% 할인(일상필수)', 'iD GLOBAL 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_cap_daily, 'DISCOUNT_RATE', 0.01, '편의점 1% 할인(일상필수)', 'iD GLOBAL 상품안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_cap_daily, 'DISCOUNT_RATE', 0.01, '커피전문점 1% 할인(일상필수)', 'iD GLOBAL 상품안내장', 'VERIFIED', now(), 1, current_date);
end $$;
