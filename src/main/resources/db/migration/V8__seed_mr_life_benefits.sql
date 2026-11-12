-- 정답 케이스 카드: 신한카드 Mr.Life(CR001).
-- 출처: 신한카드 Mr.Life 상품설명서, 여신금융협회 심의필 2026-C1a-09920(2026.07.01)
-- 간소화/제외:
--   - 월납 할인(공과금: 전기·가스·통신), 주유소(리터당 혜택): 카똑 지원 8개 업종 밖이거나 계산 범위 밖이라 제외.
--   - All Day/Night 혜택 중 세탁비: 지원 업종 밖이라 제외. 병원/약국은 약국만 반영(병원은 업종 밖).
--   - All Day/Night의 "일 1회" 하루 단위 횟수 제한은 미반영 — 월 단위 횟수 한도만 반영한다(간소화).
--   - 온라인쇼핑 혜택은 PDF상 티켓몬스터·롯데홈쇼핑·쿠팡은 "사이트 직접 접속시에만" 적용된다는 단서가 있지만
--     merchant 단위 조건까지는 반영하지 못해 업종 전체로 간소화했다.
-- 실적구간: 전월 실적 30~50만원 / 50~100만원 / 100만원 이상.
-- Night 시간대: 오후 9시(21:00) ~ 오전 9시(09:00), 자정을 넘는 구간(to < from).

update card_products
	set annual_fee = 18000, product_version = '2026-C1a-09920', data_status = 'CALCULABLE'
	where product_key = 'CR001';

do $$
declare
	v_product_id bigint;
	v_tier1_id bigint;
	v_tier2_id bigint;
	v_tier3_id bigint;
	v_category_mart_id bigint;
	v_category_convenience_id bigint;
	v_category_pharmacy_id bigint;
	v_category_online_id bigint;
	v_category_taxi_id bigint;
	v_category_restaurant_id bigint;
	v_category_cafe_id bigint;
	v_cap_mart_t1_id bigint;
	v_cap_mart_t2_id bigint;
	v_cap_mart_t3_id bigint;
	v_cap_convenience_id bigint;
	v_cap_pharmacy_id bigint;
	v_cap_online_id bigint;
	v_cap_taxi_night_id bigint;
	v_cap_food_night_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR001';

	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 1, 300000, '30~50만원')
		returning id into v_tier1_id;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 2, 500000, '50~100만원')
		returning id into v_tier2_id;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 3, 1000000, '100만원 이상')
		returning id into v_tier3_id;

	insert into merchant_categories (name, code)
		values ('마트', 'MART')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_mart_id;
	select id into v_category_convenience_id from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_category_pharmacy_id from merchant_categories where code = 'PHARMACY';
	select id into v_category_taxi_id from merchant_categories where code = 'TAXI';
	select id into v_category_restaurant_id from merchant_categories where code = 'RESTAURANT';
	select id into v_category_cafe_id from merchant_categories where code = 'CAFE';
	insert into merchant_categories (name, code)
		values ('온라인쇼핑', 'ONLINE_SHOPPING')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_online_id;

	-- 주말 마트 10%: 전월 실적 구간마다 월 할인한도가 다르다(3천/7천/1만원).
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 3000, '주말 마트 할인한도 (30~50만원 구간)')
		returning id into v_cap_mart_t1_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 7000, '주말 마트 할인한도 (50~100만원 구간)')
		returning id into v_cap_mart_t2_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 10000, '주말 마트 할인한도 (100만원 이상 구간)')
		returning id into v_cap_mart_t3_id;

	insert into benefit_rules (
		product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		max_eligible_txn_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_category_mart_id, v_tier1_id, v_cap_mart_t1_id, 'DISCOUNT_RATE', 0.10,
			50000, '{"daysOfWeek":["SAT","SUN"]}'::jsonb,
			'주말 마트 10% 할인', 'Mr.Life 상품설명서 p.2 주말 할인서비스', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_mart_id, v_tier2_id, v_cap_mart_t2_id, 'DISCOUNT_RATE', 0.10,
			50000, '{"daysOfWeek":["SAT","SUN"]}'::jsonb,
			'주말 마트 10% 할인', 'Mr.Life 상품설명서 p.2 주말 할인서비스', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_mart_id, v_tier3_id, v_cap_mart_t3_id, 'DISCOUNT_RATE', 0.10,
			50000, '{"daysOfWeek":["SAT","SUN"]}'::jsonb,
			'주말 마트 10% 할인', 'Mr.Life 상품설명서 p.2 주말 할인서비스', 'VERIFIED', now(), 1, current_date);

	-- All Day 10%(실적 조건 없음): 편의점·약국. 1회 1만원까지 할인 적용(최대 1천원), 월 5회.
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description)
		values (v_product_id, 'MONTHLY', 5, 'All Day 편의점 월 횟수 한도')
		returning id into v_cap_convenience_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description)
		values (v_product_id, 'MONTHLY', 5, 'All Day 약국 월 횟수 한도')
		returning id into v_cap_pharmacy_id;

	insert into benefit_rules (
		product_id, category_id, cap_group_id, benefit_type, rate,
		max_eligible_txn_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_category_convenience_id, v_cap_convenience_id, 'DISCOUNT_RATE', 0.10,
			10000, '편의점 10% 할인(All Day)', 'Mr.Life 상품설명서 p.2 Time 할인서비스 All Day', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_pharmacy_id, v_cap_pharmacy_id, 'DISCOUNT_RATE', 0.10,
			10000, '약국 10% 할인(All Day)', 'Mr.Life 상품설명서 p.2 Time 할인서비스 All Day', 'VERIFIED', now(), 1, current_date);

	-- Night 10%(오후9시~오전9시, 실적 조건 없음): 온라인쇼핑·택시·식음료(음식점+커피전문점). 1회 1만원까지, 월 10회.
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description)
		values (v_product_id, 'MONTHLY', 10, 'Night 온라인쇼핑 월 횟수 한도')
		returning id into v_cap_online_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description)
		values (v_product_id, 'MONTHLY', 10, 'Night 택시 월 횟수 한도')
		returning id into v_cap_taxi_night_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_count, description)
		values (v_product_id, 'MONTHLY', 10, 'Night 식음료(음식점+커피전문점) 월 횟수 한도')
		returning id into v_cap_food_night_id;

	insert into benefit_rules (
		product_id, category_id, cap_group_id, benefit_type, rate,
		max_eligible_txn_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_category_online_id, v_cap_online_id, 'DISCOUNT_RATE', 0.10,
			10000, '{"timeRange":{"from":"21:00","to":"09:00"}}'::jsonb,
			'온라인쇼핑 10% 할인(Night)', 'Mr.Life 상품설명서 p.2 Time 할인서비스 Night', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_taxi_id, v_cap_taxi_night_id, 'DISCOUNT_RATE', 0.10,
			10000, '{"timeRange":{"from":"21:00","to":"09:00"}}'::jsonb,
			'택시 10% 할인(Night)', 'Mr.Life 상품설명서 p.2 Time 할인서비스 Night', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_restaurant_id, v_cap_food_night_id, 'DISCOUNT_RATE', 0.10,
			10000, '{"timeRange":{"from":"21:00","to":"09:00"}}'::jsonb,
			'음식점 10% 할인(Night)', 'Mr.Life 상품설명서 p.2 Time 할인서비스 Night', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_cafe_id, v_cap_food_night_id, 'DISCOUNT_RATE', 0.10,
			10000, '{"timeRange":{"from":"21:00","to":"09:00"}}'::jsonb,
			'커피전문점 10% 할인(Night)', 'Mr.Life 상품설명서 p.2 Time 할인서비스 Night', 'VERIFIED', now(), 1, current_date);
end $$;
