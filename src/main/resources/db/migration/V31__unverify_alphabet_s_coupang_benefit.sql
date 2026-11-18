-- 알파벳카드S의 쿠팡 기본혜택(10%)은 조건이 '{"channel":"ONLINE","installmentExcluded":true}'인데,
-- 계산 엔진은 channel·installmentExcluded를 읽지 않는다(RuleConditions 참고). 할부 결제에도
-- 무조건 10%를 계산해주는 오류로 이어질 수 있어 계산에서 뺀다.
--
-- 리뷰(hyojae02): benefit_cap_groups를 삭제하면 user_card_cap_usages(V1)·card_cap_balances(V28)가
-- 이 한도를 참조 중일 때 FK 위반으로 배포 중 Flyway가 실패할 수 있다. 그래서 행을 지우지 않고
-- verification_status만 UNVERIFIED로 바꾼다 — 추천 계산(findCalculableRules, existsSupportedRule)은
-- VERIFIED만 읽어서 바로 빠지고, 참조는 안 깨진다.
--
-- 같은 카드의 스타벅스 5%(OFFLINE)는 스타벅스가 애초에 OFFLINE 전용 가맹점이라 조건이 항상 참이라
-- 문제없이 그대로 둔다.
do $$
declare
	v_product_id bigint;
	v_merchant_coupang_id bigint;
begin
	select id into v_product_id from card_products where name = '알파벳카드S';
	select id into v_merchant_coupang_id from merchants where name = '쿠팡' and channel = 'ONLINE';

	update benefit_rules set verification_status = 'UNVERIFIED'
		where product_id = v_product_id and merchant_id = v_merchant_coupang_id;
end $$;
