package com.kattok.server.global.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import java.util.List;

/**
 * 인증은 {@code Authorization: Bearer <액세스 토큰>} 헤더로 한다. 서버는 세션을 만들지 않는다.
 *
 * <p>회원가입·로그인을 뺀 API는 토큰이 필요하다. 잘못된 토큰은 401이다. 단, {@code kattok.auth.anonymous-demo-user}를
 * 켜면(로컬 프로필 기본) 모든 경로를 열어 두고 토큰이 없는 요청은 {@code CurrentUserProvider}가 테스트 계정으로
 * 처리한다. 프론트엔드가 로그인을 붙이기 전 API 연결 테스트용이다.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class SecurityConfig {

	static final String ISSUER = "kattok";

	/** 토큰 없이 부를 수 있는 경로. */
	private static final String[] PUBLIC_PATHS = {"/api/v1/auth/signup", "/api/v1/auth/login", "/actuator/health"};

	/** 웹 요청 필터. 웹 서버 없이 띄우는 테스트(Repository 테스트 등)에서는 만들지 않는다. */
	@Bean
	@ConditionalOnWebApplication(type = Type.SERVLET)
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http, JsonErrorWriter jsonErrorWriter, AuthProperties authProperties,
			ActiveUserJwtAuthenticationConverter activeUserConverter) throws Exception {
		return http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					if (authProperties.anonymousDemoUser()) {
						auth.anyRequest().permitAll();
					} else {
						auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated();
					}
				})
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(activeUserConverter))
						.authenticationEntryPoint(jsonErrorWriter)
						.accessDeniedHandler(jsonErrorWriter))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(jsonErrorWriter)
						.accessDeniedHandler(jsonErrorWriter))
				.build();
	}

	/** 프론트 도메인이 아직 유동적인 해커톤 기간이라 전체 허용한다. 쿠키를 안 쓰고 Authorization 헤더만 쓰니 안전하다. */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(List.of("*"));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	@Bean
	public JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		// 만료 시각과 함께 발급자도 확인한다. 같은 키로 다른 곳에서 만든 토큰은 받지 않는다.
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private static SecretKey secretKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
