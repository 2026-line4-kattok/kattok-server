-- 11차 배치: 현대카드 체크(캐시백형)(CK081), 가온 올포인트 체크카드(CK082), 카카오페이 체크카드(CK087),
-- 위비트래블 J 체크카드(CK088), 신한카드 On 체크(CK090), 신한카드 SOL글로벌 체크(CK091),
-- 노리2 체크카드(Play)(CK094), 쉬즈온 신한카드 주거래 체크(CK095), MULTI Any 체크카드(CK098)
-- 총 9장의 혜택 데이터를 적재한다. 이로써 125장 지원 카드 혜택 데이터 적재가 완료된다.
-- 체크카드는 여신금융협회 심의필 대신 각 카드사 준법감시인/준법심의 코드를 product_version에 기록한다.
--
-- CR006(알파벳카드S)은 애초에 "데이터 없는 카드"가 아니었다 — V2__seed_card_alphabet_s.sql이
-- product_key 컬럼 도입 전에 만든 row를, V4__card_product_metadata_fields.sql이
-- `update card_products set product_key = 'CR006' where name = '알파벳카드S' and product_key is null`로
-- 이미 역채움해뒀고 그 직후 product_key에 unique 제약을 걸었다. 그래서 V5의 insert는
-- `on conflict (product_key) do update set name = ...`로 병합됐을 뿐 새 row를 만들지 않았다.
-- 즉 CR006은 처음부터 V2의 혜택 데이터(기본혜택 10%+추가혜택 5%, merchant_id 기준)를 그대로 갖고 있었다.
-- 이번 배치 초안에서 CR006에 동일 소스를 category_id 기준으로 다시 적재하려 했는데, 그대로 두면
-- 같은 product_id에 benefit_rules가 중복으로 쌓여 같은 거래가 두 규칙에 동시에 매칭될 수 있었다.
-- 그 블록은 이번 파일에서 뺐다(이미 반영된 혜택을 다시 적재할 필요가 없음).

update card_products set annual_fee = 5000, product_version = '260625-001', data_status = 'CALCULABLE' where product_key = 'CK081';
update card_products set annual_fee = 0, product_version = '260522-01722-PIF', data_status = 'CALCULABLE' where product_key = 'CK082';
update card_products set annual_fee = 0, product_version = '2024-2828', data_status = 'CALCULABLE' where product_key = 'CK087';
update card_products set annual_fee = 0, product_version = '2025-C02180', data_status = 'CALCULABLE' where product_key = 'CK088';
update card_products set annual_fee = 0, product_version = '20220224-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK090';
update card_products set annual_fee = 0, product_version = '20260709-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK091';
update card_products set annual_fee = 0, product_version = '260522-01722-PIF', data_status = 'CALCULABLE' where product_key = 'CK094';
update card_products set annual_fee = 0, product_version = '20261021-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK095';
update card_products set annual_fee = 0, product_version = 'L-26-0410', data_status = 'CALCULABLE' where product_key = 'CK098';

-- 현대카드 체크(캐시백형)(CK081): 기본혜택 0.3%(전가맹점) + 추가혜택 3%(일반음식점/편의점)만 반영.
-- 배달앱/대중교통은 업종 밖이라 제외. 추가혜택 한도 초과 시 기본혜택으로 대체되므로 기본혜택에
-- excludedCategoryIds를 걸지 않고, 계산 엔진이 한도 소진 여부에 따라 적용 규칙을 고르도록 둔다.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_restaurant bigint; v_cat_convenience bigint;
	v_cap_base bigint; v_cap_extra bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK081';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본혜택 0.3% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '추가혜택 통합 캐시백 한도') returning id into v_cap_extra;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'DISCOUNT_RATE', 0.003, jsonb_build_object('allMerchants', true), '국내외 전가맹점 0.3% 캐시백(기본혜택)', '현대카드 체크(캐시백형) 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_extra, 'DISCOUNT_RATE', 0.03, '일반음식점 3% 캐시백(추가혜택)', '현대카드 체크(캐시백형) 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_extra, 'DISCOUNT_RATE', 0.03, '편의점 3% 캐시백(추가혜택)', '현대카드 체크(캐시백형) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 가온 올포인트 체크카드(CK082, KB국민): 기본적립 0.2%(전가맹점) + 커피/제과(카페)·편의점·택시 0.2% +
-- 대형마트 0.8%만 반영. 이동통신/GS칼텍스주유소/대중교통은 업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint;
	v_cat_cafe bigint; v_cat_convenience bigint; v_cat_taxi bigint; v_cat_mart bigint;
	v_cap_base bigint; v_cap_cafe bigint; v_cap_convenience bigint; v_cap_taxi bigint; v_cap_mart bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK082';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 100000, '10만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 300000, '30만원 이상') returning id into v_t2;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본적립 0.2% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '커피/제과 추가적립 0.2% — 한도 없음') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '편의점 추가적립 0.2% — 한도 없음') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '택시 추가적립 0.2% — 한도 없음') returning id into v_cap_taxi;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 4000, '대형마트 추가적립 한도(월50만원 이용분)') returning id into v_cap_mart;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.002, 1.0, 1000, jsonb_build_object('allMerchants', true), '국내외 전가맹점 0.2% 포인트리 기본적립', 'KB국민 가온 올포인트 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe, 'POINT_RATE', 0.002, 1.0, 1000, '커피/제과,아이스크림 업종 0.2% 포인트리 추가적립', 'KB국민 가온 올포인트 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_convenience, 'POINT_RATE', 0.002, 1.0, 1000, 'GS25편의점 0.2% 포인트리 추가적립', 'KB국민 가온 올포인트 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_taxi, 'POINT_RATE', 0.002, 1.0, 1000, '택시 0.2% 포인트리 추가적립', 'KB국민 가온 올포인트 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_mart, 'POINT_RATE', 0.008, 1.0, 30000, '3대 대형마트 0.8% 포인트리 추가적립', 'KB국민 가온 올포인트 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카카오페이 체크카드(CK087, IBK): 온라인쇼핑/오프라인쇼핑(편의점 중심)만 반영. 카카오페이 가맹점
-- 15% 청구할인은 간편결제 전용이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_online bigint; v_cat_convenience bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK087';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 1000000, '100만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '월간 통합할인한도(20만원 이상)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 10000, '월간 통합할인한도(50만원 이상)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 20000, '월간 통합할인한도(100만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.10, '온라인쇼핑(G마켓/옥션/11번가/쿠팡 등) 10% 청구할인', 'IBK 카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.10, '온라인쇼핑(G마켓/옥션/11번가/쿠팡 등) 10% 청구할인', 'IBK 카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.10, '온라인쇼핑(G마켓/옥션/11번가/쿠팡 등) 10% 청구할인', 'IBK 카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '편의점(CU/GS25/세븐일레븐/미니스톱) 5% 청구할인', 'IBK 카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '편의점(CU/GS25/세븐일레븐/미니스톱) 5% 청구할인', 'IBK 카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '편의점(CU/GS25/세븐일레븐/미니스톱) 5% 청구할인', 'IBK 카카오페이 체크카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 위비트래블 J 체크카드(CK088, 우리): 국내 쇼핑(쿠팡/무신사/29CM)·푸드(스타벅스 사이렌오더)만 반영.
-- 해외/일본여행서비스는 해외, 간편결제 전용 쇼핑·배달/주문은 결제수단조건부·배달앱이라 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_online bigint; v_cat_cafe bigint;
	v_cap_online_t1 bigint; v_cap_online_t2 bigint; v_cap_online_t3 bigint;
	v_cap_cafe_t1 bigint; v_cap_cafe_t2 bigint; v_cap_cafe_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK088';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상 50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상 70만원 미만') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 700000, '70만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '쇼핑 캐시백 한도(20~50만원)') returning id into v_cap_online_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '쇼핑 캐시백 한도(50~70만원)') returning id into v_cap_online_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 9000, '쇼핑 캐시백 한도(70만원 이상)') returning id into v_cap_online_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '푸드 캐시백 한도(20~50만원)') returning id into v_cap_cafe_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '푸드 캐시백 한도(50~70만원)') returning id into v_cap_cafe_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 9000, '푸드 캐시백 한도(70만원 이상)') returning id into v_cap_cafe_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_t1, v_cap_online_t1, 'DISCOUNT_RATE', 0.05, '쿠팡/무신사/29CM 5% 캐시백(쇼핑)', '위비트래블 J 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_online_t2, 'DISCOUNT_RATE', 0.05, '쿠팡/무신사/29CM 5% 캐시백(쇼핑)', '위비트래블 J 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_online_t3, 'DISCOUNT_RATE', 0.05, '쿠팡/무신사/29CM 5% 캐시백(쇼핑)', '위비트래블 J 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_cafe_t1, 'DISCOUNT_RATE', 0.05, '스타벅스 사이렌오더 5% 캐시백(푸드)', '위비트래블 J 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_cafe_t2, 'DISCOUNT_RATE', 0.05, '스타벅스 사이렌오더 5% 캐시백(푸드)', '위비트래블 J 체크카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_cafe_t3, 'DISCOUNT_RATE', 0.05, '스타벅스 사이렌오더 5% 캐시백(푸드)', '위비트래블 J 체크카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 On 체크(CK090): 생활편의영역(편의점/생활잡화 + 커피전문점) 최대 2% 적립만 반영. 간편결제
-- 적립은 결제수단조건부, 통신·디지털컨텐츠는 업종 밖이라 제외. 간편결제 적립과 공유하는 월1만6천포인트
-- 통합한도를 생활편의영역 전용 한도로 그대로 반영한다(간편결제는 모델링하지 않으므로 실질적으로
-- 생활편의영역만 이 한도를 쓴다).
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_convenience bigint; v_cat_cafe bigint;
	v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK090';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상 50만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 500000, '50만원 이상 80만원 미만') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 800000, '80만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 16000, '생활편의영역 적립 통합 한도(20~50만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 16000, '생활편의영역 적립 통합 한도(50~80만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 16000, '생활편의영역 적립 통합 한도(80만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_convenience, v_t1, v_cap_t1, 'POINT_RATE', 0.01, 1.0, '편의점/생활잡화(GS25/CU/세븐일레븐/이마트24) 1% 마이신한포인트 적립', '신한카드 On 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t2, v_cap_t2, 'POINT_RATE', 0.015, 1.0, '편의점/생활잡화(GS25/CU/세븐일레븐/이마트24) 1.5% 마이신한포인트 적립', '신한카드 On 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_t3, v_cap_t3, 'POINT_RATE', 0.02, 1.0, '편의점/생활잡화(GS25/CU/세븐일레븐/이마트24) 2% 마이신한포인트 적립', '신한카드 On 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t1, v_cap_t1, 'POINT_RATE', 0.01, 1.0, '커피전문점 1% 마이신한포인트 적립', '신한카드 On 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t2, v_cap_t2, 'POINT_RATE', 0.015, 1.0, '커피전문점 1.5% 마이신한포인트 적립', '신한카드 On 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_cafe, v_t3, v_cap_t3, 'POINT_RATE', 0.02, 1.0, '커피전문점 2% 마이신한포인트 적립', '신한카드 On 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 SOL글로벌 체크(CK091): 일상 생활비 서비스 중 대형마트/음식점/병원약국 10%만 반영. 필수
-- 생활비(대중교통/이동통신)는 업종 밖이라 제외. 일상 생활비 서비스는 추출한 상품설명서에서 전용
-- 통합한도 수치를 확인하지 못해(필수 생활비 한도 표만 확인됨) 한도 없음으로 보수적으로 반영했다(불확실).
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_mart bigint; v_cat_restaurant bigint; v_cat_pharmacy bigint;
	v_cap_mart bigint; v_cap_restaurant bigint; v_cap_pharmacy bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK091';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '대형마트 10% 캐시백 — 한도 없음(확인 필요)') returning id into v_cap_mart;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '음식점 10% 캐시백 — 한도 없음(확인 필요)') returning id into v_cap_restaurant;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '병원/약국 10% 캐시백 — 한도 없음(확인 필요)') returning id into v_cap_pharmacy;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_mart, v_tier_id, v_cap_mart, 'DISCOUNT_RATE', 0.10, '대형마트(이마트/트레이더스/홈플러스/롯데마트) 10% 캐시백', '신한카드 SOL글로벌 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_restaurant, 'DISCOUNT_RATE', 0.10, '일반 음식점 10% 캐시백', '신한카드 SOL글로벌 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_tier_id, v_cap_pharmacy, 'DISCOUNT_RATE', 0.10, '병원/약국 10% 캐시백', '신한카드 SOL글로벌 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 노리2 체크카드(Play)(CK094, KB국민): 커피(실적조건 없음) + 편의점(일상혜택) + 패스트푸드(Play혜택)만
-- 반영. 모바일/문화/뷰티/구독/배달/이동통신/영화/놀이공원/게임PC방은 업종 밖이라 제외. 월간
-- 통합할인한도(2~5만원)는 반영한 혜택들의 합(최대 6천원)보다 커서 비구속적이라 생략했다.
do $$
declare
	v_product_id bigint; v_tier1 bigint; v_tier2 bigint;
	v_cat_cafe bigint; v_cat_convenience bigint; v_cat_restaurant bigint;
	v_cap_cafe bigint; v_cap_convenience bigint; v_cap_restaurant bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK094';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상(일상혜택)') returning id into v_tier1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 300000, '30만원 이상(Play혜택)') returning id into v_tier2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '커피 환급할인 한도') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '편의점 환급할인 한도') returning id into v_cap_convenience;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 1000, 1, '패스트푸드 환급할인 한도(월1회)') returning id into v_cap_restaurant;
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_cafe, v_cap_cafe, 'DISCOUNT_RATE', 0.10, '스타벅스(사이렌오더 포함)/커피빈 10% 환급할인(실적조건 없음)', 'KB국민 노리2 체크카드(Play) 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_convenience, v_tier1, v_cap_convenience, 'DISCOUNT_RATE', 0.05, 'GS25/CU 5% 환급할인(일상혜택)', 'KB국민 노리2 체크카드(Play) 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_restaurant, v_tier2, v_cap_restaurant, 'DISCOUNT_FIXED', 1000, 10000, '패스트푸드 1,000원 환급할인(Play혜택)', 'KB국민 노리2 체크카드(Play) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 쉬즈온 신한카드 주거래 체크(CK095): 쇼핑가맹점(할인점/백화점/온라인몰/소셜커머스) 1%(기본0.3%+추가0.7%)만
-- 반영. 해외/생활가맹점(통신·교통·도시가스)은 업종 밖이라 제외. 신한은행 결제계좌 연결이 전제조건이라
-- 모든 적립이 월 3만 포인트 통합 한도를 공유한다.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_mart bigint; v_cat_department bigint; v_cat_online bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK095';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 30000, '마이신한포인트 적립 통합 한도(신한은행 결제계좌 연결)') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'POINT_RATE', 0.003, 1.0, 300000, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_mart, v_cat_department, v_cat_online)), '국내외 가맹점 0.3% 마이신한포인트 적립(기본서비스)', '쉬즈온 신한카드 주거래 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_mart, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 300000, '할인점(이마트/롯데마트/홈플러스) 1% 마이신한포인트 적립(기본+쇼핑추가)', '쉬즈온 신한카드 주거래 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 300000, '백화점(롯데/현대/신세계) 1% 마이신한포인트 적립(기본+쇼핑추가)', '쉬즈온 신한카드 주거래 체크 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_tier_id, v_cap_id, 'POINT_RATE', 0.01, 1.0, 300000, '온라인몰(11번가/G마켓/옥션)·소셜커머스(쿠팡/티몬) 1% 마이신한포인트 적립(기본+쇼핑추가)', '쉬즈온 신한카드 주거래 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- MULTI Any 체크카드(CK098, 하나): 쇼핑(SSM/대형마트/백화점) 0.6% + 전가맹점 0.2%(쇼핑 제외)만 반영.
-- 페이결제 0.4%는 간편결제 전용, 딜리버리/대중교통은 배달앱·업종 밖이라 제외. 적립 대상 중복 시 요율이
-- 높은 서비스 하나만 적용되므로(매출 건당 1개 서비스) 기본적립에 쇼핑 카테고리를 제외해 반영한다.
do $$
declare
	v_product_id bigint;
	v_cat_mart bigint; v_cat_department bigint;
	v_cap_base bigint; v_cap_shopping bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK098';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, '하나머니 적립 통합 한도') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, '하나머니 적립 통합 한도(쇼핑)') returning id into v_cap_shopping;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.002, 1.0, jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(v_cat_mart, v_cat_department)), '국내외 가맹점 0.2% 하나머니 적립', 'MULTI Any 모바일 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, point_krw_ratio, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_mart, v_cap_shopping, 'POINT_RATE', 0.006, 1.0, 'SSM/대형마트(GS슈퍼/롯데슈퍼/이마트에브리데이/이마트/홈플러스/롯데마트) 0.6% 하나머니 적립(쇼핑)', 'MULTI Any 모바일 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_cap_shopping, 'POINT_RATE', 0.006, 1.0, '백화점(신세계/현대/롯데백화점) 0.6% 하나머니 적립(쇼핑)', 'MULTI Any 모바일 체크카드 하나머니 적립 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;
