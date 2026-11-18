-- 알파벳카드S(V2, 명세 확정 전 초기 시딩)만 benefit_rules.display_text가 비어 있다. 보유 카드 목록의
-- 대표 혜택(OwnedCardService.cardExtras)은 적용 가능한 규칙 중 하나의 display_text를 그대로 보여주는데,
-- null이면 프론트가 그 값을 그대로 문자열로 다루다가 깨진다(TypeError: Cannot read properties of null
-- (reading 'replace')). 다른 124장과 같은 방식으로 문구를 채운다.
do $$
declare
	v_product_id bigint;
	v_merchant_coupang_id bigint;
	v_merchant_starbucks_id bigint;
begin
	select id into v_product_id from card_products where name = '알파벳카드S';
	select id into v_merchant_coupang_id from merchants where name = '쿠팡' and channel = 'ONLINE';
	select id into v_merchant_starbucks_id from merchants where name = '스타벅스' and channel = 'OFFLINE';

	update benefit_rules set display_text = '쿠팡 10% 할인'
		where product_id = v_product_id and merchant_id = v_merchant_coupang_id;
	update benefit_rules set display_text = '스타벅스 5% 할인'
		where product_id = v_product_id and merchant_id = v_merchant_starbucks_id;
end $$;
