# 도메인 모델 (ERD 변경안)

## 기준

- 베이스: [카똑 ERD (백엔드)](https://app.notion.com/p/40e87a520b4e81ae86fac1098967b9a3) — 13개 테이블(users, card_issuer, card_product, card_performance_tier, merchant_category, merchant, merchant_alias, benefit_rule, user_card, user_card_state, recommendation, recommendation_item, payment_record)
- 근거 데이터: 실제 카드 상품설명서/이용안내장 9종 (카드사 5곳: KB국민, 신한, 삼성, 하나, 현대)을 직접 분석해서 현재 ERD로 못 담는 패턴을 찾고, 그걸 기준으로 변경안을 정리함. "미확정"으로 남아있던 항목(상품·약관 버전 구분 방식, conditions jsonb 스키마 등)도 실데이터 기준으로 구체화함.
- 이 문서는 초안입니다. 실제 데이터 적재하면서 계속 다듬일 걸 전제로 작성했고, 5번(미해결 항목)에 아직 확정 못한 부분을 따로 모아뒀습니다.

## 1. 분석한 카드 목록과 패턴 분류

| 카드 | 카드사 | 패턴 | 비고 |
| --- | --- | --- | --- |
| 알파벳카드S | 현대카드 | 고정형 | 모든 혜택 동시 적용, 다업종 공유 한도 있음 |
| 굿데이카드 | KB국민카드 | 고정형 | 3단계 실적구간, 다업종 공유 한도 있음 |
| 신한카드 Mr.Life | 신한카드 | 고정형 | 시간대/요일 조건 있음, 리터당 혜택 1건(제외 대상) |
| 모니모페이카드 | 삼성카드 | 고정형 | 실적구간이 2축(이용금액 × 앱 로그인) — 아래 5번 참고 |
| 삼성 iD SELECT ALL | 삼성카드 | 혼합형 | 고정 혜택 + SELECT1/SELECT2 각 택1 |
| My WE:SH | KB국민카드 | 혼합형 | 고정 혜택("나한테 진심") + 3팩 중 택1("더욱 진심") |
| 삼성 & MILEAGE PLATINUM(스카이패스) | 삼성카드 | 혼합형 | 고정 마일리지 적립 + 해외형/국내형 택1 |
| THE 1(스카이패스) | 삼성카드 | 혼합형 | 고정 마일리지 + 택1 + 연1회 선택형 비거래 혜택(GIFT) |
| MOVING(무빙카드) | 하나카드 | 모드형 | ALLDAY/ONLINE/PLAY/LIFE/GLOBAL 5개 모드가 혜택 세트 전체를 결정 |

(VISA_I_004_메리어트.pdf는 특정 카드 상품이 아니라 VISA Infinite 등급 공통 서비스 안내장이라 상품 데이터 대상에서 제외)

**핵심 결론**: "고정형 카드 vs 선택형 카드"가 아니라, **혜택 규칙 하나하나가 고정인지 선택인지**가 갈립니다. 9장 중 5장이 선택 요소를 포함하고 있어서, 스키마는 처음부터 선택형을 지원하도록 설계합니다 (구현 범위는 4번 참고).

## 2. ERD 변경사항 요약

| 테이블 | 변경 | 사유 | 근거 카드 |
| --- | --- | --- | --- |
| `card_product` | 컬럼 추가: `annual_fee`, `brand` | 연회비·브랜드 정보가 기존 스키마에 없음 | 전 카드 공통 |
| `benefit_rule` | 컬럼 추가: `select_group`, `select_option`, `max_eligible_txn_amount` | 선택형 혜택, "할인 적용 대상 결제금액 상한" 표현 | iD SELECT ALL, My WE:SH, MOVING, MILEAGE PLATINUM, THE1 / 신한 Mr.Life |
| `benefit_rule` | 컬럼 삭제(이동): `monthly_cap_amount`, `monthly_cap_count`, `per_txn_cap` → `benefit_cap_group`로 이동 | 여러 업종이 한도 하나를 공유하는 패턴이 대부분의 카드에서 나타남 | 거의 전 카드 |
| `benefit_rule` | `benefit_type` enum 확장: `MILE_RATE`, `MILE_FIXED` 추가 | 항공 마일리지는 기존 4개 타입에 안 맞음 | THE1, MILEAGE PLATINUM |
| `benefit_rule` | `conditions` jsonb 최소 키 확정 | 미확정 상태였던 걸 실데이터 기준으로 구체화 | 아래 3-3 참고 |
| (신규) `benefit_cap_group` | 테이블 추가 | 한도 공유 그룹을 1급 개체로 분리 | 거의 전 카드 |
| (신규, P1) `user_card_benefit_selection` | 테이블 추가 | 사용자가 고른 선택형 옵션을 저장 | iD SELECT ALL, My WE:SH, MOVING, MILEAGE PLATINUM, THE1 |

## 3. 테이블별 상세 스키마

### 3-1. `card_product` (컬럼 추가)

| 컬럼 | 타입 | 조건 | 설명 |
| --- | --- | --- | --- |
| `annual_fee` | bigint | not null | 연회비(원). 해외겸용/국내전용처럼 연회비가 다른 변형은 **별도의 `card_product` 행**으로 등록(실제로 카드사도 별개 상품코드로 관리함) |
| `brand` | varchar | nullable | 국제 브랜드(VISA/Mastercard/AMEX 등). 국내전용이면 NULL |

**`product_version` 사용법 확정**: 파일명이나 다운로드 날짜가 아니라 **PDF 안에 인쇄된 여신금융협회 심의필 문서번호**를 그대로 저장합니다. 예: `A-20100826-9227-00149-07`, `2026-C1a-09920`. (KB 굿데이카드와 My WE:SH처럼 파일명 패턴이 같아서 "같은 상품의 다른 버전"처럼 보여도 실제로는 완전히 다른 상품인 경우가 있었음 — 파일명으로 버전을 추정하면 안 됨)

### 3-2. `benefit_rule` (컬럼 추가/삭제)

**추가**

| 컬럼 | 타입 | 조건 | 설명 |
| --- | --- | --- | --- |
| `select_group` | varchar | nullable | 선택형 그룹 식별자 (예: `SELECT_1`, `MODE`, `SPECIAL_REWARD`). NULL = 항상 적용되는 고정 혜택 |
| `select_option` | varchar | `select_group`이 있으면 not null | 그룹 내 옵션 식별자 (예: `LIFE`/`EDU`, `ALLDAY`/`ONLINE`/`PLAY`/`LIFE`/`GLOBAL`) |
| `max_eligible_txn_amount` | bigint | nullable | 할인이 적용되는 결제금액 자체의 상한 (예: "승인액 5만원까지만 적용") — 건당 할인액 한도(`per_txn_cap`)와는 다른 개념 |
| `cap_group_id` | bigint | not null, fk | → `benefit_cap_group.id` |

**삭제 (→ `benefit_cap_group`로 이동)**: `monthly_cap_amount`, `monthly_cap_count`, `per_txn_cap`

**`benefit_type` enum 확장**: `DISCOUNT_RATE` / `DISCOUNT_FIXED` / `POINT_RATE` / `POINT_FIXED` / `MILE_RATE` / `MILE_FIXED`

**정책**: `benefit_type`이 `MILE_RATE`/`MILE_FIXED`면 `point_krw_ratio`는 **정책적으로 영구 NULL**입니다. "아직 검증 안 함"이 아니라 "원화 환산 자체가 불가능한 혜택"이라는 뜻이고, 계산 엔진은 이 타입을 원화 비교 대상에서 항상 제외하고 참고 정보로만 보여줍니다(기획서 4.4 "검증되지 않은 포인트 환산 비율... 임의 계산하지 않는다" 원칙과 동일선상).

### 3-3. `benefit_rule.conditions` (jsonb) 최소 키 확정

| 키 | 타입 | 설명 | 근거 |
| --- | --- | --- | --- |
| `channel` | `"ONLINE" \| "OFFLINE" \| "ALL"` | 온/오프라인 한정 여부 | 알파벳카드S(편의점 현장결제만), 모니모페이(온라인 전체) |
| `require_app_registration` | boolean | 특정 앱 등록·결제 필수 여부 | MOVING(카카오T 앱 등록 결제만) |
| `excluded_submerchants` | string[] | 같은 가맹점이라도 제외되는 하위 채널 | 알파벳카드S(쿠팡 ⭕ / 쿠팡이츠 ❌), 모니모페이(네이버페이로 찍히면 간편결제로 분류) |
| `time_range` | `{ start: "HH:mm", end: "HH:mm" }` | 시간대 조건 | 신한 Mr.Life(Night 21:00~09:00) |
| `days_of_week` | string[] | 요일 조건 | 신한 Mr.Life(주말 할인, 공휴일 제외) |
| `installment_excluded` | boolean | 무이자할부 결제 제외 여부 | 알파벳카드S, 신한 Mr.Life 등 다수 공통 |

### 3-4. `benefit_cap_group` (신규 테이블)

| 컬럼 | 타입 | 조건 | 설명 |
| --- | --- | --- | --- |
| `id` | bigint | pk | |
| `product_id` | bigint | not null, fk | → `card_product` |
| `cap_period` | varchar | not null | `MONTHLY` / `YEARLY` / `DAILY` |
| `cap_amount` | bigint | nullable | 한도 금액(원) |
| `cap_count` | int | nullable | 한도 횟수 |
| `description` | text | nullable | 예: "장보기쇼핑+딜리버리+대중교통 통합 한도" |

**사용법**: 여러 `benefit_rule` 행이 같은 `cap_group_id`를 가지면 그 한도를 나눠 씁니다. 공유 한도가 없는 단순한 혜택도 "멤버가 1개인 그룹"으로 만들어서, 계산 엔진이 항상 같은 경로(그룹 단위 한도 체크)로 동작하게 합니다 — 공유/비공유를 분기 처리하지 않아도 됨.

**알려진 한계**: 알파벳카드S처럼 "영역별 월 5천원 + 영역 통합 월 1~1.5만원"이 동시에 걸리는 **이중 한도**는 지금 구조(그룹 1개)로 못 담습니다. 5번 참고.

### 3-5. `user_card_benefit_selection` (신규 테이블, P1)

| 컬럼 | 타입 | 조건 | 설명 |
| --- | --- | --- | --- |
| `id` | bigint | pk | |
| `user_card_id` | bigint | not null, fk | → `user_card` |
| `select_group` | varchar | not null | `benefit_rule.select_group`과 매칭 |
| `selected_option` | varchar | not null | `benefit_rule.select_option`과 매칭 |
| `effective_from` | date | not null | 적용 시작월 (대부분 카드가 "신청 다음달 1일 반영") |
| `created_at` | timestamp | not null | |

**계산 로직**: `select_group`이 있는 `benefit_rule`은, 그 `user_card`에 대해 `(select_group, select_option)`이 일치하는 `user_card_benefit_selection`이 있을 때만 계산 대상에 포함합니다. 선택 기록이 없으면 "추가 조건 미확인"으로 처리하고(혜택 0원 아님), 계산에서 제외합니다.

## 4. 구현 범위 (P0 / P1)

**스키마는 지금 선택형까지 포함해서 설계합니다** (`select_group`/`select_option`/`cap_group_id` 전부 P0 스키마에 포함). 나중에 스키마를 다시 바꾸는 비용이 선택형을 처음부터 넣는 비용보다 크기 때문입니다.

다만 **"사용자가 옵션을 고르는 기능"(API + 화면 + `user_card_benefit_selection` 저장/조회)은 P1**로 미룹니다. P0에서는:

- 고정 혜택(`select_group IS NULL`)은 정상 계산
- 선택형 혜택(`select_group IS NOT NULL`)은 선택 기록이 없는 게 기본값이라 자동으로 "추가 조건 미확인"으로 처리됨 (기획서 4.4에 이미 있는 상태값 — 새로 안 만들어도 됨)

→ **선택형 카드도 고정 부분은 P0에서 바로 지원되고, 선택 UI만 없는 상태로 반쪽 동작**합니다. P1에서 선택 기능을 붙이면 같은 카드가 바로 완전해집니다.

### 계산 범위에서 아예 제외하는 것 (스키마에는 데이터로 남기되, 계산 엔진은 건드리지 않음)

| 항목 | 이유 | 해당 카드 |
| --- | --- | --- |
| 마일리지(`MILE_RATE`/`MILE_FIXED`) | 원화 환산 불가 (3-2 정책 참고) | THE1, MILEAGE PLATINUM |
| 리터당 등 단위 기반 혜택 | 계산하려면 "리터 수" 입력이 필요한데 MVP는 결제금액만 받음 | 신한 Mr.Life(주유), 굿데이카드(주유) |
| 연 1회 선택형 비거래 혜택 (상품권/항공권 등) | 건당 계산 모델과 안 맞음, 카드 소개 정보로만 취급 | THE1(GIFT) |
| 결제 이력 기반 조건 (예: "3일 연속 이용 시 2배") | 단순 룰 매칭이 아니라 결제 이력 집계가 필요 | MOVING |

## 5. 다음 리뷰에서 다듬어야 할 점 (미해결)

1. **이중 한도** — 알파벳카드S처럼 "영역별 캡 + 통합 캡"이 동시에 걸리는 경우. `benefit_cap_group`을 중첩시킬지, `benefit_rule`이 캡 그룹을 2개 참조하게 할지 결정 필요.
2. **2축 실적구간** — 모니모페이카드(전월 이용금액 × 앱 로그인 100% 달성 여부, 서로 독립적으로 rate/한도를 결정)는 `card_performance_tier` 단일 FK 구조로 못 담음. 이 카드를 P0 대상에서 빼거나, `card_performance_tier`에 `tier_axis` 구분을 추가할지 결정 필요.
3. **가맹점 귀속 모호성** — "네이버페이로 결제하면 가맹점명이 '네이버페이'로 찍혀서 원래 업종이 아니라 간편결제로 분류됨" 같은 케이스. `merchant`/`merchant_alias`만으로 PG사와 실제 매입 가맹점을 구분할 수 있는지 데이터 적재하면서 확인 필요.
4. **신규가입 유예기간** — 다수 카드(MOVING, My WE:SH, 알파벳카드S, 신한 Mr.Life)에 "가입 후 1~2개월은 실적 미달해도 혜택 제공" 정책이 있음. `card_product`에 메타데이터 컬럼이 필요한지, P1로 미룰지 결정 필요.
5. **실적 산정 기준월 오프셋** — 신한 Mr.Life는 교통이용 실적만 전전월 기준(나머지는 전월). 지금 스키마엔 "몇 개월 전 실적을 참조하는지"를 나타내는 필드가 없음.
6. **실적 산정 제외 대상** — "세금/공과금/상품권 구매는 실적 계산에서 제외"가 카드마다 거의 똑같이 반복됨. `benefit_rule`마다 중복 기입하지 말고 `card_product` 레벨 공통 필드로 뺄지 결정 필요.

## 6. 데모 카드 추천 (검증 목표: 실제 카드 3종 이상, 카드사 2곳 이상 — 기획서 1.5)

구현 난이도가 낮은 순으로, 서로 다른 카드사 조합을 골랐습니다.

1. **알파벳카드S (현대카드)** — 고정형, 패턴 제일 단순
2. **굿데이카드 (KB국민카드)** — 고정형, 3단계 실적구간 검증에 좋음
3. **신한카드 Mr.Life (신한카드)** — 고정형이지만 시간대/요일 조건까지 있어서 `conditions` jsonb 검증 가능 (리터당 주유 혜택 1건만 제외)

여기에 시간이 남으면 **삼성 iD SELECT ALL**을 추가해서 "고정 부분만 계산되는 선택형 카드"(4번 참고) 동작을 데모에 포함하는 것도 고려해볼 만합니다.
