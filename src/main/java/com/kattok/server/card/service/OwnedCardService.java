package com.kattok.server.card.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.dto.OwnedCardResponse;
import com.kattok.server.card.dto.PerformanceInput;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.RegisterCardRequest;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.ConfirmationSource;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.entity.UserCardState;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.card.repository.UserCardStateRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.service.CurrentUserProvider;

import com.kattok.server.user.service.UserMutationGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnedCardService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserCardRepository userCardRepository;
	private final UserCardStateRepository userCardStateRepository;
	private final CardProductRepository cardProductRepository;
	private final CardPerformanceTierRepository cardPerformanceTierRepository;
	private final CurrentUserProvider currentUserProvider;
	private final UserMutationGuard mutationGuard;

	/** 추천 등 다른 도메인에서 보유 카드를 참조할 때 쓴다. 삭제한 카드는 빠지고, 등록 순서대로 준다. */
	public List<UserCard> findActiveCards(Long userId) {
		return userCardRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
	}

	public List<OwnedCardResponse> findAll() {
		Long userId = currentUserProvider.get().getId();
		List<UserCard> userCards = userCardRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
		if (userCards.isEmpty()) {
			return List.of();
		}

		List<Long> userCardIds = userCards.stream().map(UserCard::getId).toList();
		YearMonth thisMonth = YearMonth.now(KST);
		Map<Long, UserCardState> statesByUserCardId = userCardStateRepository
				.findByUserCardIdInAndPeriodMonth(userCardIds, thisMonth).stream()
				.collect(Collectors.toMap(state -> state.getUserCard().getId(), state -> state));

		return userCards.stream()
				.map(userCard -> {
					UserCardState state = statesByUserCardId.get(userCard.getId());
					PerformanceStatus status = state == null ? PerformanceStatus.UNKNOWN : state.getPerformanceStatus();
					CardPerformanceTier tier = state == null ? null : state.getPerformanceTier();
					return OwnedCardResponse.of(userCard, status, tier);
				})
				.toList();
	}

	@Transactional
	public OwnedCardResponse register(RegisterCardRequest request) {
		User user = currentUserProvider.get();
		CardProduct product = cardProductRepository.findById(request.productId())
				.orElseThrow(() -> ApiException.notFound("CARD_PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다."));

		if (userCardRepository.findActiveProductIdsByUserId(user.getId()).contains(product.getId())) {
			throw ApiException.conflict("CARD_ALREADY_REGISTERED", "이미 등록한 카드입니다.");
		}
		validatePerformanceRequired(product.getPerformanceType(), request.performance());

		PerformanceStatus status = PerformanceStatus.UNKNOWN;
		CardPerformanceTier tier = null;
		if (request.performance() != null) {
			status = request.performance().status();
			tier = resolveTier(status, request.performance().tierId(), product.getId());
		}

		UserCard userCard = userCardRepository.save(new UserCard(user, product));
		if (status != PerformanceStatus.UNKNOWN) {
			userCardStateRepository.upsert(
					userCard.getId(), YearMonth.now(KST).atDay(1), tier == null ? null : tier.getId(), status.name(),
					ConfirmationSource.USER_CONFIRMED.name(), Instant.now());
		}

		return OwnedCardResponse.of(userCard, status, tier);
	}

	private CardPerformanceTier resolveTier(PerformanceStatus status, Long tierId, Long productId) {
		validatePerformanceInput(status, tierId);
		if (status != PerformanceStatus.CONFIRMED) {
			return null;
		}
		return cardPerformanceTierRepository.findByIdAndProductId(tierId, productId)
				.orElseThrow(() -> ApiException.invalidInput("실적 구간이 해당 상품의 구간이 아닙니다."));
	}

	private static void validatePerformanceInput(PerformanceStatus status, Long tierId) {
		if (status == PerformanceStatus.CONFIRMED && tierId == null) {
			throw ApiException.invalidInput("CONFIRMED 상태에는 실적 구간 id가 필요합니다.");
		}
		if (status != PerformanceStatus.CONFIRMED && tierId != null) {
			throw ApiException.invalidInput("%s 상태에는 실적 구간 id를 보내지 않습니다.".formatted(status));
		}
	}

	/** 실적 유형이 SINGLE·MULTIPLE이면 performance가 필수이고, NONE·NEEDS_CHECK면 보낼 수 없다. */
	private static void validatePerformanceRequired(PerformanceType type, PerformanceInput performance) {
		boolean requiresPerformance = type == PerformanceType.SINGLE || type == PerformanceType.MULTIPLE;
		if (requiresPerformance && performance == null) {
			throw ApiException.invalidInput("이 카드는 지난달 실적을 입력해야 합니다.");
		}
		if (!requiresPerformance && performance != null) {
			throw ApiException.invalidInput("이 카드는 실적을 입력할 수 없습니다.");
		}
	}

	/** 마지막 카드는 삭제할 수 없다(F-04-04). 이미 삭제된 카드는 재삭제도 204로 응답하므로 이 제약에서 제외한다. */
	@Transactional
	public void delete(Long userCardId) {
		Long userId = currentUserProvider.get().getId();
		mutationGuard.lock(userId);
		UserCard userCard = userCardRepository.findByIdAndUserId(userCardId, userId)
				.orElseThrow(() -> ApiException.notFound("OWNED_CARD_NOT_FOUND", "존재하지 않는 카드입니다."));
		if (userCard.getDeletedAt() == null && userCardRepository.countByUserIdAndDeletedAtIsNull(userId) <= 1) {
			throw ApiException.conflict("LAST_OWNED_CARD", "마지막 카드는 삭제할 수 없습니다.");
		}
		userCard.delete();
	}
}
