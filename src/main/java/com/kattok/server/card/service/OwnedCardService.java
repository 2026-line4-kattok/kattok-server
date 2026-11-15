package com.kattok.server.card.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.dto.OwnedCardDetailResponse;
import com.kattok.server.card.dto.OwnedCardResponse;
import com.kattok.server.card.dto.PerformanceInput;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.RegisterCardRequest;
import com.kattok.server.card.dto.StatusChip;
import com.kattok.server.card.dto.UserCardStateResponse;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.ConfirmationSource;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.entity.UserCardCapUsage;
import com.kattok.server.card.entity.UserCardState;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardCapUsageRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.card.repository.UserCardStateRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.payment.service.PaymentQueryService;
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
	private final UserCardCapUsageRepository userCardCapUsageRepository;
	private final CardProductRepository cardProductRepository;
	private final CardPerformanceTierRepository cardPerformanceTierRepository;
	private final CurrentUserProvider currentUserProvider;
	private final UserMutationGuard mutationGuard;
	private final ProductRulesReader productRulesReader;
	private final PaymentCapLedger capLedger;
	private final UserCardStateService userCardStateService;
	private final PaymentQueryService paymentQueryService;

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
		Map<Long, ProductRules> rulesByProductId = productRulesReader.read(
				userCards.stream().map(userCard -> userCard.getProduct().getId()).collect(Collectors.toSet()),
				LocalDate.now(KST));

		return userCards.stream()
				.map(userCard -> {
					UserCardState state = statesByUserCardId.get(userCard.getId());
					PerformanceStatus status = state == null ? PerformanceStatus.UNKNOWN : state.getPerformanceStatus();
					CardPerformanceTier tier = state == null ? null : state.getPerformanceTier();
					CardExtras extras = cardExtras(userCard, status, tier, rulesByProductId.get(userCard.getProduct().getId()));
					return OwnedCardResponse.of(userCard, status, tier, extras.representativeBenefit(), extras.statusChip());
				})
				.toList();
	}

	/** 보유 카드 상세(3-2). */
	public OwnedCardDetailResponse getDetail(Long userCardId) {
		Long userId = currentUserProvider.get().getId();
		UserCard userCard = userCardRepository.findByIdAndUserIdAndDeletedAtIsNull(userCardId, userId)
				.orElseThrow(() -> ApiException.notFound("OWNED_CARD_NOT_FOUND", "존재하지 않는 카드입니다."));
		CardProduct product = userCard.getProduct();

		YearMonth thisMonth = YearMonth.now(KST);
		UserCardStateResponse state = userCardStateService.getState(userCardId, thisMonth);
		ProductRules rules = productRulesReader.read(Set.of(product.getId()), LocalDate.now(KST)).get(product.getId());
		List<OwnedCardDetailResponse.Benefit> benefits = rules == null
				? List.of()
				: rules.rules().stream()
						.map(rule -> new OwnedCardDetailResponse.Benefit(rule.id(), rule.displayText(), true, rule.capGroupId()))
						.toList();
		var savedBenefit = paymentQueryService.history(thisMonth.toString(), userCardId).savedBenefit();

		return new OwnedCardDetailResponse(
				userCard.getId(),
				new OwnedCardDetailResponse.ProductDetail(
						product.getId(), product.getName(), product.getIssuer().getName(), product.getCardType(),
						product.getAnnualFee(), product.getPerformanceType()),
				new OwnedCardDetailResponse.PerformanceDetail(
						state.performanceStatus(), state.performanceTier(), state.performanceTierOptions(),
						thisMonth.minusMonths(1).toString()),
				benefits,
				state.caps(),
				savedBenefit);
	}

	@Transactional
	public OwnedCardResponse register(RegisterCardRequest request) {
		User user = currentUserProvider.get();
		mutationGuard.lock(user.getId());
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

		ProductRules rules = productRulesReader.read(Set.of(product.getId()), LocalDate.now(KST)).get(product.getId());
		CardExtras extras = cardExtras(userCard, status, tier, rules);
		return OwnedCardResponse.of(userCard, status, tier, extras.representativeBenefit(), extras.statusChip());
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

	/**
	 * 목록 화면의 대표 혜택 한 줄과 상태 칩을 만든다. 대표 혜택은 적용 가능한 혜택 중 한도금액이 가장 큰 것(한도
	 * 없음은 가장 큰 값으로 취급)이며, 이 기준은 확정된 게 아니라 필요하면 바꿀 수 있다.
	 */
	private CardExtras cardExtras(
			UserCard userCard, PerformanceStatus status, CardPerformanceTier tier, ProductRules rules) {
		if (rules == null) {
			return new CardExtras(null, StatusChip.ALL_CONFIRMED);
		}
		Long confirmedTierId = tier == null ? null : tier.getId();
		boolean tracksPerformance = TierBenefits.tracksPerformance(userCard.getProduct().getPerformanceType());
		List<Rule> applicableRules = rules.rules().stream()
				.filter(rule -> rule.requiredTierId() == null
						|| (tracksPerformance && status == PerformanceStatus.CONFIRMED
								&& rule.requiredTierId().equals(confirmedTierId)))
				.toList();

		String representativeBenefit = applicableRules.stream()
				.max(Comparator.comparing(rule -> capRank(rules.caps().get(rule.capGroupId()))))
				.map(Rule::displayText)
				.orElse(null);

		PerformanceType performanceType = userCard.getProduct().getPerformanceType();
		boolean needsPerformance = (performanceType == PerformanceType.SINGLE || performanceType == PerformanceType.MULTIPLE)
				&& status == PerformanceStatus.UNKNOWN;
		StatusChip statusChip;
		if (anyCapExhausted(userCard.getId(), applicableRules, rules.caps())) {
			statusChip = StatusChip.LIMIT_EXHAUSTED;
		} else if (needsPerformance) {
			statusChip = StatusChip.NEEDS_PERFORMANCE;
		} else if (performanceType == PerformanceType.NEEDS_CHECK) {
			// 데이터 확인이 끝나지 않아 실적 조건 자체를 아직 모른다. "다 확인함"으로 안내하지 않는다.
			statusChip = null;
		} else {
			statusChip = StatusChip.ALL_CONFIRMED;
		}
		return new CardExtras(representativeBenefit, statusChip);
	}

	/** 한도금액이 큰 혜택을 대표로 고른다. 한도가 없으면(무제한) 가장 큰 값으로 취급한다. */
	private static long capRank(Cap cap) {
		return cap == null || cap.capAmount() == null ? Long.MAX_VALUE : cap.capAmount();
	}

	private boolean anyCapExhausted(Long userCardId, List<Rule> applicableRules, Map<Long, Cap> caps) {
		Set<Long> relevantCapGroupIds = new HashSet<>();
		for (Rule rule : applicableRules) {
			Long current = rule.capGroupId();
			while (current != null && relevantCapGroupIds.add(current)) {
				Cap cap = caps.get(current);
				current = cap == null ? null : cap.parentId();
			}
		}
		if (relevantCapGroupIds.isEmpty()) {
			return false;
		}

		LocalDate today = LocalDate.now(KST);
		Map<Long, PaymentCapLedger.Balance> balances = capLedger.read(userCardId, today).stream()
				.collect(Collectors.toMap(PaymentCapLedger.Balance::capId, balance -> balance));
		Map<Long, UserCardCapUsage> legacyUsage = userCardCapUsageRepository
				.findByUserCardIdAndPeriodMonth(userCardId, YearMonth.now(KST)).stream()
				.collect(Collectors.toMap(usage -> usage.getCapGroup().getId(), usage -> usage));

		for (Long capGroupId : relevantCapGroupIds) {
			Cap cap = caps.get(capGroupId);
			if (cap == null) {
				continue;
			}
			PaymentCapLedger.Balance balance = balances.get(capGroupId);
			if (balance != null) {
				if (exhausted(cap.capAmount(), balance.amount()) || exhausted(cap.capCount(), balance.count())) {
					return true;
				}
			} else if (cap.capPeriod() == CapPeriod.MONTHLY) {
				UserCardCapUsage usage = legacyUsage.get(capGroupId);
				if (usage != null
						&& (exhausted(cap.capAmount(), usage.getRemainingLimitAmount())
								|| exhausted(cap.capCount(), usage.getRemainingCount()))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean exhausted(Object capLimit, Number remaining) {
		return capLimit != null && remaining != null && remaining.longValue() == 0;
	}

	private record CardExtras(String representativeBenefit, StatusChip statusChip) {
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
