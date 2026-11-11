-- 정답 케이스 카드: 하나 MOVING카드(CR005), ALLDAY 모드 기준.
-- 출처: 하나카드 MOVING카드 상품안내장, 여신금융협회 심의필 2026-C1a-09124(2026.06.19)
-- 간소화/제외 (기획 11/9 "모드형은 전 가맹점 기본 모드로 가정" 규칙 적용):
--   - ONLINE/PLAY/LIFE/GLOBAL 모드는 전부 제외, ALLDAY 모드만 반영.
--   - 온라인간편결제(하나페이·삼성페이 등) 영역: 결제수단 조건부 혜택이라 제외.
--   - 딜리버리·대중교통(버스/지하철)·슈퍼마켓: 카똑 지원 8개 업종 밖이라 제외.
--   - 『3일 쓰면 2배 적립』: 연속 이용일수 집계가 필요한 결제이력 기반 조건이라 계산 범위 밖. 기본 적립률만 반영.
-- 적립 기준: 하나머니(1하나머니=1원). 실적 조건 없음(ALLDAY 적립은 지난달 실적 무관).
-- "적립 영역이 중복되는 경우 적립금액이 큰 서비스가 우선 적용"(매출 건당 하나만) — 전가맹점 혜택과
-- 카테고리별 혜택이 데이터상 동시에 존재하는 건 정상이며, 더 큰 쪽을 고르는 건 계산 엔진의 몫으로 둔다.

update card_products
	set annual_fee = 20000, product_version = '2026-C1a-09124', data_status = 'CALCULABLE'
	where product_key = 'CR005';

do $$
declare
	v_product_id bigint;
	v_category_mart_id bigint;
	v_category_department_id bigint;
	v_category_online_id bigint;
	v_category_taxi_id bigint;
	v_cap_unlimited_id bigint;
	v_cap_shopping_taxi_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR005';
	select id into v_category_mart_id from merchant_categories where code = 'MART';
	select id into v_category_taxi_id from merchant_categories where code = 'TAXI';
	insert into merchant_categories (name, code)
		values ('백화점', 'DEPARTMENT_STORE')
		on conflict (code) do update set name = excluded.name
		returning id into v_category_department_id;
	select id into v_category_online_id from merchant_categories where code = 'ONLINE_SHOPPING';

	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', 'ALLDAY 국내외 가맹점 적립 — 한도 없음')
		returning id into v_cap_unlimited_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description)
		values (v_product_id, 'MONTHLY', 50000, 'ALLDAY 장보기쇼핑+택시 통합 적립한도')
		returning id into v_cap_shopping_taxi_id;

	insert into benefit_rules (
		product_id, cap_group_id, benefit_type, rate, point_krw_ratio,
		conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values (
		v_product_id, v_cap_unlimited_id, 'POINT_RATE', 0.005, 1.0,
		jsonb_build_object('allMerchants', true),
		'국내외 가맹점 0.5% 하나머니 적립(ALLDAY)', 'MOVING 상품안내장 p.4 ALLDAY 적립',
		'VERIFIED', now(), 1, current_date
	);

	insert into benefit_rules (
		product_id, category_id, cap_group_id, benefit_type, rate, point_krw_ratio,
		display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values
		(v_product_id, v_category_mart_id, v_cap_shopping_taxi_id, 'POINT_RATE', 0.02, 1.0,
			'마트 2% 하나머니 적립(ALLDAY)', 'MOVING 상품안내장 p.4 ALLDAY 적립', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_department_id, v_cap_shopping_taxi_id, 'POINT_RATE', 0.02, 1.0,
			'백화점 2% 하나머니 적립(ALLDAY)', 'MOVING 상품안내장 p.4 ALLDAY 적립', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_online_id, v_cap_shopping_taxi_id, 'POINT_RATE', 0.02, 1.0,
			'온라인쇼핑 2% 하나머니 적립(ALLDAY)', 'MOVING 상품안내장 p.4 ALLDAY 적립', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_category_taxi_id, v_cap_shopping_taxi_id, 'POINT_RATE', 0.03, 1.0,
			'택시 3% 하나머니 적립(ALLDAY)', 'MOVING 상품안내장 p.4 ALLDAY 적립', 'VERIFIED', now(), 1, current_date);
end $$;
