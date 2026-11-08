-- 한도 그룹에 "상위 한도" 개념을 추가한다.
-- 예: 알파벳카드S "커피전문점 월 5천원"(영역별 한도)이 "커피전문점+편의점+대중교통 통합 월 1만원"
-- (상위 한도) 안에 포함되는 것처럼, 한도가 두 겹으로 동시에 걸리는 카드를 표현하기 위함
-- (docs/domain-model.md 5번 미해결 항목 1 "이중 한도").

alter table benefit_cap_groups
	add column parent_cap_group_id bigint references benefit_cap_groups (id);
