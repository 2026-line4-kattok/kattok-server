-- 실적 구간 확인 상태를 performance_tier_id의 null 여부로만 구분하면 "확인했지만 모름"과
-- "확인했는데 최저 구간도 못 채움(BELOW)"을 구별할 수 없다. 상태를 명시적인 컬럼으로 분리한다.
alter table user_card_states
	add column performance_status varchar(20) not null default 'UNKNOWN';

update user_card_states
	set performance_status = 'CONFIRMED'
	where performance_tier_id is not null;
