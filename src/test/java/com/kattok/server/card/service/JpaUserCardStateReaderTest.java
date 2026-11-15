package com.kattok.server.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.TestcontainersConfiguration;
import com.kattok.server.card.dto.OwnedCardResponse;
import com.kattok.server.card.dto.PerformanceInput;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.RegisterCardRequest;
import com.kattok.server.card.dto.UpdateCardStateRequest;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.dto.UserCardStateResponse;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.user.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JpaUserCardStateReaderTest {

	private static final YearMonth MONTH = YearMonth.of(2026, 11);

	@Autowired
	private JpaUserCardStateReader reader;

	@Autowired
	private OwnedCardService ownedCardService;

	@Autowired
	private UserCardStateService userCardStateService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CardProductRepository cardProductRepository;

	@Autowired
	private CardPerformanceTierRepository cardPerformanceTierRepository;

	@Test
	@DisplayName("보유 카드가 없으면 빈 목록을 반환한다")
	void noCards() {
		Long userId = demoUserId();

		assertThat(reader.findForRecommendation(userId, MONTH)).isEmpty();
	}

	@Test
	@DisplayName("실적을 확인한 적 없으면 UNKNOWN이고 한도는 비어있다")
	void unconfirmedState() {
		Long userId = demoUserId();
		OwnedCardResponse registered = ownedCardService.register(
				new RegisterCardRequest(starPlusProductId(), new PerformanceInput(PerformanceStatus.UNKNOWN, null)));

		List<UserCardSnapshot> snapshots = reader.findForRecommendation(userId, MONTH);

		assertThat(snapshots).hasSize(1);
		UserCardSnapshot snapshot = snapshots.get(0);
		assertThat(snapshot.userCardId()).isEqualTo(registered.id());
		assertThat(snapshot.registrationOrder()).isEqualTo(1);
		assertThat(snapshot.performance().status()).isEqualTo(PerformanceStatus.UNKNOWN);
		assertThat(snapshot.caps()).isEmpty();
	}

	@Test
	@DisplayName("실적 구간과 한도를 확인하면 스냅샷에 그대로 반영된다")
	void confirmedStateWithCaps() {
		Long userId = demoUserId();
		Long productId = starPlusProductId();
		OwnedCardResponse registered = ownedCardService.register(
				new RegisterCardRequest(productId, new PerformanceInput(PerformanceStatus.UNKNOWN, null)));
		Long tierId = convenienceTierId(productId);

		userCardStateService.updateState(registered.id(),
				new UpdateCardStateRequest(MONTH, new PerformanceInput(PerformanceStatus.CONFIRMED, tierId), null));
		UserCardStateResponse state = userCardStateService.getState(registered.id(), MONTH);
		Long capGroupId = state.caps().get(0).capGroupId();
		userCardStateService.updateState(
				registered.id(),
				new UpdateCardStateRequest(
						MONTH, new PerformanceInput(PerformanceStatus.CONFIRMED, tierId),
						List.of(new UpdateCardStateRequest.CapUpdate(capGroupId, 1000L, null))));

		UserCardSnapshot snapshot = reader.findForRecommendation(userId, MONTH).get(0);

		assertThat(snapshot.performance().status()).isEqualTo(PerformanceStatus.CONFIRMED);
		assertThat(snapshot.performance().tierId()).isEqualTo(tierId);
		assertThat(snapshot.caps()).containsKey(capGroupId);
		assertThat(snapshot.caps().get(capGroupId).remainingAmount()).isEqualTo(1000L);
	}

	@Test
	@DisplayName("실적을 확인했지만 최저 구간도 못 채웠으면 BELOW로 반영된다")
	void belowState() {
		Long userId = demoUserId();
		OwnedCardResponse registered = ownedCardService.register(
				new RegisterCardRequest(starPlusProductId(), new PerformanceInput(PerformanceStatus.UNKNOWN, null)));

		userCardStateService.updateState(registered.id(),
				new UpdateCardStateRequest(MONTH, new PerformanceInput(PerformanceStatus.BELOW, null), null));

		UserCardSnapshot snapshot = reader.findForRecommendation(userId, MONTH).get(0);

		assertThat(snapshot.performance().status()).isEqualTo(PerformanceStatus.BELOW);
		assertThat(snapshot.performance().tierId()).isNull();
	}

	@Test
	@DisplayName("등록 순서대로 registrationOrder가 매겨진다")
	void registrationOrder() {
		Long userId = demoUserId();
		OwnedCardResponse first = ownedCardService.register(
				new RegisterCardRequest(starPlusProductId(), new PerformanceInput(PerformanceStatus.UNKNOWN, null)));
		Long secondProductId = anotherProductId(first.product().id());
		OwnedCardResponse second = ownedCardService.register(new RegisterCardRequest(secondProductId, null));

		List<UserCardSnapshot> snapshots = reader.findForRecommendation(userId, MONTH);

		assertThat(snapshots).extracting(UserCardSnapshot::userCardId, UserCardSnapshot::registrationOrder)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(first.id(), 1),
						org.assertj.core.groups.Tuple.tuple(second.id(), 2));
	}

	private Long demoUserId() {
		return userRepository.findByEmail("demo@kattok.app").orElseThrow().getId();
	}

	private Long starPlusProductId() {
		return cardProductRepository.search(null, "스타플러스", null).stream().findFirst().orElseThrow().getId();
	}

	private Long anotherProductId(Long excludeId) {
		return cardProductRepository.findAll().stream()
				.filter(product -> !product.getId().equals(excludeId))
				.filter(product -> product.getPerformanceType() == PerformanceType.NONE
						|| product.getPerformanceType() == PerformanceType.NEEDS_CHECK)
				.findFirst()
				.orElseThrow()
				.getId();
	}

	private Long convenienceTierId(Long productId) {
		return cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(productId).stream()
				.map(CardPerformanceTier::getId)
				.findFirst()
				.orElseThrow();
	}
}
