# Swagger UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 배포 가능한 Swagger UI 공통 설정과 JWT 호출을 제공한다.
**Architecture:** springdoc Web MVC UI + OpenAPI 보안 설정, 기존 Spring Security에 문서 경로만 공개.
**Tech Stack:** Java 21 / Spring Boot 4.1.2 / springdoc 3.2.0 / MockMvc / PostgreSQL Testcontainers.
**Spec:** docs/superpowers/specs/2026-11-16-swagger-ui-design.md

## Global Constraints
- 기존 API 동작·인증 유지. 배포하지 않음.
- 문서만 익명 공개하고 실제 API는 기존 토큰 정책 유지.
- 현재 서버 상대 주소 사용, 실 토큰 예시·토큰 영구 보관 없음.

## Review Focus
- 문서 공개가 실제 API 보호를 해제하지 않는지.
- 익명 UI의 초기 문서·설정 조회가 모두 성공하는지.
- 가입·로그인에 잘못된 인증 요구를 표시하지 않는지.
- 외부 샘플 서버를 호출하거나 토큰을 브라우저에 영구 보관하지 않는지.
- 자동 생성 설명의 한계를 노션의 전체 계약으로 오인하지 않는지.

### Task 1: Swagger UI 공통 설정
**Files:** build.gradle.kts, application.yml, SecurityConfig.java, global/OpenApiConfig.java, AuthController.java, README.md, global/OpenApiTest.java.
**Interfaces:** 기존 컨트롤러에서 /v3/api-docs 생성, /swagger-ui/index.html 화면, bearerAuth 보안 스키마.
- [x] RED: 엄격 인증 모드에서 UI·리디렉션·swagger-config·api-docs 익명 조회, bearer schema 및 보호 API security와 auth security 빈 배열, 토큰 없는 실제 API 401, API 경로만 문서화 검증.
- [x] GREEN: 의존성·OpenAPI 빈·문서 경로 허용·가입로그인 보안 표시·UI 설정 추가.
- [x] 전체 테스트 실행, README에 로컬/배포 후 주소·Authorize 사용·노션 병행·배포 별도 기록.
- [ ] 독립 리뷰 후 필요한 수정 검증, 커밋·PR 생성. 배포하지 않음.
