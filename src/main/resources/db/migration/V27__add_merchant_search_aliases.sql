-- 결제처 검색용 별칭. 기존 결제처 ID·이름·채널은 유지한다.
alter table merchants add column aliases text[] not null default '{}';

update merchants set aliases = array['Starbucks', '스벅', '스타벅스커피']
where name = '스타벅스' and channel = 'OFFLINE';

update merchants set aliases = array['씨유']
where name = 'CU' and channel = 'OFFLINE';

update merchants set aliases = array['지에스25', '지에스이십오']
where name = 'GS25' and channel = 'OFFLINE';

update merchants set aliases = array['메가커피']
where name = '메가MGC커피' and channel = 'OFFLINE';
