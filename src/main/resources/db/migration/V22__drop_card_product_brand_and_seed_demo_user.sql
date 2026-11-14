-- brand(국제 브랜드) 컬럼 삭제. 브랜드별 연회비 차이를 별도 상품 행으로 등록하는 설계였는데
-- 실제로는 그렇게 적재하지 않았고, PM 확인 결과 브랜드 구분 자체가 필요 없다고 확정됨.
alter table card_products drop column brand;

-- 회원가입·로그인은 정상적으로 만들고, 시연 때 실제 로그인할 테스트 계정을 미리 만들어 둔다(2026-11
-- 회의 결정). 로그인 구현 전까지는 이 계정을 "현재 사용자"로 고정해서 쓴다(CurrentUserProvider).
-- password_hash는 로그인 구현 때 해시 방식이 정해지면 이후 마이그레이션으로 채운다.
insert into users (email, password_hash, terms_agreed_at)
values ('demo@kattok.app', 'DEMO-NO-LOGIN', now());
