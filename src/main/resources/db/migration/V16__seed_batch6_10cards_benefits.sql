-- 6차 배치 10장: 현대카드 ZERO Up(포인트형)(CR092), BeV III 카드(CR094),
-- 현대카드ZERO Edition3(포인트형)(CR095), K-패스카드(CR096), 카드의정석2 DAILY(CR100),
-- KB Youth Club 체크카드(CK001), 트래블러스 체크카드(토심이)(CK003),
-- 신한카드 SOL트립앤J 체크(CK004), 노리2 체크카드(KB Pay)(CK005), 신한카드 SOL트래블 체크(CK007).
-- 체크카드(CK)는 심의필 번호 형식이 신용카드(여신금융협회 심의필)와 달라 각 카드사의
-- 준법감시인 심의필 코드를 그대로 product_version에 기록한다.
-- KB Youth Club 체크카드는 핵심 혜택이 전부 2종 선택형 패키지(A팩/B팩)에 묶여 있고
-- 어느 팩에도 전가맹점 기본 옵션이 없어 정책상 전체 제외, UNVERIFIED로 유지한다.

update card_products set annual_fee = 30000, data_status = 'CALCULABLE' where product_key = 'CR092';
update card_products set annual_fee = 210000, product_version = '2026-C1a-03929', data_status = 'CALCULABLE' where product_key = 'CR094';
update card_products set annual_fee = 15000, data_status = 'CALCULABLE' where product_key = 'CR095';
update card_products set annual_fee = 8000, data_status = 'CALCULABLE' where product_key = 'CR096';
update card_products set annual_fee = 25000, product_version = '2026-C1a-07818', data_status = 'CALCULABLE' where product_key = 'CR100';
update card_products set annual_fee = 0, product_version = '251117-05452-TRM' where product_key = 'CK001';
update card_products set annual_fee = 0, data_status = 'CALCULABLE' where product_key = 'CK003';
update card_products set annual_fee = 0, product_version = '20260406-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK004';
update card_products set annual_fee = 0, product_version = '260522-01722-PIF', data_status = 'CALCULABLE' where product_key = 'CK005';
update card_products set annual_fee = 0, product_version = '20260610-Dpp-001', data_status = 'CALCULABLE' where product_key = 'CK007';

-- 현대카드 ZERO Up(포인트형)(CR092): 기본 1.2% M포인트 + 온라인몰·대형마트 2.4%(10만원 이상).
-- 교육/주유/이동통신은 업종 밖.
do $$
declare
	v_product_id bigint; v_cat_online bigint; v_cat_mart bigint; v_cap_base bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR092';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 가맹점 1.2% M포인트 — 한도 없음') returning id into v_cap_base;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'POINT_RATE', 0.012, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 1.2% M포인트(기본)', '현대카드 ZERO Up(포인트형) 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, point_krw_ratio, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_online, v_cap_base, 'POINT_RATE', 0.024, 1.0, 100000, '온라인몰 2.4% M포인트(추가, 10만원 이상)', '현대카드 ZERO Up(포인트형) 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_base, 'POINT_RATE', 0.024, 1.0, 100000, '대형마트 2.4% M포인트(추가, 10만원 이상)', '현대카드 ZERO Up(포인트형) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- BeV III 카드(CR094): 요일별 기본적립(주중1.1%/주말1.3%) + 일상쇼핑(백화점·대형마트) 특별적립(주중1.3%/주말1.5%).
-- 쿠폰서비스(연1회 택1 상품권 등)는 1회성, 주유할인은 리터당 할인이라 제외.
do $$
declare
	v_product_id bigint;
	v_cat_department bigint; v_cat_mart bigint;
	v_cap_base bigint; v_cap_special bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR094';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본 적립 — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '일상쇼핑 특별 적립 — 한도 없음') returning id into v_cap_special;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cap_base, 'POINT_RATE', 0.011, 1.0, jsonb_build_object('allMerchants', true, 'daysOfWeek', jsonb_build_array('MON','TUE','WED','THU')), '전가맹점 1.1% 포인트리(기본, 주중)', 'BeV III 카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cap_base, 'POINT_RATE', 0.013, 1.0, jsonb_build_object('allMerchants', true, 'daysOfWeek', jsonb_build_array('FRI','SAT','SUN')), '전가맹점 1.3% 포인트리(기본, 주말)', 'BeV III 카드 안내장', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_department, v_cap_special, 'POINT_RATE', 0.013, 1.0, jsonb_build_object('daysOfWeek', jsonb_build_array('MON','TUE','WED','THU')), '백화점 1.3% 포인트리(특별, 주중)', 'BeV III 카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_department, v_cap_special, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('daysOfWeek', jsonb_build_array('FRI','SAT','SUN')), '백화점 1.5% 포인트리(특별, 주말)', 'BeV III 카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_special, 'POINT_RATE', 0.013, 1.0, jsonb_build_object('daysOfWeek', jsonb_build_array('MON','TUE','WED','THU')), '대형마트 1.3% 포인트리(특별, 주중)', 'BeV III 카드 안내장', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_cap_special, 'POINT_RATE', 0.015, 1.0, jsonb_build_object('daysOfWeek', jsonb_build_array('FRI','SAT','SUN')), '대형마트 1.5% 포인트리(특별, 주말)', 'BeV III 카드 안내장', 'VERIFIED', now(), 1, current_date);
end $$;

-- 현대카드ZERO Edition3(포인트형)(CR095): 기본혜택 국내외 1.2% M포인트만 반영(실적·한도 없음).
do $$
declare v_product_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR095';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 가맹점 1.2% M포인트 — 한도 없음') returning id into v_cap_id;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_id, 'POINT_RATE', 0.012, 1.0, jsonb_build_object('allMerchants', true), '국내외 가맹점 1.2% M포인트', '현대카드ZERO Edition3(포인트형) 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- K-패스카드(CR096, KB국민): 생활서비스(커피·약국·편의점·패스트푸드) 5%만 반영. 대중교통/이동통신/영화는
-- 업종 밖, KB Pay 추가할인은 결제수단 조건부라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_pharmacy bigint; v_cat_convenience bigint; v_cat_restaurant bigint;
	v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR096';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, '생활서비스 할인 통합 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, 30000, '커피업종 5% 청구할인', 'KB국민 K-패스카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_pharmacy, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, 30000, '약국업종 5% 청구할인', 'KB국민 K-패스카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_convenience, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, 30000, '편의점업종 5% 청구할인', 'KB국민 K-패스카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, 30000, '패스트푸드업종 5% 청구할인', 'KB국민 K-패스카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 카드의정석2 DAILY(CR100, 우리): 기본할인 1% + 고정소비(식비·장보기·교통·통신 중 음식점/온라인쇼핑·마트/택시) 5%.
-- 통신은 업종 밖, 정기결제(OTT 등 구독) 50%는 제외.
do $$
declare
	v_product_id bigint; v_t1 bigint; v_t2 bigint; v_t3 bigint;
	v_cat_restaurant bigint; v_cat_online bigint; v_cat_mart bigint; v_cat_taxi bigint;
	v_cap_base bigint; v_cap_t1 bigint; v_cap_t2 bigint; v_cap_t3 bigint;
begin
	select id into v_product_id from card_products where product_key = 'CR100';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상 100만원 미만') returning id into v_t1;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 2, 1000000, '100만원 이상 200만원 미만') returning id into v_t2;
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 3, 2000000, '200만원 이상') returning id into v_t3;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본할인 1% — 한도 없음') returning id into v_cap_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '고정소비 통합 한도(50~100만원)') returning id into v_cap_t1;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 6000, '고정소비 통합 한도(100~200만원)') returning id into v_cap_t2;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 12000, '고정소비 통합 한도(200만원 이상)') returning id into v_cap_t3;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cap_base, 'DISCOUNT_RATE', 0.01, jsonb_build_object('allMerchants', true), '국내외 가맹점 1% 청구할인(기본)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_restaurant, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '음식점 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '음식점 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '음식점 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '온라인쇼핑 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '온라인쇼핑 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_online, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '온라인쇼핑 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '대형마트 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '대형마트 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_mart, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '대형마트 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t1, v_cap_t1, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t2, v_cap_t2, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_taxi, v_t3, v_cap_t3, 'DISCOUNT_RATE', 0.05, '택시 5% 청구할인(고정소비)', '카드의정석2 DAILY 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- KB Youth Club 체크카드(CK001): A팩/B팩 선택형이며 어느 팩에도 전가맹점 기본 옵션이 없어
-- 전체 제외. 반영할 혜택이 없어 UNVERIFIED로 유지한다.

-- 트래블러스 체크카드(토심이)(CK003): 카페 1,000원 고정할인 + 전국맛집(푸딘코 선정) 5,000원 고정할인만 반영.
-- 해외가맹점할인/KB Pay추가할인/고속버스/빵집/주차장/철도는 해외·결제수단조건부·업종 밖이라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_restaurant bigint;
	v_cap_cafe bigint; v_cap_restaurant bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK003';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 1000, 1, '커피음료전문점 할인 월 1회') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, cap_count, description) values (v_product_id, 'MONTHLY', 5000, 1, '전국맛집 할인 월 1회') returning id into v_cap_restaurant;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, amount, min_payment_amount, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, v_cat_cafe, v_tier_id, v_cap_cafe, 'DISCOUNT_FIXED', 1000, 5000, '커피음료전문점 1,000원 할인', 'KB국민 트래블러스 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, v_cat_restaurant, null, v_cap_restaurant, 'DISCOUNT_FIXED', 5000, 20000, '전국맛집 5,000원 할인', 'KB국민 트래블러스 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 SOL트립앤J 체크(CK004): 국내 편의점 5%만 반영(전월실적 무관). 일본여행서비스(해외)와
-- 국내 대중교통은 각각 해외·업종 밖이라 제외.
do $$
declare v_product_id bigint; v_cat_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK004';
	select id into v_cat_id from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'YEARLY', 40000, '편의점 할인 연 한도(반기별 2만원)') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_id, v_cap_id, 'DISCOUNT_RATE', 0.05, '국내 편의점 5% 결제일 할인', '신한카드 SOL트립앤J 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;

-- 노리2 체크카드(KB Pay)(CK005): 커피 10% + 편의점 5%만 반영. 모바일/문화/뷰티/구독/배달/이동통신/영화/
-- 놀이공원과 KB Pay·Global 추가할인은 업종 밖·결제수단조건부·해외라 제외.
do $$
declare
	v_product_id bigint; v_tier_id bigint;
	v_cat_cafe bigint; v_cat_convenience bigint;
	v_cap_cafe bigint; v_cap_convenience bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK005';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 200000, '20만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '커피 할인 한도') returning id into v_cap_cafe;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 2000, '편의점 할인 한도') returning id into v_cap_convenience;
	insert into benefit_rules (product_id, category_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_cafe, v_cap_cafe, 'DISCOUNT_RATE', 0.10, '커피 10% 할인(실적 무관)', 'KB국민 노리2 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_convenience, v_tier_id, v_cap_convenience, 'DISCOUNT_RATE', 0.05, '편의점 5% 할인', 'KB국민 노리2 체크카드 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- 신한카드 SOL트래블 체크(CK007): 국내 4대 편의점 5%만 반영(전월실적 30만원 이상). 더라운지 공항라운지,
-- 해외 관련 서비스, 국내 대중교통은 업종 밖이라 제외.
do $$
declare v_product_id bigint; v_tier_id bigint; v_cat_id bigint; v_cap_id bigint;
begin
	select id into v_product_id from card_products where product_key = 'CK007';
	select id into v_cat_id from merchant_categories where code = 'CONVENIENCE_STORE';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 300000, '30만원 이상') returning id into v_tier_id;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 3000, '편의점 할인 한도') returning id into v_cap_id;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_cat_id, v_tier_id, v_cap_id, 'DISCOUNT_RATE', 0.05, '국내 4대 편의점 5% 결제일 할인', '신한카드 SOL트래블 체크 서비스 안내', 'VERIFIED', now(), 1, current_date);
end $$;
