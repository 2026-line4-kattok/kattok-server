-- 추천 결과 화면에서 추천 카드 대신 다른 카드로 결제하겠다고 고른 경우, 결제 확인 전까지 그 카드를 기억한다(4-7).
-- 추천 결과(recommendation_items.is_recommended)는 추천 당시 그대로 두고, 고른 카드는 결제 확인 대기 기록에만 남긴다.
-- 추천 카드를 그대로 골랐거나 아직 고르지 않았으면 NULL이다.
alter table payment_records add column chosen_user_card_id bigint references user_cards (id);
