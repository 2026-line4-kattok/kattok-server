package com.kattok.server.card.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.entity.CardIssuer;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.PerformanceType;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CardProductRepositoryTest {

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("별칭으로 검색하면 상품명이 달라도 찾는다")
	void searchByAlias() {
		CardIssuer issuer = anyExistingIssuer();
		cardProductRepository.save(new CardProduct(
				issuer, "TEST-ALIAS-001", "신한카드 Mr.Life", new String[] {"미스터라이프", "미스타 라이프"},
				CardType.CREDIT, null, DataStatus.UNVERIFIED, null, null, null, PerformanceType.NEEDS_CHECK));

		var result = cardProductRepository.search(null, "미스터라이프", null);

		assertThat(result).extracting(CardProduct::getName).contains("신한카드 Mr.Life");
	}

	@Test
	@DisplayName("별칭도 상품명도 일치하지 않으면 찾지 못한다")
	void searchByAliasNoMatch() {
		CardIssuer issuer = anyExistingIssuer();
		cardProductRepository.save(new CardProduct(
				issuer, "TEST-ALIAS-002", "신한카드 Mr.Life", new String[] {"미스터라이프"},
				CardType.CREDIT, null, DataStatus.UNVERIFIED, null, null, null, PerformanceType.NEEDS_CHECK));

		var result = cardProductRepository.search(null, "전혀다른이름", null);

		assertThat(result).extracting(CardProduct::getName).doesNotContain("신한카드 Mr.Life");
	}

	private CardIssuer anyExistingIssuer() {
		return entityManager.createQuery("select i from CardIssuer i", CardIssuer.class)
				.setMaxResults(1)
				.getSingleResult();
	}
}
