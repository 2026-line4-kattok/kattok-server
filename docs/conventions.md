# 개발 컨벤션

두 명이 따로 작업해도 코드가 어긋나지 않도록 맞춰 둘 규칙입니다. 로컬 실행, 브랜치, 커밋 메시지, Flyway 파일 규칙은 [README](../README.md)를 따릅니다.

아직 공통 코드가 없는 초기 단계의 합의안입니다. 아래의 `global` 패키지와 에러 응답은 첫 기능을 만들 때 함께 구현합니다.

## 패키지 구조

기본 패키지는 `com.kattok.server`이고, 계층이 아니라 도메인 기준으로 나눕니다.

```text
com.kattok.server
├── global             # 공통 설정, 에러 처리
├── user               # 계정, 인증
├── card               # 카드 상품, 보유 카드, 실적·한도 상태
├── benefit            # 혜택 규칙, 계산 엔진
├── merchant           # 결제처, 업종
├── recommendation     # 카드 비교, 추천 결과
└── payment            # 결제 확인, 기록
```

도메인 안은 다음과 같이 나눕니다. 필요 없는 패키지는 만들지 않습니다.

```text
card
├── controller
├── service
├── repository
├── entity
└── dto
```

- 다른 도메인의 기능은 해당 도메인의 Service를 통해 사용합니다. 다른 도메인의 Repository를 직접 호출하지 않습니다.
- 혜택 계산 로직은 Spring과 JPA에 의존하지 않는 순수 Java로 작성합니다. DB 없이 단위 테스트로 검증하기 위해서입니다.

## 네이밍

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스 | PascalCase | `CardService` |
| 메서드, 변수 | camelCase | `findOwnedCards` |
| 상수, enum 값 | UPPER_SNAKE_CASE | `NOT_VERIFIED` |
| 요청 DTO | `<동작><대상>Request` | `RegisterCardRequest` |
| 응답 DTO | `<대상>Response` | `OwnedCardResponse` |
| 테이블 | snake_case 복수형 | `card_products` |
| 컬럼 | snake_case | `verified_at` |

## 코드 작성

- DTO는 `record`로 작성하고, 입력 검증은 요청 DTO에 Bean Validation으로 선언합니다.
- 엔티티를 API 응답으로 직접 반환하지 않습니다.
- 엔티티에는 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용합니다. `@Data`와 `@Setter`는 사용하지 않고, 상태 변경은 의미가 드러나는 메서드로 작성합니다.
- 연관관계는 `FetchType.LAZY`를 사용합니다.
- 트랜잭션은 Service에 선언하고, 조회 전용 메서드에는 `readOnly = true`를 붙입니다.

## API

### URL

- `/api/v1`로 시작하고, 리소스는 복수형 kebab-case로 씁니다. (예: `GET /api/v1/owned-cards`)
- JSON 필드는 camelCase로 씁니다.
- 특정 시점은 UTC 기준 ISO 8601로 주고받고, 화면에 표시할 때 프론트엔드에서 한국 시간으로 변환합니다. (예: `2026-11-04T06:30:00Z`)

### 응답

성공하면 데이터를 감싸지 않고 그대로 반환합니다.

```json
{
  "id": 1,
  "productName": "카드 이름"
}
```

실패하면 HTTP 상태 코드와 함께 다음 형식으로 반환합니다. `fieldErrors`는 입력 검증 실패에만 포함합니다.

```json
{
  "code": "INVALID_INPUT",
  "message": "결제 금액을 확인해 주세요.",
  "fieldErrors": [
    { "field": "amount", "message": "1원 이상 입력해 주세요." }
  ]
}
```

| 상태 코드 | 사용 상황 | `code` 예시 |
| --- | --- | --- |
| 400 | 입력값 오류 | `INVALID_INPUT` |
| 401 | 인증 필요 | `UNAUTHORIZED` |
| 403 | 권한 없음 | `FORBIDDEN` |
| 404 | 대상 없음 | `CARD_NOT_FOUND` |
| 409 | 현재 상태와 충돌 | `CARD_ALREADY_REGISTERED` |
| 500 | 서버 오류 | `INTERNAL_ERROR` |

`code`는 프론트엔드가 분기할 때 쓰는 값이므로, 한 번 정하면 임의로 바꾸지 않습니다. `message`는 사용자에게 보여 줄 수 있는 문장으로 쓰고, 내부 예외 내용을 담지 않습니다.

### 요청을 처리한 결과는 에러가 아닙니다

조회 실패와 비즈니스 결과를 구분합니다(기획서 4.4, 4.6).

- "추가 혜택 없음", "일부 비교 완료", "계산 불가"처럼 요청을 처리해 얻은 결과는 200으로 응답하고 본문에 상태를 담습니다.
- 데이터 조회 실패 같은 시스템 오류는 5xx로 응답합니다. 빈 결과로 바꿔서 반환하지 않습니다.

## 도메인 데이터

기획서의 정책을 코드에서 지키기 위한 규칙입니다.

- **금액:** 원 단위 정수로 다루며 `long`을 사용합니다. 미확인 상태가 있는 금액은 `null`을 담을 수 있도록 `Long`을 사용합니다. 할인율 같은 비율은 `BigDecimal`을 사용합니다. `double`과 `float`는 사용하지 않습니다.
- **미확인 값:** 미확인 실적·한도를 `0`이나 기본값으로 저장하지 않습니다. 값이 없으면 `null`로 두고, 확인 여부와 확인 시점을 구분할 수 있게 합니다.
- **시간:** 특정 시점은 `Instant`로 다루고 DB에는 `timestamptz`로 저장합니다. 실적 기준 월처럼 날짜 단위 값은 `YearMonth`, `LocalDate`를 사용합니다.
- **추천 기록:** 추천 당시의 조건과 결과를 스냅샷으로 저장합니다. 카드 상태나 혜택 규칙이 바뀌어도 과거 기록을 다시 계산해 덮어쓰지 않습니다.
- **중복 방지:** 재시도로 중복이 생길 수 있는 데이터는 애플리케이션 검사에만 의존하지 않고 DB 유니크 제약을 함께 둡니다.

## 테스트

- 혜택 계산 로직은 단위 테스트로 작성하고, 기대 금액의 근거가 되는 약관 조건을 테스트에 드러냅니다.
- DB 동작을 확인하는 테스트는 Testcontainers를 사용합니다.
- 테스트 이름은 `@DisplayName`에 한글로 씁니다. (예: `@DisplayName("실적이 미확인이면 충족으로 가정하지 않는다")`)
- 새 기능과 버그 수정에는 테스트를 함께 추가합니다.

## 민감 정보

- 카드번호와 CVC는 수집하지 않습니다.
- 비밀번호, 토큰, 개인정보를 로그에 남기지 않습니다.
- 운영 비밀값은 환경 변수로 주입하고 커밋하지 않습니다.
