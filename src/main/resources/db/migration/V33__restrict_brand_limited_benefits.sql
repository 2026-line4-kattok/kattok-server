-- 브랜드 한정 혜택("스타벅스 20%", "GS25 5%" 등)이 업종 전체(category_id)로 들어가 있어, 같은 업종의 다른
-- 결제처(메가MGC커피 등)와 업종만 고른 결제에도 계산됐다. 혜택 배치(V11~V21)를 넣을 때는 결제처가 쿠팡·
-- 스타벅스뿐이었고, 나머지 결제처는 V24에서 들어왔기 때문이다.
--
-- 규칙 행은 그대로 두고 conditions에 merchantIds(허용 결제처 목록)만 더한다(RuleConditions 참고).
-- 문구에 "등"이 붙은 4개(카페(스타벅스/이디야/커피빈 등) 등)는 업종 전체일 수 있어 원문 확인 전까지 둔다.
-- 결제처 테이블에 없는 브랜드(지그재그, 이마트에브리데이)는 고를 수 없으니 목록에서 뺀다.
create function pg_temp.restrict_to_merchants(p_key text, p_text text, p_names text[]) returns void
language plpgsql as $$
declare
	v_ids jsonb;
	v_found int;
	v_updated int;
begin
	select jsonb_agg(id order by id), count(*) into v_ids, v_found from merchants where name = any(p_names);
	if v_found <> cardinality(p_names) then
		raise exception '결제처 이름이 맞지 않습니다: % / %', p_key, p_names;
	end if;
	update benefit_rules r
		set conditions = coalesce(r.conditions, '{}'::jsonb) || jsonb_build_object('merchantIds', v_ids)
		from card_products p
		where r.product_id = p.id and p.product_key = p_key and r.display_text = p_text and r.category_id is not null;
	get diagnostics v_updated = row_count;
	if v_updated = 0 then
		raise exception '규칙을 찾지 못했습니다: % / %', p_key, p_text;
	end if;
end $$;

select pg_temp.restrict_to_merchants('CR070', '스타벅스 3,000원 할인', array['스타벅스']);

select pg_temp.restrict_to_merchants('CK010', '쿠팡/네이버플러스 스토어 20% 캐시백(Easy)', array['쿠팡', '네이버플러스스토어']);
select pg_temp.restrict_to_merchants('CK010', '스타벅스 20% 캐시백(Desire)', array['스타벅스']);
select pg_temp.restrict_to_merchants('CK010', '아웃백/VIPS 20% 캐시백(Desire)', array['아웃백스테이크하우스', 'VIPS']);

select pg_temp.restrict_to_merchants('CK020', '카카오T 택시 10% 캐시백(Life)', array['카카오T']);
select pg_temp.restrict_to_merchants('CK020', '무신사/29CM 10% 캐시백(Life)', array['무신사', '29CM']);

select pg_temp.restrict_to_merchants('CK021', '커피(스타벅스) 건당 1,000원 환급할인(1구간)', array['스타벅스']);
select pg_temp.restrict_to_merchants('CK021', '커피(스타벅스) 건당 2,000원 환급할인(2구간)', array['스타벅스']);

select pg_temp.restrict_to_merchants('CK022', '스타벅스 20% 환급할인', array['스타벅스']);
select pg_temp.restrict_to_merchants('CK022', '패밀리레스토랑(아웃백/VIPS) 20% 환급할인', array['아웃백스테이크하우스', 'VIPS']);
select pg_temp.restrict_to_merchants('CK022', 'GS25 5% 환급할인', array['GS25']);

select pg_temp.restrict_to_merchants('CK027', '편의점(CU/GS25/Emart24) 10% 청구할인(나라)', array['CU', 'GS25', '이마트24']);
select pg_temp.restrict_to_merchants('CK027', '택시(카카오T) 10% 청구할인(사랑)', array['카카오T']);
select pg_temp.restrict_to_merchants('CK027', '외식(아웃백스테이크하우스) 20% 현장할인(사랑)', array['아웃백스테이크하우스']);

select pg_temp.restrict_to_merchants('CK031', '스타벅스 사이렌오더 5% 캐시백(푸드)', array['스타벅스']);
select pg_temp.restrict_to_merchants('CK031', '쿠팡/무신사/29CM 5% 캐시백(일상)', array['쿠팡', '무신사', '29CM']);

select pg_temp.restrict_to_merchants('CK040', '택시(카카오T) 5% 하나머니(교통)', array['카카오T']);

select pg_temp.restrict_to_merchants('CK044', '아웃백스테이크하우스 10% 환급할인', array['아웃백스테이크하우스']);
select pg_temp.restrict_to_merchants('CK044', '롯데/신세계/현대백화점 5% 환급할인', array['롯데백화점', '신세계백화점', '현대백화점']);

select pg_temp.restrict_to_merchants('CK045', '편의점(CU/GS25/세븐일레븐) 5% 포인트 적립', array['CU', 'GS25', '세븐일레븐']);

select pg_temp.restrict_to_merchants('CK047', '온라인쇼핑(쿠팡/G마켓/옥션/11번가) 0.6% 청구할인', array['쿠팡', 'G마켓', '옥션', '11번가']);

select pg_temp.restrict_to_merchants('CK055', '편의점(CU/GS25/세븐일레븐/이마트24) 5% 하나머니 적립', array['CU', 'GS25', '세븐일레븐', '이마트24']);
select pg_temp.restrict_to_merchants('CK055', '커피/베이커리(스타벅스/커피빈/파리바게뜨/뚜레쥬르) 5% 하나머니 적립', array['스타벅스', '커피빈', '파리바게뜨', '뚜레쥬르']);

select pg_temp.restrict_to_merchants('CK057', '스타벅스(사이렌오더 포함)/커피빈 10% 할인(실적조건 없음)', array['스타벅스', '커피빈']);
select pg_temp.restrict_to_merchants('CK057', 'GS25/CU 5% 할인', array['GS25', 'CU']);

select pg_temp.restrict_to_merchants('CK058', '커피(스타벅스/폴바셋/투썸플레이스/매머드커피) 1% 특별적립', array['스타벅스', '폴바셋', '투썸플레이스', '매머드커피']);
select pg_temp.restrict_to_merchants('CK058', '쇼핑(이마트/롯데마트/홈플러스/GS더프레시/롯데슈퍼) 1% 특별적립', array['이마트', '롯데마트', '홈플러스', 'GS더프레시', '롯데슈퍼']);

select pg_temp.restrict_to_merchants('CK059', '온라인쇼핑(쿠팡/무신사/지그재그) 5% 캐시백(SHOPPING)', array['쿠팡', '무신사']);
select pg_temp.restrict_to_merchants('CK059', '스타벅스 사이렌오더 5% 캐시백(EAT)', array['스타벅스']);

select pg_temp.restrict_to_merchants('CK062', '스타벅스/카페베네/파스쿠치 5% 캐쉬백', array['스타벅스', '카페베네', '파스쿠치']);

select pg_temp.restrict_to_merchants('CK063', '스타벅스/커피빈 1% 캐쉬백', array['스타벅스', '커피빈']);

select pg_temp.restrict_to_merchants('CK065', '마트(이마트/트레이더스/롯데마트/농협하나로마트) 1% 특별 적립', array['이마트', '이마트 트레이더스', '롯데마트', '농협하나로마트']);
select pg_temp.restrict_to_merchants('CK065', '마트(이마트/트레이더스/롯데마트/농협하나로마트) 2% 특별 적립', array['이마트', '이마트 트레이더스', '롯데마트', '농협하나로마트']);

select pg_temp.restrict_to_merchants('CK066', '온라인쇼핑(쿠팡/G마켓/옥션) 0.5% 특별캐시백', array['쿠팡', 'G마켓', '옥션']);

select pg_temp.restrict_to_merchants('CK070', '소셜커머스(쿠팡/티몬/위메프) 5% 환급할인', array['쿠팡', '티몬', '위메프']);

select pg_temp.restrict_to_merchants('CK071', '쇼핑(쿠팡/티몬/위메프) 10% 할인', array['쿠팡', '티몬', '위메프']);
select pg_temp.restrict_to_merchants('CK071', '편의점(GS25/CU/세븐일레븐) 5% 할인', array['GS25', 'CU', '세븐일레븐']);

select pg_temp.restrict_to_merchants('CK077', '아웃백스테이크하우스/VIPS 10% 환급할인', array['아웃백스테이크하우스', 'VIPS']);
select pg_temp.restrict_to_merchants('CK077', 'G마켓/옥션 3% 환급할인', array['G마켓', '옥션']);

select pg_temp.restrict_to_merchants('CK079', '할인점(이마트/홈플러스/롯데마트) 0.3% 캐시백', array['이마트', '홈플러스', '롯데마트']);

select pg_temp.restrict_to_merchants('CK082', 'GS25편의점 0.2% 포인트리 추가적립', array['GS25']);

select pg_temp.restrict_to_merchants('CK087', '편의점(CU/GS25/세븐일레븐/미니스톱) 5% 청구할인', array['CU', 'GS25', '세븐일레븐', '미니스톱']);

select pg_temp.restrict_to_merchants('CK088', '쿠팡/무신사/29CM 5% 캐시백(쇼핑)', array['쿠팡', '무신사', '29CM']);
select pg_temp.restrict_to_merchants('CK088', '스타벅스 사이렌오더 5% 캐시백(푸드)', array['스타벅스']);

select pg_temp.restrict_to_merchants('CK090', '편의점/생활잡화(GS25/CU/세븐일레븐/이마트24) 1% 마이신한포인트 적립', array['GS25', 'CU', '세븐일레븐', '이마트24']);
select pg_temp.restrict_to_merchants('CK090', '편의점/생활잡화(GS25/CU/세븐일레븐/이마트24) 1.5% 마이신한포인트 적립', array['GS25', 'CU', '세븐일레븐', '이마트24']);
select pg_temp.restrict_to_merchants('CK090', '편의점/생활잡화(GS25/CU/세븐일레븐/이마트24) 2% 마이신한포인트 적립', array['GS25', 'CU', '세븐일레븐', '이마트24']);

select pg_temp.restrict_to_merchants('CK091', '대형마트(이마트/트레이더스/홈플러스/롯데마트) 10% 캐시백', array['이마트', '이마트 트레이더스', '홈플러스', '롯데마트']);

select pg_temp.restrict_to_merchants('CK094', '스타벅스(사이렌오더 포함)/커피빈 10% 환급할인(실적조건 없음)', array['스타벅스', '커피빈']);
select pg_temp.restrict_to_merchants('CK094', 'GS25/CU 5% 환급할인(일상혜택)', array['GS25', 'CU']);

select pg_temp.restrict_to_merchants('CK095', '할인점(이마트/롯데마트/홈플러스) 1% 마이신한포인트 적립(기본+쇼핑추가)', array['이마트', '롯데마트', '홈플러스']);
select pg_temp.restrict_to_merchants('CK095', '백화점(롯데/현대/신세계) 1% 마이신한포인트 적립(기본+쇼핑추가)', array['롯데백화점', '현대백화점', '신세계백화점']);
select pg_temp.restrict_to_merchants('CK095', '온라인몰(11번가/G마켓/옥션)·소셜커머스(쿠팡/티몬) 1% 마이신한포인트 적립(기본+쇼핑추가)', array['11번가', 'G마켓', '옥션', '쿠팡', '티몬']);

select pg_temp.restrict_to_merchants('CK098', 'SSM/대형마트(GS슈퍼/롯데슈퍼/이마트에브리데이/이마트/홈플러스/롯데마트) 0.6% 하나머니 적립(쇼핑)', array['GS슈퍼', '롯데슈퍼', '이마트', '홈플러스', '롯데마트']);
select pg_temp.restrict_to_merchants('CK098', '백화점(신세계/현대/롯데백화점) 0.6% 하나머니 적립(쇼핑)', array['신세계백화점', '현대백화점', '롯데백화점']);

drop function pg_temp.restrict_to_merchants(text, text, text[]);
