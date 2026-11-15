package com.kattok.server.card.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.UpdateCardStateRequest;
import com.kattok.server.card.dto.UserCardStateResponse;
import com.kattok.server.card.dto.UserCardStateResponse.CapStatus;
import com.kattok.server.card.dto.UserCardStateResponse.PerformanceTierOption;
import com.kattok.server.card.dto.CardProductDetailResponse.PerformanceTierDetail;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.ConfirmationSource;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.entity.UserCardCapUsage;
import com.kattok.server.card.entity.UserCardState;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.UserCardCapUsageRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.card.repository.UserCardStateRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.user.service.CurrentUserProvider;

import com.kattok.server.user.service.UserMutationGuard;

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
	private final UserMutationGuard mutationGuard;
	private final PaymentCapLedger capLedger;

	public UserCardStateResponse getState(Long userCardId, YearMonth month) {
		return getState(userCardId, month, null);
	}

	public UserCardStateResponse getState(Long userCardId, YearMonth month, LocalDate date) {
		if (date != null && !YearMonth.from(date).equals(month)) throw ApiException.invalidInput("date는 조회 월에 속해야 합니다.");
		return buildResponse(findOwnedCard(userCardId), month, date);
	}

	/**
	 * 보내지 않은 필드는 그대로 둔다(부분 수정). {@code performance}를 보내지 않으면 실적 확인 상태가
	 * 바뀌지 않는다. {@code caps}에 담지 않은 한도 그룹도 바뀌지 않는다.
	 */
	@Transactional
	public UserCardStateResponse updateState(Long userCardId, UpdateCardStateRequest request) {
		mutationGuard.lock(currentUserProvider.get().getId());
		UserCard userCard = findOwnedCard(userCardId);
		Long productId = userCard.getProduct().getId();
		LocalDate monthStart = request.month().atDay(1);

		if (request.performance() != null) {
			PerformanceType type = userCard.getProduct().getPerformanceType();
			if (type != PerformanceType.SINGLE && type != PerformanceType.MULTIPLE) {
				throw ApiException.invalidInput("이 카드는 실적을 입력할 수 없습니다.");
			}
			PerformanceStatus status = request.performance().status();
			CardPerformanceTier tier = resolveTier(status, request.performance().tierId(), productId);
			userCardStateRepository.upsert(
					userCard.getId(), monthStart, tier == null ? null : tier.getId(), status.name(),
					ConfirmationSource.USER_CONFIRMED.name(), Instant.now());
		}

		if (request.caps() != null && !request.caps().isEmpty()) {
			Set<Long> requestedCapGroupIds = new HashSet<>();
			for (UpdateCardStateRequest.CapUpdate capUpdate : request.caps()) {
				if (!requestedCapGroupIds.add(capUpdate.capGroupId())) {
					throw ApiException.invalidInput("같은 한도를 중복해서 보낼 수 없습니다.");
				}
			}
			Map<Long, Cap> caps = capsOf(productId);
			for (UpdateCardStateRequest.CapUpdate capUpdate : request.caps()) {
				Cap cap = caps.get(capUpdate.capGroupId());
				if (cap == null) {
					throw ApiException.invalidInput("해당 카드의 한도가 아닙니다.");
				}
				validateWithinCap(cap, capUpdate);
				LocalDate periodStart = capUpdate.periodStart();
				if (cap.capPeriod() == CapPeriod.DAILY && periodStart == null)
					throw ApiException.invalidInput("일 한도 수정에는 periodStart 날짜가 필요합니다.");
				LocalDate expected = PaymentCapLedger.period(cap.capPeriod(), periodStart == null ? monthStart : periodStart);
				if (periodStart != null && !periodStart.equals(expected)
						|| cap.capPeriod() == CapPeriod.DAILY && !YearMonth.from(expected).equals(request.month())
						|| cap.capPeriod() == CapPeriod.MONTHLY && !expected.equals(monthStart)
						|| cap.capPeriod() == CapPeriod.YEARLY && expected.getYear() != monthStart.getYear())
					throw ApiException.invalidInput("한도 기간이 요청 월과 일치하지 않습니다.");
				capLedger.manual(userCard.getId(), cap.id(), cap.capPeriod(), expected, cap.description(),
						cap.capAmount() != null, cap.capCount() != null, capUpdate.amountProvided(),
						capUpdate.remainingLimitAmount(), capUpdate.countProvided(), capUpdate.remainingCount());
			}
		}

		return buildResponse(userCard, request.month(), null);
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

	private UserCardStateResponse buildResponse(UserCard userCard, YearMonth month, LocalDate date) {
		Long productId = userCard.getProduct().getId();
		PerformanceType type = userCard.getProduct().getPerformanceType();
		ProductRules productRules = productRulesReader.read(Set.of(productId), LocalDate.now(KST)).get(productId);

		List<PerformanceTierDetail> options = TierBenefits.tierOptions(
				type, cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(productId), productRules);

		UserCardState state = userCardStateRepository.findByUserCardIdAndPeriodMonth(userCard.getId(), month)
				.orElse(null);
		PerformanceStatus status = state == null ? PerformanceStatus.UNKNOWN : state.getPerformanceStatus();
		CardPerformanceTier confirmedTier = state == null ? null : state.getPerformanceTier();
		PerformanceTierOption performanceTier = confirmedTier == null ? null : toOption(confirmedTier);

		Long effectiveTierId = TierBenefits.effectiveTierId(
				type, status, confirmedTier == null ? null : confirmedTier.getId(), productRules);
		List<CapStatus> caps = capsFor(productRules, effectiveTierId, userCard.getId(), month, date);

		return new UserCardStateResponse(
				month.toString(), type, status, performanceTier, options, caps,
				state == null ? null : state.getConfirmedAt());
	}

	/** 실적 무관 규칙의 한도는 항상, 구간 규칙의 한도는 적용 구간(확인한 구간 또는 최소 혜택 구간 가정)일 때만 포함한다. */
	private List<CapStatus> capsFor(
			ProductRules productRules, Long effectiveTierId, Long userCardId, YearMonth month, LocalDate date) {
		if (productRules == null) {
			return List.of();
		}

		Set<Long> relevantCapGroupIds = new LinkedHashSet<>();
		for (Rule rule : productRules.rules()) {
			if (rule.requiredTierId() == null || rule.requiredTierId().equals(effectiveTierId)) {
				addWithParents(relevantCapGroupIds, rule.capGroupId(), productRules.caps());
			}
		}

		Map<Long, UserCardCapUsage> usageByCapGroupId = userCardCapUsageRepository
				.findByUserCardIdAndPeriodMonth(userCardId, month).stream()
				.collect(Collectors.toMap(usage -> usage.getCapGroup().getId(), usage -> usage));

		LocalDate reference = date != null ? date : month.equals(YearMonth.now(KST)) ? LocalDate.now(KST) : month.atDay(1);
		boolean dailyDateKnown = date != null || month.equals(YearMonth.now(KST));
		Map<Long, PaymentCapLedger.Balance> balances = capLedger.read(userCardId, reference).stream()
				.filter(balance -> balance.period() != CapPeriod.DAILY || dailyDateKnown)
				.collect(Collectors.toMap(PaymentCapLedger.Balance::capId, balance -> balance));
		return relevantCapGroupIds.stream().map(id -> toCapStatus(productRules.caps().get(id),
				usageByCapGroupId.get(id), balances.get(id), reference, dailyDateKnown)).toList();
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
		if (amount != null && (amount < 0 || cap.capAmount() == null || amount > cap.capAmount())) {
			throw ApiException.invalidInput("잔여 한도가 올바르지 않습니다.");
		}
		Integer count = capUpdate.remainingCount();
		if (count != null && (count < 0 || cap.capCount() == null || count > cap.capCount())) {
			throw ApiException.invalidInput("잔여 횟수가 올바르지 않습니다.");
		}
	}

	private static PerformanceTierOption toOption(CardPerformanceTier tier) {
		return new PerformanceTierOption(tier.getId(), tier.getLevel(), tier.getLabel(), tier.getMinAmount());
	}

	private static CapStatus toCapStatus(Cap cap, UserCardCapUsage usage, PaymentCapLedger.Balance balance,
			LocalDate reference, boolean dailyDateKnown) {
		if (cap.capPeriod() != CapPeriod.MONTHLY) usage = null;
		Long amount = balance != null ? balance.amount() : usage == null ? null : usage.getRemainingLimitAmount();
		Integer count = balance != null ? balance.count() : usage == null ? null : usage.getRemainingCount();
		String source = usage != null && usage.getSource() == ConfirmationSource.USER_CONFIRMED ? "USER_INPUT" : "ESTIMATED";
		return new CapStatus(cap.id(), cap.description(), cap.capPeriod(), cap.capAmount(), cap.capCount(),
				cap.capAmount() == null ? null : amount, cap.capCount() == null ? null : count,
				usage == null ? null : usage.getConfirmedAt(),
				cap.capAmount() == null ? null : balance != null ? balance.amountSource() : amount == null ? "UNKNOWN" : source,
				cap.capCount() == null ? null : balance != null ? balance.countSource() : count == null ? "UNKNOWN" : source,
				cap.capPeriod() == CapPeriod.DAILY && !dailyDateKnown ? null : PaymentCapLedger.period(cap.capPeriod(), reference));
	}
}
