-- 정답 케이스 카드: KB국민 굿데이카드(CR004).
-- 출처: KB국민 굿데이카드 상품설명서, 여신금융협회 심의필 2024-C1a-14663(2024.11.22)
-- 간소화/제외:
--   - 주유(60원/ℓ): 리터당 혜택이라 계산 범위 밖(기획 11/9 기준). 데이터로도 안 남긴다.
--   - 통신(10%, 월 3만원), 대중교통 중 버스/지하철: 카똑 지원 8개 업종 밖이라 제외.
--   - 대중교통 중 택시만 지원 업종에 해당해 반영한다.
--   - 음식점 세부 제외(제과/아이스크림, 패스트푸드, 유흥주점)는 merchant_category 단위로 못 나눠서 미반영.
--   - 신규가입 90일 유예기간(1구간 혜택 조건부 제공)은 미반영 — 기획 쪽 간소화 합의 대상(미해결 항목).
-- 실적구간: 직전월 결제회수실적 기준 1구간 30만원↑ / 2구간 60만원↑ / 3구간 120만원↑.

update card_products
	set annual_fee = 5000, product_version = '2024-C1a-14663', data_status = 'CALCULABLE'
	where product_key = 'CR004';

do $$
declare
	v_product_id bigint;
	v_tier1_id bigint;
	v_tier2_id bigint;
	v_tier3_id bigint;
	v_category_restaurant_id bigint;
	v_category_cafe_id bigint;
	v_category_convenience_id bigint;
	v_category_pharmacy_id bigint;
	v_category_taxi_id bigint;
	v_cap_food_id bigint;
	v_cap_taxi_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR004';

	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 1, 300000, '30만원 이상')
		returning id into v_tier1_id;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 2, 600000, '60만원 이상')
		returning id into v_tier2_id;
	insert into card_performance_tiers (product_id, level, min_amount, label)
		values (v_product_id, 3, 1200000, '120만원 이상')
		returning id into v_tier3_id;

	insert into merchant_categories (name, code)
		values ('음식점', 'RESTAURANT')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_restaurant_id;
	insert into merchant_categories (name, code)
		values ('커피전문점', 'CAFE')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_cafe_id;
	insert into merchant_categories (name, code)
		values ('편의점', 'CONVENIENCE_STORE')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_convenience_id;
	insert into merchant_categories (name, code)
		values ('약국', 'PHARMACY')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_pharmacy_id;
	insert into merchant_categories (name, code)
		values ('택시', 'TAXI')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_taxi_id;

	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 50000, '음식점·커피전문점·편의점·약국 통합 한도')
		returning id into v_cap_food_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 50000, '대중교통(택시) 한도 — 버스/지하철 몫은 카똑 지원 업종 밖이라 미반영')
		returning id into v_cap_taxi_id;

	-- 음식점·커피전문점·편의점·약국 10%: 2구간(60만원)부터 적용, 1구간은 대상 아님(PDF 표 참고)
	insert into benefit_rules (
		product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_category_restaurant_id, v_tier2_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'음식점 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_restaurant_id, v_tier3_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'음식점 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_cafe_id, v_tier2_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'커피전문점 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_cafe_id, v_tier3_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'커피전문점 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_convenience_id, v_tier2_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'편의점 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_convenience_id, v_tier3_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'편의점 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_pharmacy_id, v_tier2_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'약국 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_pharmacy_id, v_tier3_id, v_cap_food_id, 'DISCOUNT_RATE', 0.10,
			'약국 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date);

	-- 대중교통 10%(택시만 반영): 1구간부터 전 구간 적용
	insert into benefit_rules (
		product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_category_taxi_id, v_tier1_id, v_cap_taxi_id, 'DISCOUNT_RATE', 0.10,
			'택시 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_taxi_id, v_tier2_id, v_cap_taxi_id, 'DISCOUNT_RATE', 0.10,
			'택시 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_taxi_id, v_tier3_id, v_cap_taxi_id, 'DISCOUNT_RATE', 0.10,
			'택시 10% 청구할인', 'KB국민 굿데이카드 상품설명서 p.1', 'VERIFIED', now(), 1, current_date);
end $$;
