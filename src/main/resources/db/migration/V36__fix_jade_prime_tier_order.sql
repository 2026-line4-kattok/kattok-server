-- JADE Prime(CR090) 실적구간 순서 바로잡기. 카드 데이터 정합성 검사(CardDataIntegrityTest)에서 찾았다.
-- V15에서 level 1 = 200만원, level 2 = 50만원으로 거꾸로 넣어서 두 가지가 틀렸다.
--   1) 계산 엔진은 규칙의 실적구간이 사용자 구간과 정확히 같을 때만 적용한다. 택시 50% 규칙이 50만원 구간에만
--      묶여 있어서, 전월 200만원 이상인 사용자는 택시 혜택을 못 받았다.
--   2) 실적을 모를 때 엔진은 level이 가장 낮은 구간을 가정하는데, 그게 200만원 구간이었다.
-- 구간 id는 그대로 두고 level만 바꾸므로 사용자가 이미 고른 실적구간은 유지된다.
do $$
declare
	v_product_id bigint; v_tier_low bigint; v_tier_high bigint; v_taxi_rule benefit_rules%rowtype;
begin
	select id into v_product_id from card_products where product_key = 'CR090';
	select id into v_tier_low from card_performance_tiers where product_id = v_product_id and min_amount = 500000;
	select id into v_tier_high from card_performance_tiers where product_id = v_product_id and min_amount = 2000000;
	if v_tier_low is null or v_tier_high is null then
		raise exception 'JADE Prime 실적구간을 찾지 못했습니다.';
	end if;

	update card_performance_tiers set level = 1 where id = v_tier_low;
	update card_performance_tiers set level = 2 where id = v_tier_high;

	-- 택시 50%는 전월 50만원 이상이면 받으므로 200만원 구간에도 같은 규칙(같은 월 한도)을 둔다.
	select * into strict v_taxi_rule from benefit_rules where product_id = v_product_id and required_tier_id = v_tier_low;
	insert into benefit_rules (product_id, category_id, merchant_id, required_tier_id, cap_group_id, benefit_type, rate, amount,
		point_krw_ratio, min_payment_amount, max_eligible_txn_amount, conditions, display_text, source_ref,
		verification_status, verified_at, rule_version, valid_from, valid_to, stackable)
	values (v_product_id, v_taxi_rule.category_id, v_taxi_rule.merchant_id, v_tier_high, v_taxi_rule.cap_group_id,
		v_taxi_rule.benefit_type, v_taxi_rule.rate, v_taxi_rule.amount, v_taxi_rule.point_krw_ratio,
		v_taxi_rule.min_payment_amount, v_taxi_rule.max_eligible_txn_amount, v_taxi_rule.conditions, v_taxi_rule.display_text,
		v_taxi_rule.source_ref, v_taxi_rule.verification_status, now(), v_taxi_rule.rule_version, v_taxi_rule.valid_from,
		v_taxi_rule.valid_to, v_taxi_rule.stackable);
end $$;
