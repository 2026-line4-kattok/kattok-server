-- 시연용 테스트 계정(demo@kattok.app)을 발표·부스용 깨끗한 상태로 맞춘다. 운영 서버에서만 실행한다.
-- 토큰 없는 요청을 테스트 계정으로 처리하던 기간(#69 이전)의 프론트 테스트 기록이 이 계정에 쌓여 있어서다.
--
-- 테스트는 빈 테스트 계정을 전제로 하므로, Flyway placeholder demoseed가 true일 때만 실행한다
-- (application.yml의 KATTOK_DEMO_SEED, 운영 배포에서만 true). 다른 환경에서는 아무것도 하지 않고 적용 기록만 남는다.
--
-- 1) 결제·추천·카드 기록을 탈퇴(UserAccountService.delete)와 같은 순서로 지운다. 계정 자체는 남긴다.
-- 2) 서비스 규칙을 하나씩 보여주는 시연용 카드 4장을 등록한다.
--    - BC 바로 KaPick(실적 15만원 확정): 브랜드 한정 혜택(쿠팡 7%, 11번가 없음), 작은 월 한도(쇼핑 1,500원)
--    - BC 바로 MACAO(실적 30만원 확정): 결제금액별 할인율(이마트 3만원 3%, 10만원 10%)
--    - 굿데이카드(실적 미확정): 실적에 따라 달라지는 조건부 추천(음식점 10%는 60만원 이상)
--    - 트래블로그 신용카드(실적 조건 없음): 전 가맹점 1% 기본 혜택
--    실적 확인은 월 단위라, 발표가 다음 달로 넘어가도 확정 상태가 유지되게 이번 달과 다음 달(한국 시간) 모두 넣는다.
do $$
declare
	v_user_id bigint; v_month date; v_card bigint; v_tier bigint;
	v_key text; v_min bigint;
begin
	if '${demoseed}' <> 'true' then
		return;
	end if;

	select id into v_user_id from users where email = 'demo@kattok.app';
	if v_user_id is null then
		raise exception '시연용 테스트 계정이 없습니다.';
	end if;

	delete from payment_cap_effects where payment_record_id in (select id from payment_records where user_id = v_user_id);
	delete from payment_records where user_id = v_user_id;
	delete from recommendation_items where recommendation_id in (select id from recommendations where user_id = v_user_id);
	delete from recommendations where user_id = v_user_id;
	delete from card_cap_balances where user_card_id in (select id from user_cards where user_id = v_user_id);
	delete from user_card_states where user_card_id in (select id from user_cards where user_id = v_user_id);
	delete from user_card_cap_usages where user_card_id in (select id from user_cards where user_id = v_user_id);
	delete from user_card_benefit_selections where user_card_id in (select id from user_cards where user_id = v_user_id);
	delete from user_cards where user_id = v_user_id;

	v_month := date_trunc('month', now() at time zone 'Asia/Seoul')::date;
	-- 실적 구간이 null이면 실적 상태를 넣지 않는다(미확정이거나 실적 조건이 없는 카드).
	for v_key, v_min in select * from (values ('CT04', 150000::bigint), ('CT06', 300000::bigint), ('CR004', null::bigint),
		('CT10', null::bigint)) as t(product_key, min_amount)
	loop
		insert into user_cards (user_id, product_id)
		select v_user_id, id from card_products where product_key = v_key
		returning id into strict v_card;

		if v_min is not null then
			select t.id into strict v_tier
			from card_performance_tiers t join card_products p on p.id = t.product_id
			where p.product_key = v_key and t.min_amount = v_min;

			insert into user_card_states (user_card_id, period_month, performance_tier_id, performance_status, source, confirmed_at)
			values
				(v_card, v_month, v_tier, 'CONFIRMED', 'USER_CONFIRMED', now()),
				(v_card, (v_month + interval '1 month')::date, v_tier, 'CONFIRMED', 'USER_CONFIRMED', now());
		end if;
	end loop;
end $$;
