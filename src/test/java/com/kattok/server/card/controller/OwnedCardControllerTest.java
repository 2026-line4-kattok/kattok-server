package com.kattok.server.card.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class OwnedCardControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserCardRepository userCardRepository;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("등록된 보유 카드를 등록 순서대로 반환하고, createdAt은 한국 시간(+09:00)으로 나온다")
	void findAll() throws Exception {
		registerAnyProductForDemoUser();

		mockMvc.perform(get("/api/v1/owned-cards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].product.name").isNotEmpty())
				.andExpect(jsonPath("$[0].performanceTier").doesNotExist())
				.andExpect(jsonPath("$[0].createdAt").value(matchesPattern(".*\\+09:00")));
	}

	@Test
	@DisplayName("삭제하면 목록에서 빠지고, 다시 삭제해도 204를 반환한다")
	void delete_removesFromList() throws Exception {
		UserCard userCard = registerAnyProductForDemoUser();

		mockMvc.perform(delete("/api/v1/owned-cards/{id}", userCard.getId()))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/owned-cards/{id}", userCard.getId()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/owned-cards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == %d)]".formatted(userCard.getId())).doesNotExist());
	}

	@Test
	@DisplayName("존재하지 않는 보유 카드를 삭제하면 404와 OWNED_CARD_NOT_FOUND를 반환한다")
	void delete_notFound() throws Exception {
		mockMvc.perform(delete("/api/v1/owned-cards/{id}", 999_999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OWNED_CARD_NOT_FOUND"));
	}

	private UserCard registerAnyProductForDemoUser() {
		User demoUser = userRepository.findByEmail("demo@kattok.app").orElseThrow();
		CardProduct product = cardProductRepository.findAll().stream().findFirst().orElseThrow();
		return userCardRepository.save(new UserCard(demoUser, product));
	}
}
