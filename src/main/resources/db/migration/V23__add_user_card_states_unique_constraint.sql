-- user_card_cap_usages는 (user_card_id, cap_group_id, period_month) 유니크 제약이 있는데
-- user_card_states는 (user_card_id, period_month) 유니크 제약이 빠져 있었다. 카드 상태 수정(PUT) API가
-- ON CONFLICT로 한 달 상태를 upsert하려면 이 제약이 필요하다(docs/conventions.md "중복 방지" 규칙).
alter table user_card_states
	add constraint uq_user_card_states_card_month unique (user_card_id, period_month);
