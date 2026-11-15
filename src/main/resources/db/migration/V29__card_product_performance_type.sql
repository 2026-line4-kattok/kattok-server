-- 카드가 실적 조건을 NONE(없음)/SINGLE(기준 1개)/MULTIPLE(구간 여러 개)/NEEDS_CHECK(확인 전) 중
-- 어느 방식으로 가지는지. 기존 데이터로 계산한다: 혜택 데이터가 검증 안 된 카드(UNVERIFIED·PARTIAL)는
-- NEEDS_CHECK, 검증된 카드(CALCULABLE)는 이미 적재된 실적 구간 개수로 판단한다.
alter table card_products add column performance_type varchar not null default 'NEEDS_CHECK';

update card_products
	set performance_type = 'NEEDS_CHECK'
	where data_status <> 'CALCULABLE';

update card_products
	set performance_type = 'NONE'
	where data_status = 'CALCULABLE'
		and id not in (select product_id from card_performance_tiers);

update card_products cp
	set performance_type = 'SINGLE'
	where data_status = 'CALCULABLE'
		and (select count(*) from card_performance_tiers t where t.product_id = cp.id) = 1;

update card_products cp
	set performance_type = 'MULTIPLE'
	where data_status = 'CALCULABLE'
		and (select count(*) from card_performance_tiers t where t.product_id = cp.id) >= 2;
