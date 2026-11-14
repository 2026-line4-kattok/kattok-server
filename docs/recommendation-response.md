# 추천 응답 상세 정보

`POST /api/v1/recommendations`와 `GET /api/v1/recommendations/{id}`에 상세 필드를 추가한다.
기존 계산·순위·동률 규칙과 카드 상태 저장 구조는 변경하지 않는다.

## 기존 응답과 호환

카드 단위 `calculation`과 `remainingLimitAfter`는 유지한다.
`appliedRules`의 기존 `ruleId`, `description`, `benefitAmount`도 유지한다.

새 추천에는 다음 정보가 추가된다.

- `appliedRules[].calculation`: 적용 혜택의 계산식. 현재는 가장 큰 혜택 하나를 적용하므로 카드 단위 계산식과 같다.
- `appliedRules[].capGroupIds`: 해당 혜택의 한도부터 상위 한도까지 ID 목록. 같은 그룹을 중복 반환하지 않는다.
- `assumptions[].capGroupId`: 한도·횟수 가정이 가리키는 그룹 ID. 실적 가정은 null이다.
- `remainingLimitsAfter[]`: 금액 또는 횟수 제한이 있는 그룹별 상세 예측. 무제한 그룹만 있으면 빈 배열이다.

## 한도별 예측

| 필드 | 의미 |
| --- | --- |
| `capGroupId`, `description`, `capPeriod` | 추천 당시 한도 식별 정보 |
| `capAmount`, `capCount` | 전체 금액·횟수 제한. 제한 없으면 null |
| `remainingLimitBefore`, `remainingLimitAmount` | 계산에 사용한 결제 전 금액과 결제 후 예상 금액 |
| `remainingCountBefore`, `remainingCount` | 계산에 사용한 결제 전 횟수와 결제 후 예상 횟수 |
| `amountSource`, `countSource` | 각각 USER_INPUT / ESTIMATED / ASSUMED. 해당 제한 없으면 null |

모르는 금액·횟수는 전체 한도·횟수로 가정하되 저장된 카드 상태를 변경하지 않는다.
현재 적용된 혜택 금액을 각 연결 한도에서 차감해 예측하며, 혜택이 양수면 제한 횟수는 1회 줄인다.
예측 잔여 값은 0 아래로 내려가지 않는다. 실제 결제 확인·차감 API는 이 변경에 포함하지 않는다.

## 질문 대상

실적 → 남은 금액 → 남은 횟수 순서, 같은 종류는 한도 ID 순서로 가정을 반환한다.
금액·횟수의 `affectsRank`는 지정한 그룹의 해당 미확인 값만 0으로 바꿔 추천 카드가 달라지는지 판정한다.
다른 그룹이나 같은 그룹의 다른 필드는 바꾸지 않는다. 기본 추천 계산에는 영향을 주지 않는다.

## 스냅샷과 이전 기록

새 상세 정보도 기존 jsonb 결과 스냅샷에 저장한다. GET은 저장된 결과만 사용한다.
카드 상태·규칙을 바꿔도 이전 추천을 다시 계산하지 않는다. DB 마이그레이션은 필요하지 않다.

확장 이전 기록은 기존 응답을 유지하며, 저장되지 않은 `remainingLimitsAfter`·`capGroupIds`는 빈 배열,
혜택별 `calculation`·가정의 `capGroupId`는 null이다. 현재 규칙으로 과거 정보를 추측하지 않는다.
따라서 빈 배열이 반드시 당시 한도 없음이라는 의미는 아니다. 과거 기록에는 상세 정보가 없을 수 있다.

중복 혜택 합산, 채널·결제수단 조건, 동률 기준 변경, 미리보기, 질문 최대 2개 선택 UI는 별도 작업이다.
