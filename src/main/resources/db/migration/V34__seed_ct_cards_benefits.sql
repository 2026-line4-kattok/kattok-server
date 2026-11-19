-- 혜택 배치에서 빠져 있던 카드사별 TOP10 보완분(CT01~CT11) 11장의 혜택을 적재한다.
-- 원문은 카드사 상품설명서다. MACAO(CT06)와 트래블로그 신용(CT10)은 설명서 PDF가 이미지로만 되어 있어
-- 카드사 공식 상품 페이지 본문을 기준으로 했다.
--
-- 기준은 이전 배치와 같다: 8개 업종 밖 혜택, 페이(간편결제) 전용 혜택, 멤버십 가입 조건 혜택, 사용자
-- 이용 이력에 따른 혜택(전월 최다 이용 영역 등), 해외 이용 혜택은 넣지 않는다. 브랜드 한정 혜택은
-- 업종 규칙에 merchantIds(V33)를 붙이고, 결제처 테이블에 없는 브랜드는 목록에서 뺀다.
-- 실적 구간을 새로 넣으므로 performance_type(V29 계산값)도 직접 맞춘다.
create function pg_temp.merchant_ids(p_names text[]) returns jsonb
language plpgsql as $$
declare
	v_ids jsonb;
	v_found int;
begin
	select jsonb_agg(id order by id), count(*) into v_ids, v_found from merchants where name = any(p_names);
	if v_found <> cardinality(p_names) then
		raise exception '결제처 이름이 맞지 않습니다: %', p_names;
	end if;
	return v_ids;
end $$;

create function pg_temp.category_id(p_code text) returns bigint
language plpgsql as $$
declare
	v_id bigint;
begin
	select id into v_id from merchant_categories where code = p_code;
	if v_id is null then
		raise exception '업종 코드가 맞지 않습니다: %', p_code;
	end if;
	return v_id;
end $$;

-- CT01 DailyWith(데일리위드)카드: 6대 생활영역 1% 기본캐시백(한도 없음, 전월 50만원 이상).
-- 8개 업종에 해당하는 음식점·커피전문점·온라인쇼핑·대형마트·편의점만 넣는다. 통신비·병원비·교통은 업종 밖,
-- 추가캐시백(전월 최다 이용 영역 1%)은 사용자 이용 이력이 필요해 제외.
update card_products set annual_fee = 10000, product_version = '2026-8385', data_status = 'CALCULABLE', performance_type = 'SINGLE'
	where product_key = 'CT01';
do $$
declare
	v_product_id bigint; v_tier bigint; v_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT01';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '6대 생활영역 1% 기본캐시백 — 한도 없음') returning id into v_cap;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, pg_temp.category_id('RESTAURANT'), v_tier, v_cap, 'DISCOUNT_RATE', 0.01, null, '음식점 1% 기본캐시백', 'IBK DailyWith 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_cap, 'DISCOUNT_RATE', 0.01, null, '커피전문점 1% 기본캐시백', 'IBK DailyWith 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_cap, 'DISCOUNT_RATE', 0.01,
			jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡', '11번가', 'G마켓', '위메프'])),
			'온라인쇼핑(쿠팡/11번가/G마켓/위메프) 1% 기본캐시백', 'IBK DailyWith 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('MART'), v_tier, v_cap, 'DISCOUNT_RATE', 0.01,
			jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['이마트', '홈플러스', '롯데마트'])),
			'대형마트(이마트/홈플러스/롯데마트) 1% 기본캐시백', 'IBK DailyWith 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('CONVENIENCE_STORE'), v_tier, v_cap, 'DISCOUNT_RATE', 0.01,
			jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['GS25', 'CU', '세븐일레븐'])),
			'편의점(GS25/CU/세븐일레븐) 1% 기본캐시백', 'IBK DailyWith 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CT04 BC 바로 KaPick: 바로카드 Pick! 7% 카카오페이포인트 적립(전월 15만원 이상, 그룹별 월 한도).
-- 쇼픽(온쇼·패쇼 중 쿠팡/G마켓/무신사/29CM), 먹픽(커피·편의점), 생픽(카카오T 앱 자동결제)만 넣는다.
-- 오쇼(스타필드·올리브영·다이소)·배달·통신·대중교통·영화는 업종 밖, 국내 카카오페이 결제 1%는 페이 전용, 해외 2% 제외.
update card_products set annual_fee = 15000, product_version = '2026-C1a-11925', data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT04';
do $$
declare
	v_product_id bigint; v_tier bigint; v_shop bigint; v_eat bigint; v_life bigint;
	v_level int; v_min bigint; v_label text; v_shop_cap bigint; v_life_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT04';
	for v_level, v_min, v_label, v_shop_cap, v_life_cap in
		select * from (values (1, 150000, '15만원 이상', 1500, 3000), (2, 300000, '30만원 이상', 4000, 3000),
			(3, 600000, '60만원 이상', 8000, 6000), (4, 1000000, '100만원 이상', 15000, 6000)) as t(a, b, c, d, e)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_shop_cap, '쇼픽 월 적립한도(' || v_label || ')') returning id into v_shop;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_shop_cap, '먹픽 월 적립한도(' || v_label || ')') returning id into v_eat;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_life_cap, '생픽 월 적립한도(' || v_label || ')') returning id into v_life;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_shop, 'POINT_RATE', 0.07, 1.0,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡', 'G마켓', '무신사', '29CM'])),
				'쇼픽(쿠팡/G마켓/무신사/29CM) 7% 카카오페이포인트', 'BC 바로 KaPick 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_eat, 'POINT_RATE', 0.07, 1.0,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스', '이디야', '메가MGC커피', '컴포즈커피', '매머드커피'])),
				'먹픽 커피(스타벅스/이디야/메가MGC커피/빽다방/컴포즈커피/매머드커피) 7% 카카오페이포인트', 'BC 바로 KaPick 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('CONVENIENCE_STORE'), v_tier, v_eat, 'POINT_RATE', 0.07, 1.0,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['CU', 'GS25', '이마트24', '세븐일레븐'])),
				'먹픽 편의점(CU/GS25/이마트24/세븐일레븐) 7% 카카오페이포인트', 'BC 바로 KaPick 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('TAXI'), v_tier, v_life, 'POINT_RATE', 0.07, 1.0,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['카카오T'])),
				'생픽 카카오T(앱 자동결제) 7% 카카오페이포인트', 'BC 바로 KaPick 상품설명서', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT05 BC 바로 ZONE: ZONE1 7% 결제일할인(전월 30만원 이상, 영역별 월 한도) + ZONE2 전 가맹점 1%(실적·한도 없음).
-- EAT(커피·간식 중 결제처가 있는 곳)와 LIFE(온라인몰 중 쿠팡/무신사)만 넣는다. 배달·오프라인샵(다이소·올리브영)·
-- PLAY(구독·멤버십·선물하기)·&(전통시장)·해외는 제외. ZONE1이 적용된 결제 건은 ZONE2가 빠지고, 엔진은 큰 혜택 하나만 고른다.
update card_products set annual_fee = 19000, product_version = '2025-C1a-16209', data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT05';
do $$
declare
	v_product_id bigint; v_tier bigint; v_eat bigint; v_life bigint; v_base bigint;
	v_level int; v_min bigint; v_label text; v_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT05';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', 'ZONE2 1% — 한도 없음') returning id into v_base;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_base, 'DISCOUNT_RATE', 0.01, jsonb_build_object('allMerchants', true), '전 가맹점 1% 결제일할인(ZONE2)', 'BC 바로 ZONE 상품설명서', 'VERIFIED', now(), 1, current_date);
	for v_level, v_min, v_label, v_cap in
		select * from (values (1, 300000, '30만원 이상', 5000), (2, 600000, '60만원 이상', 10000)) as t(a, b, c, d)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_cap, 'EAT-ZONE 월 할인한도(' || v_label || ')') returning id into v_eat;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_cap, 'LIFE-ZONE 월 할인한도(' || v_label || ')') returning id into v_life;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_eat, 'DISCOUNT_RATE', 0.07,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스', '투썸플레이스', '이디야', '메가MGC커피', '컴포즈커피'])),
				'EAT-ZONE 커피(스타벅스/투썸플레이스/이디야/메가MGC커피/컴포즈커피) 7% 결제일할인', 'BC 바로 ZONE 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('RESTAURANT'), v_tier, v_eat, 'DISCOUNT_RATE', 0.07,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['맘스터치'])),
				'EAT-ZONE 간식(맥도날드/버거킹/롯데리아/맘스터치/써브웨이) 7% 결제일할인', 'BC 바로 ZONE 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_life, 'DISCOUNT_RATE', 0.07,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡', '무신사'])),
				'LIFE-ZONE 온라인몰(쿠팡/무신사/지그재그/오늘의집/크림) 7% 결제일할인', 'BC 바로 ZONE 상품설명서', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT08 어디로든 그린카드: 전월 30만원 이상일 때 적립, 구간별 통합 월 적립한도(30만 3만P / 60만 4만P).
-- 커피(스타벅스/폴바셋/이디야) 10%(월 5천P)와 국내 가맹점 0.2%(커피전문점 제외)만 넣는다.
-- 전기차·수소차 충전, 버스·지하철, 공유모빌리티, 고속버스는 업종 밖.
update card_products set annual_fee = 15000, product_version = '2026-C1a-11598', data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT08';
do $$
declare
	v_product_id bigint; v_tier bigint; v_total bigint; v_coffee bigint;
	v_level int; v_min bigint; v_label text; v_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT08';
	for v_level, v_min, v_label, v_cap in
		select * from (values (1, 300000, '30만원 이상', 30000), (2, 600000, '60만원 이상', 40000)) as t(a, b, c, d)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_cap, '통합 월 적립한도(' || v_label || ')') returning id into v_total;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 5000, v_total, '커피 월 적립한도(' || v_label || ')') returning id into v_coffee;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_coffee, 'POINT_RATE', 0.10, 1.0,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스', '폴바셋', '이디야'])),
				'커피(스타벅스/폴바셋/이디야) 10% 적립', '어디로든 그린카드 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, null, v_tier, v_total, 'POINT_RATE', 0.002, 1.0,
				jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(pg_temp.category_id('CAFE'))),
				'국내 가맹점 0.2% 적립', '어디로든 그린카드 상품설명서', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT09 디지로카 Paris: 국내 모든 가맹점 0.7%(실적·한도 없음) + 온라인쇼핑 2%(전월 50만원 이상, 통합 월 이용금액
-- 200만원까지 → 할인 한도 4만원). 멤버십 추가 3%는 멤버십 가입 조건이라 제외, 네이버페이는 페이 결제라 제외.
-- 심의필 번호는 PDF 글꼴 문제로 숫자가 읽히지 않아 product_version을 비워 둔다.
update card_products set annual_fee = 20000, data_status = 'CALCULABLE', performance_type = 'SINGLE'
	where product_key = 'CT09';
do $$
declare
	v_product_id bigint; v_tier bigint; v_base bigint; v_online bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT09';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '모든 가맹점 0.7% — 한도 없음') returning id into v_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 40000, '온라인 쇼핑 월 할인한도(월 이용금액 200만원)') returning id into v_online;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, null, null, v_base, 'DISCOUNT_RATE', 0.007, jsonb_build_object('allMerchants', true),
			'국내 모든 가맹점 0.7% 결제일할인', '디지로카 Paris 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_online, 'DISCOUNT_RATE', 0.02,
			jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡', '11번가', 'G마켓', '옥션'])),
			'온라인쇼핑(쿠팡/11번가/G마켓/옥션/SSG.COM) 2% 결제일할인', '디지로카 Paris 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CT11 the OPUS silver: 국내 가맹점 1% 기본적립(실적·한도 없음) + 특별적립Ⅰ 2%(백화점·온라인쇼핑, 전월 50만원 이상,
-- 통합 월 5만점) + 스타벅스 10% 청구할인(1만원 이상, 전월 50만원 이상, OTT/뷰티/여가 통합 월 5천원).
-- 특별적립Ⅱ(항공·호텔·면세점·골프·해외), 프리미엄 바우처, 인천공항 주차, 유튜브·올리브영·카카오T주차·CGV는 업종 밖.
update card_products set annual_fee = 150000, product_version = '2026-C1a-16260', data_status = 'CALCULABLE', performance_type = 'SINGLE'
	where product_key = 'CT11';
do $$
declare
	v_product_id bigint; v_tier bigint; v_base bigint; v_special bigint; v_leisure bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT11';
	insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, 1, 500000, '50만원 이상') returning id into v_tier;
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '기본적립 1% — 한도 없음') returning id into v_base;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 50000, '특별적립Ⅰ 통합 월 적립한도') returning id into v_special;
	insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', 5000, 'OTT/뷰티/여가 통합 월 할인한도') returning id into v_leisure;
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values
		(v_product_id, null, null, v_base, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true),
			'국내 가맹점 1% 기본적립', 'the OPUS silver 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('DEPARTMENT_STORE'), v_tier, v_special, 'POINT_RATE', 0.02, 1.0,
			jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['롯데백화점', '신세계백화점', '현대백화점'])),
			'백화점(롯데/신세계/현대) 2% 특별적립', 'the OPUS silver 상품설명서', 'VERIFIED', now(), 1, current_date),
		(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_special, 'POINT_RATE', 0.02, 1.0,
			jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡'])),
			'온라인쇼핑(쿠팡/SSG.COM/롯데ON/Hmall) 2% 특별적립', 'the OPUS silver 상품설명서', 'VERIFIED', now(), 1, current_date);
	insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, pg_temp.category_id('CAFE'), v_tier, v_leisure, 'DISCOUNT_RATE', 0.10, 10000,
		jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스'])),
		'스타벅스 10% 청구할인(1만원 이상)', 'the OPUS silver 상품설명서', 'VERIFIED', now(), 1, current_date);
end $$;

-- CT02 일상의 기쁨카드(신용): 전월 50만원 이상, 구간별 월간 통합할인한도(50만 2만 / 70만 3만 / 100만 4만원).
-- 대형마트 5%(월 1회, 1회 10만원까지), 주요 커피전문점 20%·쇼핑 20%(월 2회, 1회 2만원까지), 편의점 10%(월 2회,
-- 1회 1만원까지)만 넣는다. 일 1회 횟수 한도는 이전 배치처럼 생략. 영화·버스/지하철·주유·놀이공원·금융우대는 업종 밖,
-- 올리브영·달.콤·롯데홈쇼핑은 결제처가 없다.
update card_products set annual_fee = 10000, product_version = '2026-8420', data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT02';
do $$
declare
	v_product_id bigint; v_tier bigint; v_total bigint; v_mart bigint; v_coffee bigint; v_conv bigint; v_online bigint;
	v_level int; v_min bigint; v_label text; v_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT02';
	for v_level, v_min, v_label, v_cap in
		select * from (values (1, 500000, '50만원 이상', 20000), (2, 700000, '70만원 이상', 30000), (3, 1000000, '100만원 이상', 40000)) as t(a, b, c, d)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_cap, '월간 통합할인한도(' || v_label || ')') returning id into v_total;
		insert into benefit_cap_groups (product_id, cap_period, cap_count, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 1, v_total, '대형마트 월 1회(' || v_label || ')') returning id into v_mart;
		insert into benefit_cap_groups (product_id, cap_period, cap_count, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 2, v_total, '커피전문점 월 2회(' || v_label || ')') returning id into v_coffee;
		insert into benefit_cap_groups (product_id, cap_period, cap_count, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 2, v_total, '편의점 월 2회(' || v_label || ')') returning id into v_conv;
		insert into benefit_cap_groups (product_id, cap_period, cap_count, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 2, v_total, '쇼핑 월 2회(' || v_label || ')') returning id into v_online;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, max_eligible_txn_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('MART'), v_tier, v_mart, 'DISCOUNT_RATE', 0.05, 100000,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['이마트', '홈플러스', '롯데마트'])),
				'이마트/홈플러스/롯데마트 5% 할인(월 1회, 1회 10만원까지)', 'IBK 일상의 기쁨카드(신용) 개인설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_coffee, 'DISCOUNT_RATE', 0.20, 20000,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스', '커피빈', '카페베네', '탐앤탐스', '엔제리너스', '투썸플레이스', '할리스'])),
				'주요 커피전문점(스타벅스/커피빈/카페베네/탐앤탐스/엔제리너스/투썸플레이스/할리스/달.콤) 20% 할인(월 2회, 1회 2만원까지)', 'IBK 일상의 기쁨카드(신용) 개인설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('CONVENIENCE_STORE'), v_tier, v_conv, 'DISCOUNT_RATE', 0.10, 10000,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['GS25', 'CU', '세븐일레븐'])),
				'GS25/CU/세븐일레븐/올리브영 10% 할인(월 2회, 1회 1만원까지)', 'IBK 일상의 기쁨카드(신용) 개인설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_online, 'DISCOUNT_RATE', 0.20, 20000,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡', '티몬'])),
				'쿠팡/티몬/롯데홈쇼핑 20% 할인(월 2회, 1회 2만원까지)', 'IBK 일상의 기쁨카드(신용) 개인설명서', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT03 I-어디로든 그린카드: 어디로든 그린카드(CT08)와 같은 그린카드 혜택(IBK 발급). 전월 30만원 이상, 통합 월
-- 적립한도 30만 3만P / 60만 4만P, 커피(스타벅스/폴바셋/이디야) 10%(월 5천P), 국내 가맹점 0.2%(커피전문점 제외).
-- 친환경 자동차 충전, 공유 모빌리티, 대중교통, 고속버스는 업종 밖.
update card_products set annual_fee = 15000, product_version = '2026-8394', data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT03';
do $$
declare
	v_product_id bigint; v_tier bigint; v_total bigint; v_coffee bigint;
	v_level int; v_min bigint; v_label text; v_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT03';
	for v_level, v_min, v_label, v_cap in
		select * from (values (1, 300000, '30만원 이상', 30000), (2, 600000, '60만원 이상', 40000)) as t(a, b, c, d)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_cap, '통합 월 적립한도(' || v_label || ')') returning id into v_total;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', 5000, v_total, '커피전문점 월 적립한도(' || v_label || ')') returning id into v_coffee;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_coffee, 'POINT_RATE', 0.10, 1.0,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스', '폴바셋', '이디야'])),
				'커피전문점(스타벅스/폴바셋/이디야) 10% 에코머니 적립', 'IBK I-어디로든 그린카드 개인설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, null, v_tier, v_total, 'POINT_RATE', 0.002, 1.0,
				jsonb_build_object('allMerchants', true, 'excludedCategoryIds', jsonb_build_array(pg_temp.category_id('CAFE'))),
				'생활서비스(국내 가맹점) 0.2% 에코머니 적립', 'IBK I-어디로든 그린카드 개인설명서', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT06 BC 바로 MACAO(BC카드 공식 상품 페이지 본문 기준): 담을수록 할인(장보기) — 결제금액 10만원 이상 10%,
-- 5만원 이상 5%, 그 외 3%. 전월 30만원 이상, 구간별 장보기 영역 한도(1만/1.5만/2.5만원)와 월 통합 기본 한도
-- (1.5만/2만/3만원). 장바구니 한도(전월 5만원 이상 결제 5회 이상)는 이용 이력이 필요해 기본 한도로 넣는다.
-- 금액별 할인율은 최소 결제금액이 다른 규칙 3개로 넣고, 엔진이 그중 큰 혜택 하나를 고른다.
-- 주유는 업종 밖, 해외 2%는 제외. 네이버페이·컬리·SSG.COM·배민B마트·이마트 에브리데이·홈플러스 익스프레스·오아시스는 결제처가 없다.
update card_products set annual_fee = 12000, data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT06';
do $$
declare
	v_product_id bigint; v_tier bigint; v_total bigint; v_grocery bigint;
	v_level int; v_min bigint; v_label text; v_total_cap bigint; v_grocery_cap bigint;
	v_online jsonb; v_mart jsonb;
begin
	select id into v_product_id from card_products where product_key = 'CT06';
	v_online := jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡']));
	v_mart := jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['이마트', '홈플러스', '롯데마트', '롯데슈퍼', 'GS더프레시', '이마트 트레이더스']));
	for v_level, v_min, v_label, v_total_cap, v_grocery_cap in
		select * from (values (1, 300000, '30만원 이상', 15000, 10000), (2, 500000, '50만원 이상', 20000, 15000),
			(3, 1000000, '100만원 이상', 30000, 25000)) as t(a, b, c, d, e)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_total_cap, '월 통합 할인한도(' || v_label || ')') returning id into v_total;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', v_grocery_cap, v_total, '장보기 영역 할인한도(' || v_label || ')') returning id into v_grocery;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, min_payment_amount, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_grocery, 'DISCOUNT_RATE', 0.10, 100000, v_online,
				'장보기 쿠팡 10% 할인(10만원 이상 결제)', 'BC 바로 MACAO 상품 페이지', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_grocery, 'DISCOUNT_RATE', 0.05, 50000, v_online,
				'장보기 쿠팡 5% 할인(5만원 이상 결제)', 'BC 바로 MACAO 상품 페이지', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_grocery, 'DISCOUNT_RATE', 0.03, null, v_online,
				'장보기 쿠팡 3% 할인', 'BC 바로 MACAO 상품 페이지', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('MART'), v_tier, v_grocery, 'DISCOUNT_RATE', 0.10, 100000, v_mart,
				'장보기 마트(이마트/홈플러스/롯데마트/롯데슈퍼/GS더프레시/트레이더스 등) 10% 할인(10만원 이상 결제)', 'BC 바로 MACAO 상품 페이지', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('MART'), v_tier, v_grocery, 'DISCOUNT_RATE', 0.05, 50000, v_mart,
				'장보기 마트(이마트/홈플러스/롯데마트/롯데슈퍼/GS더프레시/트레이더스 등) 5% 할인(5만원 이상 결제)', 'BC 바로 MACAO 상품 페이지', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('MART'), v_tier, v_grocery, 'DISCOUNT_RATE', 0.03, null, v_mart,
				'장보기 마트(이마트/홈플러스/롯데마트/롯데슈퍼/GS더프레시/트레이더스 등) 3% 할인', 'BC 바로 MACAO 상품 페이지', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT07 신세계 BC 바로 콰트로 플러스: 청구할인, 전월 30만원 이상, 구간별 카테고리 한도와 통합 한도
-- (30만 1만/1.5만, 50만 1.5만/2.5만, 70만 2만/3.5만, 120만 3만/5.5만원). 쇼핑 5%(신세계백화점·이마트),
-- 교통 중 택시 4%, 온라인몰 7%, 생활 중 약국 5%, 커피/외식 10%만 넣는다. 주유·전기차 충전·대중교통·병원·휴대폰요금·
-- 신세계면세점은 업종 밖, SSG.COM은 결제처가 없다.
update card_products set annual_fee = 5000, data_status = 'CALCULABLE', performance_type = 'MULTIPLE'
	where product_key = 'CT07';
do $$
declare
	v_product_id bigint; v_tier bigint; v_total bigint;
	v_shop bigint; v_move bigint; v_online bigint; v_life bigint; v_food bigint;
	v_level int; v_min bigint; v_label text; v_cat_cap bigint; v_total_cap bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT07';
	for v_level, v_min, v_label, v_cat_cap, v_total_cap in
		select * from (values (1, 300000, '30만원 이상', 10000, 15000), (2, 500000, '50만원 이상', 15000, 25000),
			(3, 700000, '70만원 이상', 20000, 35000), (4, 1200000, '120만원 이상', 30000, 55000)) as t(a, b, c, d, e)
	loop
		insert into card_performance_tiers (product_id, level, min_amount, label) values (v_product_id, v_level, v_min, v_label) returning id into v_tier;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, description) values (v_product_id, 'MONTHLY', v_total_cap, '통합 할인한도(' || v_label || ')') returning id into v_total;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', v_cat_cap, v_total, '쇼핑 할인한도(' || v_label || ')') returning id into v_shop;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', v_cat_cap, v_total, '교통 할인한도(' || v_label || ')') returning id into v_move;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', v_cat_cap, v_total, '온라인몰 할인한도(' || v_label || ')') returning id into v_online;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', v_cat_cap, v_total, '생활 할인한도(' || v_label || ')') returning id into v_life;
		insert into benefit_cap_groups (product_id, cap_period, cap_amount, parent_cap_group_id, description) values (v_product_id, 'MONTHLY', v_cat_cap, v_total, '커피/외식 할인한도(' || v_label || ')') returning id into v_food;
		insert into benefit_rules (product_id, category_id, required_tier_id, cap_group_id, benefit_type, rate, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
		values
			(v_product_id, pg_temp.category_id('DEPARTMENT_STORE'), v_tier, v_shop, 'DISCOUNT_RATE', 0.05,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['신세계백화점'])),
				'쇼핑 신세계백화점 5% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('MART'), v_tier, v_shop, 'DISCOUNT_RATE', 0.05,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['이마트', '이마트 트레이더스'])),
				'쇼핑 이마트(에브리데이·트레이더스 포함) 5% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('TAXI'), v_tier, v_move, 'DISCOUNT_RATE', 0.04, null,
				'교통 택시 4% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('ONLINE_SHOPPING'), v_tier, v_online, 'DISCOUNT_RATE', 0.07,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['쿠팡', 'G마켓', '옥션', '11번가'])),
				'온라인몰(SSG.COM/쿠팡/G마켓/옥션/11번가) 7% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('PHARMACY'), v_tier, v_life, 'DISCOUNT_RATE', 0.05, null,
				'생활 약국 5% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('CAFE'), v_tier, v_food, 'DISCOUNT_RATE', 0.10,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['스타벅스', '투썸플레이스', '이디야', '폴바셋'])),
				'커피(스타벅스/투썸플레이스/이디야/폴바셋) 10% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date),
			(v_product_id, pg_temp.category_id('RESTAURANT'), v_tier, v_food, 'DISCOUNT_RATE', 0.10,
				jsonb_build_object('merchantIds', pg_temp.merchant_ids(array['아웃백스테이크하우스'])),
				'외식 아웃백 10% 청구할인', '신세계 BC 바로 콰트로 플러스 상품설명서', 'VERIFIED', now(), 1, current_date);
	end loop;
end $$;

-- CT10 트래블로그 신용카드(하나카드 공식 안내 페이지 본문 기준): 실적·한도 없이 국내외 전 가맹점 1% 하나머니 적립.
-- 하나Pay·간편결제 1.3%는 페이 결제, 해외·항공·면세점·여행 3%는 업종 밖이라 제외.
update card_products set annual_fee = 20000, data_status = 'CALCULABLE', performance_type = 'NONE'
	where product_key = 'CT10';
do $$
declare
	v_product_id bigint; v_base bigint;
begin
	select id into v_product_id from card_products where product_key = 'CT10';
	insert into benefit_cap_groups (product_id, cap_period, description) values (v_product_id, 'MONTHLY', '국내외 전 가맹점 1% — 한도 없음') returning id into v_base;
	insert into benefit_rules (product_id, cap_group_id, benefit_type, rate, point_krw_ratio, conditions, display_text, source_ref, verification_status, verified_at, rule_version, valid_from)
	values (v_product_id, v_base, 'POINT_RATE', 0.01, 1.0, jsonb_build_object('allMerchants', true),
		'국내외 전 가맹점 1% 하나머니 적립', '하나카드 트래블로그 신용카드 안내 페이지', 'VERIFIED', now(), 1, current_date);
end $$;

drop function pg_temp.merchant_ids(text[]);
drop function pg_temp.category_id(text);
