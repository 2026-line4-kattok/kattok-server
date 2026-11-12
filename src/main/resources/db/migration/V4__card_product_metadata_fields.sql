-- 125장 지원 카드 메타데이터(이름·카드사 등)를 먼저 채워넣고, 혜택 데이터는 검증되는 대로 채워넣기 위한 준비.
-- product_version(심의필 번호)·annual_fee(연회비)는 PDF 정밀 확인 전까지는 NULL로 둔다.

alter table card_products add column product_key varchar;
alter table card_products add column aliases text[];
alter table card_products add column image_file_name varchar;

alter table card_products alter column product_version drop not null;
alter table card_products alter column annual_fee drop not null;

-- 기존에 시딩된 알파벳카드S(V2)도 식별자를 채운다.
update card_products set product_key = 'CR006' where name = '알파벳카드S' and product_key is null;

alter table card_products alter column product_key set not null;
alter table card_products add constraint uq_card_products_product_key unique (product_key);
