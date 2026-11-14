-- 카똑 지원 8개 업종별 대표 결제처를 적재한다. PM 확인용 초안(2026-11)이며, 쿠팡·스타벅스는 V2에서
-- 이미 적재돼 있어 충돌 방지를 위해 on conflict do nothing을 쓴다.

do $$
declare
	v_cat_convenience bigint;
	v_cat_cafe bigint;
	v_cat_restaurant bigint;
	v_cat_mart bigint;
	v_cat_online bigint;
	v_cat_pharmacy bigint;
	v_cat_taxi bigint;
	v_cat_department bigint;
begin
	select id into v_cat_convenience from merchant_categories where code = 'CONVENIENCE_STORE';
	select id into v_cat_cafe from merchant_categories where code = 'CAFE';
	select id into v_cat_restaurant from merchant_categories where code = 'RESTAURANT';
	select id into v_cat_mart from merchant_categories where code = 'MART';
	select id into v_cat_online from merchant_categories where code = 'ONLINE_SHOPPING';
	select id into v_cat_pharmacy from merchant_categories where code = 'PHARMACY';
	select id into v_cat_taxi from merchant_categories where code = 'TAXI';
	select id into v_cat_department from merchant_categories where code = 'DEPARTMENT_STORE';

	insert into merchants (name, channel, category_id) values
		('GS25', 'OFFLINE', v_cat_convenience),
		('CU', 'OFFLINE', v_cat_convenience),
		('세븐일레븐', 'OFFLINE', v_cat_convenience),
		('이마트24', 'OFFLINE', v_cat_convenience),
		('미니스톱', 'OFFLINE', v_cat_convenience),

		('스타벅스', 'OFFLINE', v_cat_cafe),
		('투썸플레이스', 'OFFLINE', v_cat_cafe),
		('폴바셋', 'OFFLINE', v_cat_cafe),
		('이디야', 'OFFLINE', v_cat_cafe),
		('메가MGC커피', 'OFFLINE', v_cat_cafe),
		('컴포즈커피', 'OFFLINE', v_cat_cafe),
		('커피빈', 'OFFLINE', v_cat_cafe),
		('할리스', 'OFFLINE', v_cat_cafe),
		('엔제리너스', 'OFFLINE', v_cat_cafe),
		('탐앤탐스', 'OFFLINE', v_cat_cafe),
		('카페베네', 'OFFLINE', v_cat_cafe),
		('파스쿠치', 'OFFLINE', v_cat_cafe),
		('매머드커피', 'OFFLINE', v_cat_cafe),
		('파리바게뜨', 'OFFLINE', v_cat_cafe),
		('뚜레쥬르', 'OFFLINE', v_cat_cafe),

		('아웃백스테이크하우스', 'OFFLINE', v_cat_restaurant),
		('VIPS', 'OFFLINE', v_cat_restaurant),
		('맘스터치', 'OFFLINE', v_cat_restaurant),

		('이마트', 'OFFLINE', v_cat_mart),
		('롯데마트', 'OFFLINE', v_cat_mart),
		('홈플러스', 'OFFLINE', v_cat_mart),
		('이마트 트레이더스', 'OFFLINE', v_cat_mart),
		('롯데 빅마켓', 'OFFLINE', v_cat_mart),
		('농협하나로마트', 'OFFLINE', v_cat_mart),
		('GS더프레시', 'OFFLINE', v_cat_mart),
		('롯데슈퍼', 'OFFLINE', v_cat_mart),
		('GS슈퍼', 'OFFLINE', v_cat_mart),

		('쿠팡', 'ONLINE', v_cat_online),
		('G마켓', 'ONLINE', v_cat_online),
		('옥션', 'ONLINE', v_cat_online),
		('11번가', 'ONLINE', v_cat_online),
		('티몬', 'ONLINE', v_cat_online),
		('위메프', 'ONLINE', v_cat_online),
		('무신사', 'ONLINE', v_cat_online),
		('29CM', 'ONLINE', v_cat_online),
		('네이버플러스스토어', 'ONLINE', v_cat_online),

		('온누리약국', 'OFFLINE', v_cat_pharmacy),
		('메디팜', 'OFFLINE', v_cat_pharmacy),
		('종로약국', 'OFFLINE', v_cat_pharmacy),

		('카카오T', 'OFFLINE', v_cat_taxi),

		('롯데백화점', 'OFFLINE', v_cat_department),
		('신세계백화점', 'OFFLINE', v_cat_department),
		('현대백화점', 'OFFLINE', v_cat_department)
	on conflict (name, channel) do nothing;
end $$;
