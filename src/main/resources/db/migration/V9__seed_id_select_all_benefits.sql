-- 정답 케이스 카드: 삼성 iD SELECT ALL 카드(CR003).
-- 출처: 삼성 iD SELECT ALL 카드 상품설명서, 여신금융협회 심의필 2025-C1a-09519(2025.07.07)
-- 간소화/제외 (기획 11/9 "선택형은 기본 모드만 반영" 규칙 적용):
--   - SELECT 서비스1(택1: 전가맹점 0.7% / 아파트관리비·통신 10% / 교육 10%) 중
--     전가맹점 옵션만 "기본값"으로 가정해 반영, 나머지 두 옵션은 선택형이라 제외.
--   - SELECT 서비스2(택1: 온라인쇼핑몰·의료·배달앱 7% / 음식점·편의점·할인점·주유 7%)는
--     둘 다 특정 영역 한정이라 "기본값"이 없어 전체 제외.
--   - 기본서비스(생활잡화·공연·서점·보틀숍 5%), 디지털콘텐츠/멤버십 50%, 해외 2%는
--     카똑 지원 8개 업종 밖이라 제외.
-- 결과적으로 이 카드는 "국내 가맹점 0.7% 할인(실적·한도 없음, 택시 등 일부 제외)" 하나만 반영한다.

update card_products
	set annual_fee = 20000, product_version = '2025-C1a-09519', data_status = 'CALCULABLE'
	where product_key = 'CR003';

do $$
declare
	v_product_id bigint;
	v_category_taxi_id bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR003';
	select id into v_category_taxi_id from merchant_categories where code = 'TAXI';

	-- 한도 없는 혜택이라도 cap_group_id는 not null이라, 상한 없는 그룹을 하나 만든다.
	insert into benefit_cap_groups (product_id, cap_period, description)
		values (v_product_id, 'MONTHLY', '국내 가맹점 0.7% 할인 — 한도 없음')
		returning id into v_cap_id;

	insert into benefit_rules (
		product_id, cap_group_id, benefit_type, rate,
		conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from
	) values (
		v_product_id, v_cap_id, 'DISCOUNT_RATE', 0.007,
		jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_category_taxi_id)),
		'국내 가맹점 0.7% 할인', 'iD SELECT ALL 상품설명서 p.3 국내 가맹점 0.7% 할인',
		'VERIFIED', now(), 1, current_date
	);
end $$;
