package com.kattok.server.card.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.UpdateCardStateRequest;
import com.kattok.server.card.dto.UserCardStateResponse;
import com.kattok.server.card.dto.UserCardStateResponse.CapStatus;
import com.kattok.server.card.dto.UserCardStateResponse.PerformanceTierOption;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.ConfirmationSource;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.entity.UserCardCapUsage;
import com.kattok.server.card.entity.UserCardState;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.UserCardCapUsageRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.card.repository.UserCardStateRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

/** 보유 카드 한 장의 특정 월 실적 구간·한도 상태를 조회·수정한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserCardStateService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserCardRepository userCardRepository;
	private final UserCardStateRepository userCardStateRepository;
	private final UserCardCapUsageRepository userCardCapUsageRepository;
	private final CardPerformanceTierRepository cardPerformanceTierRepository;
	private final ProductRulesReader productRulesReader;
	private final CurrentUserProvider currentUserProvider;

	public UserCardStateResponse getState(Long userCardId, YearMonth month) {
		return buildResponse(findOwnedCard(userCardId), month);
	}

	/**
	 * 보내지 않은 필드는 그대로 둔다(부분 수정). {@code performance}를 보내지 않으면 실적 확인 상태가
	 * 바뀌지 않는다. {@code caps}에 담지 않은 한도 그룹도 바뀌지 않는다.
	 */
	@Transactional
	public UserCardStateResponse updateState(Long userCardId, UpdateCardStateRequest request) {
		UserCard userCard = findOwnedCard(userCardId);
		Long productId = userCard.getProduct().getId();
		LocalDate monthStart = request.month().atDay(1);

		if (request.performance() != null) {
			PerformanceStatus status = request.performance().status();
			CardPerformanceTier tier = resolveTier(status, request.performance().tierId(), productId);
			userCardStateRepository.upsert(
					userCard.getId(), monthStart, tier == null ? null : tier.getId(), status.name(),
					ConfirmationSource.USER_CONFIRMED.name(), Instant.now());
		}

		if (request.caps() != null && !request.caps().isEmpty()) {
			Map<Long, Cap> caps = capsOf(productId);
			for (UpdateCardStateRequest.CapUpdate capUpdate : request.caps()) {
				Cap cap = caps.get(capUpdate.capGroupId());
				if (cap == null) {
					throw ApiException.invalidInput("해당 카드의 한도가 아닙니다.");
				}
				validateWithinCap(cap, capUpdate);
				userCardCapUsageRepository.upsert(
						userCard.getId(), capUpdate.capGroupId(), monthStart,
						capUpdate.remainingLimitAmount(), capUpdate.remainingCount(),
						ConfirmationSource.USER_CONFIRMED.name(), Instant.now());
			}
		}

		return buildResponse(userCard, request.month());
	}

	private UserCard findOwnedCard(Long userCardId) {
		Long userId = currentUserProvider.get().getId();
		return userCardRepository.findByIdAndUserIdAndDeletedAtIsNull(userCardId, userId)
				.orElseThrow(() -> ApiException.notFound("OWNED_CARD_NOT_FOUND", "존재하지 않는 카드입니다."));
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

	private UserCardStateResponse buildResponse(UserCard userCard, YearMonth month) {
		Long productId = userCard.getProduct().getId();

		List<PerformanceTierOption> options = cardPerformanceTierRepository
				.findByProductIdOrderByLevelAsc(productId).stream()
				.map(UserCardStateService::toOption)
				.toList();

		UserCardState state = userCardStateRepository.findByUserCardIdAndPeriodMonth(userCard.getId(), month)
				.orElse(null);
		PerformanceStatus status = state == null ? PerformanceStatus.UNKNOWN : state.getPerformanceStatus();
		CardPerformanceTier confirmedTier = state == null ? null : state.getPerformanceTier();
		PerformanceTierOption performanceTier = confirmedTier == null ? null : toOption(confirmedTier);

		List<CapStatus> caps = confirmedTier == null
				? List.of()
				: capsForTier(productId, confirmedTier.getId(), userCard.getId(), month);

		return new UserCardStateResponse(
				month.toString(), status, performanceTier, options, caps,
				state == null ? null : state.getConfirmedAt());
	}

	private List<CapStatus> capsForTier(Long productId, Long confirmedTierId, Long userCardId, YearMonth month) {
		ProductRules productRules = productRulesReader.read(Set.of(productId), LocalDate.now(KST)).get(productId);
		if (productRules == null) {
			return List.of();
		}

		Set<Long> relevantCapGroupIds = new LinkedHashSet<>();
		for (Rule rule : productRules.rules()) {
			if (confirmedTierId.equals(rule.requiredTierId())) {
				addWithParents(relevantCapGroupIds, rule.capGroupId(), productRules.caps());
			}
		}

		Map<Long, UserCardCapUsage> usageByCapGroupId = userCardCapUsageRepository
				.findByUserCardIdAndPeriodMonth(userCardId, month).stream()
				.collect(Collectors.toMap(usage -> usage.getCapGroup().getId(), usage -> usage));

		return relevantCapGroupIds.stream()
				.map(capGroupId -> toCapStatus(productRules.caps().get(capGroupId), usageByCapGroupId.get(capGroupId)))
				.toList();
	}

	private Map<Long, Cap> capsOf(Long productId) {
		ProductRules productRules = productRulesReader.read(Set.of(productId), LocalDate.now(KST)).get(productId);
		return productRules == null ? Map.of() : productRules.caps();
	}

	private static void addWithParents(Set<Long> ids, Long capGroupId, Map<Long, Cap> caps) {
		Long current = capGroupId;
		while (current != null && ids.add(current)) {
			Cap cap = caps.get(current);
			current = cap == null ? null : cap.parentId();
		}
	}

	private static void validateWithinCap(Cap cap, UpdateCardStateRequest.CapUpdate capUpdate) {
		Long amount = capUpdate.remainingLimitAmount();
		if (amount != null && (amount < 0 || (cap.capAmount() != null && amount > cap.capAmount()))) {
			throw ApiException.invalidInput("잔여 한도가 올바르지 않습니다.");
		}
		Integer count = capUpdate.remainingCount();
		if (count != null && (count < 0 || (cap.capCount() != null && count > cap.capCount()))) {
			throw ApiException.invalidInput("잔여 횟수가 올바르지 않습니다.");
		}
	}

	private static PerformanceTierOption toOption(CardPerformanceTier tier) {
		return new PerformanceTierOption(tier.getId(), tier.getLevel(), tier.getLabel(), tier.getMinAmount());
	}

	private static CapStatus toCapStatus(Cap cap, UserCardCapUsage usage) {
		return new CapStatus(
				cap.id(), cap.description(), cap.capPeriod(), cap.capAmount(),
				usage == null ? null : usage.getRemainingLimitAmount(),
				usage == null ? null : usage.getRemainingCount(),
				usage == null ? null : usage.getConfirmedAt());
	}
}
