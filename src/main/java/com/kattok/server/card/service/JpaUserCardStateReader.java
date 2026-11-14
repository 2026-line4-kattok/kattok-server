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

	/**
	 * 실적을 확인한 적이 없거나(row 없음), 확인했지만 모른다고 한 경우(performanceTier가 {@code null})
	 * 모두 {@link PerformanceState#unknown()}으로 본다. 현재 카드 등록·상태 수정 API에는 "확인했지만
	 * 최저 구간도 못 채웠다"를 명시적으로 표현하는 방법이 없어 {@code BELOW}는 아직 쓰이지 않는다.
	 */
	private static PerformanceState toPerformanceState(UserCardState state) {
		if (state == null || state.getPerformanceTier() == null) {
			return PerformanceState.unknown();
		}
		return PerformanceState.confirmed(state.getPerformanceTier().getId());
	}
}
