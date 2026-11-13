-- brand(국제 브랜드) 컬럼 삭제. 브랜드별 연회비 차이를 별도 상품 행으로 등록하는 설계였는데
-- 실제로는 그렇게 적재하지 않았고, PM 확인 결과 브랜드 구분 자체가 필요 없다고 확정됨.
alter table card_products drop column brand;

-- 로그인 없이 데모 계정 하나로 운영하기로 합의(팀 결정). 이 계정을 "현재 사용자"로 고정해서 쓴다.
insert into users (email, password_hash, terms_agreed_at)
values ('demo@kattok.app', 'DEMO-NO-LOGIN', now());
