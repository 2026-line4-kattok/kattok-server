package com.kattok.server.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.kattok.server.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "kattok.auth.anonymous-demo-user=false")
@AutoConfigureMockMvc
class OpenApiTest {

	@Autowired MockMvc mvc;
	@Autowired JsonMapper json;

	@Test
	void docsArePublicAndDescribeBearerAuthenticationWithoutExposingNonApiRoutes() throws Exception {
		String body = mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.security[0].bearerAuth").isArray())
				.andExpect(jsonPath("$.servers[0].url").value("/"))
				.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists())
				.andReturn().getResponse().getContentAsString();
		JsonNode document = json.readTree(body);
		assertThat(document.path("paths").propertyNames()).allMatch(path -> path.startsWith("/api/v1/"));
		// Protected operations inherit the root requirement; an empty override would wrongly make them public.
		assertThat(document.at("/paths/~1api~1v1~1users~1me/get").has("security")).isFalse();
		assertThat(document.at("/paths/~1api~1v1~1auth~1login/post/security").isArray()).isTrue();
	}

	@Test
	void uiAssetsAndConfigurationArePublicAndUseThisServersDocument() throws Exception {
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/html"));
		mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("/v3/api-docs"))
				.andExpect(jsonPath("$.persistAuthorization").value(false));
	}

	@Test
	void documentationAccessDoesNotMakeActualApiAnonymous() throws Exception {
		mvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get("/api/v1/card-issuers"))
				.andExpect(status().isUnauthorized());
	}
}
