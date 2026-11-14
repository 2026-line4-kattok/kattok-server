package com.kattok.server.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param anonymousDemoUser {@code true}면 토큰 없는 요청을 테스트 계정으로 처리한다. 프론트엔드가 로그인을
 *     붙이기 전 API 연결 테스트용 임시 설정이다. 기본은 {@code false}(회원가입·로그인 외 API는 토큰 필수)이고,
 *     로컬 프로필에서만 켠다.
 */
@ConfigurationProperties(prefix = "kattok.auth")
public record AuthProperties(boolean anonymousDemoUser) {
}
