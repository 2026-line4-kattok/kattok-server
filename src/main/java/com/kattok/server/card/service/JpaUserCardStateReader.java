package com.kattok.server.card.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.entity.UserCardCapUsage;
import com.kattok.server.card.entity.UserCardState;
import com.kattok.server.card.repository.UserCardCapUsageRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.card.repository.UserCardStateRepository;

import lombok.RequiredArgsConstructor;

/** {@link UserCardStateReader}의 실제 구현. DB에서 보유 카드와 그 달의 실적·한도 확인 상태를 읽는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaUserCardStateReader implements UserCardStateReader {

	private final UserCardRepository userCardRepository;
	private final UserCardStateRepository userCardStateRepository;
	private final UserCardCapUsageRepository userCardCapUsageRepository;

	@Override
	public List<UserCardSnapshot> findForRecommendation(Long userId, YearMonth period) {
		List<UserCard> userCards = userCardRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
		if (userCards.isEmpty()) {
			return List.of();
		}

		List<Long> userCardIds = userCards.stream().map(UserCard::getId).toList();

		Map<Long, UserCardState> statesByUserCardId = userCardStateRepository
				.findByUserCardIdInAndPeriodMonth(userCardIds, period).stream()
				.collect(Collectors.toMap(state -> state.getUserCard().getId(), state -> state));

		Map<Long, List<UserCardCapUsage>> usagesByUserCardId = userCardCapUsageRepository
				.findByUserCardIdInAndPeriodMonth(userCardIds, period).stream()
				.collect(Collectors.groupingBy(usage -> usage.getUserCard().getId()));

		List<UserCardSnapshot> snapshots = new ArrayList<>();
		int registrationOrder = 1;
		for (UserCard userCard : userCards) {
			PerformanceState performance = toPerformanceState(statesByUserCardId.get(userCard.getId()));
			Map<Long, CapState> caps = usagesByUserCardId.getOrDefault(userCard.getId(), List.of()).stream()
					.collect(Collectors.toMap(
							usage -> usage.getCapGroup().getId(),
							usage -> new CapState(
									usage.getRemainingLimitAmount(), usage.getRemainingCount(), usage.getSource())));
			snapshots.add(new UserCardSnapshot(
					userCard.getId(), userCard.getProduct().getId(), registrationOrder++, performance, caps));
		}
		return snapshots;
	}

	/** 실적을 확인한 적이 없으면(row 없음) UNKNOWN, 있으면 저장된 performance_status를 그대로 따른다. */
	private static PerformanceState toPerformanceState(UserCardState state) {
		if (state == null) {
			return PerformanceState.unknown();
		}
		return switch (state.getPerformanceStatus()) {
			case CONFIRMED -> PerformanceState.confirmed(state.getPerformanceTier().getId());
			case BELOW -> PerformanceState.below();
			case UNKNOWN -> PerformanceState.unknown();
		};
	}
}
