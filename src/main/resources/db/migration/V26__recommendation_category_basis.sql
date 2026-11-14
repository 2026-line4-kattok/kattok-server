-- 업종 칩만 골라 추천하는 경우(명세 1-1 basis = CATEGORY)를 저장할 수 있게 한다.
-- 결제처를 고르면 merchant_id와 그 결제처의 업종이, 업종만 고르면 category_id만 들어간다.
alter table recommendations
	add column category_id bigint references merchant_categories (id);

update recommendations r
	set category_id = m.category_id
	from merchants m
	where r.merchant_id = m.id;

alter table recommendations
	alter column category_id set not null,
	alter column merchant_id drop not null,
	alter column merchant_name_snapshot drop not null,
	add constraint recommendations_merchant_snapshot_check
		check ((merchant_id is null) = (merchant_name_snapshot is null));

-- 조건부 추천일 때 가정 없이 확인된 정보만으로 계산한 1위(명세 1-1 confirmedOnlyTopUserCardId).
-- 추천 카드와 같으면 null이다.
alter table recommendations
	add column confirmed_only_top_user_card_id bigint references user_cards (id);
