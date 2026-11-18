-- 알파벳카드S 쿠팡 기본 10%는 온라인·일시불 조건(channel, installmentExcluded)이 붙어 있다.
-- 추천 엔진은 결제 채널·결제수단을 판정하지 않으므로 이 규칙을 추천 계산에서 뺀다.
-- 행은 남겨 카드 상세에 추천 미반영 혜택으로 보이게 하고, 한도·기록 참조도 유지한다.
-- 같은 카드의 스타벅스 5%(channel OFFLINE)는 결제처 자체가 오프라인이라 그대로 둔다.
update benefit_rules r
set verification_status = 'UNVERIFIED',
	verified_at = null
from card_products p, merchants m
where r.product_id = p.id
	and r.merchant_id = m.id
	and p.product_key = 'CR006'
	and m.name = '쿠팡'
	and m.channel = 'ONLINE';
