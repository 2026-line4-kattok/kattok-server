-- 알파벳카드S의 쿠팡 기본혜택(10%)은 조건이 '{"channel":"ONLINE","installmentExcluded":true}'인데,
-- 계산 엔진은 channel·installmentExcluded를 읽지 않는다(RuleConditions 참고). 할부 결제에도
-- 무조건 10%를 계산해주는 오류로 이어질 수 있어 뺀다. 같은 카드의 스타벅스 5%(OFFLINE)는
-- 스타벅스가 애초에 OFFLINE 전용 가맹점이라 조건이 항상 참이라 문제없이 둔다.
do $$
declare
	v_product_id bigint;
	v_merchant_coupang_id bigint;
	v_cap_group_ids bigint[];
begin
	select id into v_product_id from card_products where name = '알파벳카드S';
	select id into v_merchant_coupang_id from merchants where name = '쿠팡' and channel = 'ONLINE';
	select array_agg(cap_group_id) into v_cap_group_ids from benefit_rules
		where product_id = v_product_id and merchant_id = v_merchant_coupang_id;

	delete from benefit_rules
		where product_id = v_product_id and merchant_id = v_merchant_coupang_id;

	delete from benefit_cap_groups
		where product_id = v_product_id and id = any(v_cap_group_ids);
end $$;
