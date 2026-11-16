-- 약관·출처로 중복 적용을 확인한 규칙만 담당자가 명시적으로 활성화한다.
-- 기존/합산 요율/중복 불가 규칙을 자동 분류하거나 역채움하지 않는다.
alter table benefit_rules add column stackable boolean not null default false;
comment on column benefit_rules.stackable is
    'Verified combinable benefits only. False keeps the existing exclusive single-rule calculation.';
