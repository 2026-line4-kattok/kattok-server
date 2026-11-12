-- 화면에 보여줄 혜택 문구. 계산 조건이 아니므로 conditions에 안 넣고 별도 컬럼으로 둔다 (11/9 기획 전달 반영).

alter table benefit_rules add column display_text varchar;
