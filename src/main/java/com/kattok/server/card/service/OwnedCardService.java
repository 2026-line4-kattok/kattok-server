package com.kattok.server.card.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.dto.OwnedCardResponse;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.entity.UserCardState;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.card.repository.UserCardStateRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnedCardService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserCardRepository userCardRepository;
	private final UserCardStateRepository userCardStateRepository;
	private final CurrentUserProvider currentUserProvider;

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
					CardPerformanceTier tier = state == null ? null : state.getPerformanceTier();
					return OwnedCardResponse.of(userCard, tier);
				})
				.toList();
	}

	/** 마지막 카드는 삭제할 수 없다(F-04-04). 이미 삭제된 카드는 재삭제도 204로 응답하므로 이 제약에서 제외한다. */
	@Transactional
	public void delete(Long userCardId) {
		Long userId = currentUserProvider.get().getId();
		UserCard userCard = userCardRepository.findByIdAndUserId(userCardId, userId)
				.orElseThrow(() -> ApiException.notFound("OWNED_CARD_NOT_FOUND", "존재하지 않는 카드입니다."));
		if (userCard.getDeletedAt() == null && userCardRepository.countByUserIdAndDeletedAtIsNull(userId) <= 1) {
			throw ApiException.conflict("LAST_OWNED_CARD", "마지막 카드는 삭제할 수 없습니다.");
		}
		userCard.delete();
	}
}
