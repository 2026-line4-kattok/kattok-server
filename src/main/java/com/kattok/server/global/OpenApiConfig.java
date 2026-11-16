package com.kattok.server.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/** 문서의 인증 표시는 실제 인가 정책을 바꾸지 않는다. */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI kattokOpenApi() {
		return new OpenAPI()
				.addServersItem(new Server().url("/"))
				.info(new Info().title("카똑 API").version("v1")
						.description("현재 실행 중인 서버의 API입니다. 로그인 응답의 accessToken을 Authorize에 입력하세요. "
								+ "요청 생략·null 처리와 세부 정책은 노션 API 명세서를 함께 확인하세요."))
				.components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
	}
}
