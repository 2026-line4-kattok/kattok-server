# Verified Benefit Stacking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 기존 결과를 유지하면서 확인 표시된 혜택만 합산하고 응답·결제 원장까지 일관되게 전달한다.

**Architecture:** 기본 false인 stackable 메타데이터를 계산 모델로 읽고, 기존 단일 후보들과 명시된 합산 후보를 비교한다. 규칙별 최종 금액을 응답에 저장하고 공유 한도별 projection을 기존 결제 원장에 전달한다.

**Tech Stack:** Java 21, Spring Boot, JPA/Flyway/PostgreSQL, JUnit/MockMvc/Testcontainers.

**Spec:** docs/superpowers/specs/2026-11-16-verified-benefit-stacking-design.md

## Global Constraints

- 기존 데이터는 stackable=false. 실제 상품 역채움 금지.
- 기존 생성자·응답 필드·과거 스냅샷 호환 유지.
- 금액·횟수 출처 독립, 모르는 값은 확정하지 않음.
- 공유 횟수는 거래당 한 번, 공유 금액은 연결된 혜택만 합산.
- 동률 연회비·채널 정책은 변경하지 않음.

## Review Focus

- 미표시 규칙과 표시 규칙을 잘못 합산해 기존 카드 금액이 증가하지 않는지.
- 서로 다른 하위 한도가 같은 상위 한도를 소비할 때 중복 차감·횟수 중복이 없는지.
- 가정이 있는 0원 규칙이 확정된 양수 혜택의 상태·질문에 섞이지 않는지.
- 동일 총액에서 대표 규칙과 합산 후보 선택이 데이터 조회 순서에 흔들리지 않는지.
- 현재 상품 규칙 변경 뒤 저장된 합산 추천의 결제 확인·복원이 달라지지 않는지.

### Task 1: 명시된 합산 계산과 근거 모델

**Files:** ProductRules.java, CardBenefitResult.java, BenefitCalculator.java, BenefitDetails.java; BenefitStackingTest.java, BenefitProjectionTest.java, AssumptionAnalyzerTest.java.

**Interfaces:** Consumes 기존 ProductRules·UserCardSnapshot. Produces Rule.stackable(), CardBenefitResult.appliedBenefits() of AppliedBenefit(ruleId, benefitAmount), BenefitDetails의 결과 단위 projection·가정·대표 잔여 overload.

- [x] **Step 1: Write failing tests** — 미표시 100/200→200; 표시 100/200→300; 표시 100/200+미표시250→300; 미표시400이면400; 공유 금액250→총250, 공유 횟수2→1; 각 하위 금액과 상위 합계; 절사·미확인·0·입력순 변경·순위 가정 변화.
- [x] **Step 2: Verify RED** — `./gradlew test --tests '*BenefitStackingTest'`; missing metadata/overload 또는 합산 미구현 실패 확인.
- [x] **Step 3: Implement** — 기존 생성자 호환과 기본 false, 명시된 후보만 합산, 규칙별 결과와 한도별 projection·가정 중복 제거.
- [x] **Step 4: Verify GREEN** — 새 테스트 후 `./gradlew test`; 기존 정답 케이스 포함 전체 통과.
- [x] **Step 5: Commit** — `feat(benefit): 명시된 혜택 합산과 공유 한도 계산 지원`.

### Task 2: 영속화·추천 스냅샷·실제 결제 연계

**Files:** V30 migration, BenefitRule.java, ProductRulesReader.java, RecommendationService.java; VerifiedBenefitStackingTest.java; docs/recommendation-response.md.

**Interfaces:** Consumes Task 1 appliedBenefits·projection overload. Produces DB stackable 읽기와 복수 appliedRules·집계 remainingLimitsAfter를 저장한 기존 추천 응답.

- [ ] **Step 1: Write failing HTTP tests** — 테스트 DB에서만 표시를 켜고 실제 POST/GET 추천 총액/규칙별 금액·계산식/공유 금액·횟수·독립 출처 확인, 실제 결제 확인 후 원장·혜택 집계 확인, NOT_PAID 수정 후 실제 차감 복원, 규칙 변경 뒤 과거 GET 불변, 재입력 보호, 표시되지 않은 데이터 유지.
- [ ] **Step 2: Verify RED** — `./gradlew test --tests '*VerifiedBenefitStackingTest'`; column 또는 응답 합산 미구현 실패 확인.
- [ ] **Step 3: Implement** — 새 nullable=false default false 메타데이터, 명시적 읽기, 적용 목록 기반 상세·가정·원장 projection 저장. 기존 결제 원장의 동작 변경은 검증에서 필요할 때만.
- [ ] **Step 4: Verify GREEN** — 새 테스트와 `./gradlew test`; 모든 기존 테스트 통과.
- [ ] **Step 5: Document and commit** — 표시의 의미·표시 대상 검증 책임·호환 필드 의미·기존 데이터 변화 없음을 문서화하고 커밋. 전체 변경 독립 리뷰, 필요한 수정 RED→GREEN, PR 생성·CI·Notion에 PR 대기로 반영.
