package com.kattok.server.recommendation.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.AssumptionAnalyzer;
import com.kattok.server.benefit.engine.BenefitDetails;
import com.kattok.server.benefit.engine.BenefitDetails.AssumptionType;
import com.kattok.server.benefit.engine.BenefitDetails.AssumptionTarget;
import com.kattok.server.benefit.engine.BenefitEngine;
import com.kattok.server.benefit.engine.BenefitEngine.CardInput;
import com.kattok.server.benefit.engine.CardBenefitResult;
import com.kattok.server.benefit.engine.CardBenefitResult.NoBenefitReason;
import com.kattok.server.benefit.engine.PaymentContext;
import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.RecommendationResult;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.UserCard;
import com.kattok.server.card.service.OwnedCardService;
import com.kattok.server.card.service.UserCardStateReader;
import com.kattok.server.global.ApiException;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;
import com.kattok.server.merchant.service.MerchantService;
import com.kattok.server.payment.service.PaymentRecordService;
import com.kattok.server.recommendation.dto.CreateRecommendationRequest;
import com.kattok.server.recommendation.dto.RecommendationResponse;
import com.kattok.server.recommendation.dto.RecommendationResponse.AppliedRule;
import com.kattok.server.recommendation.dto.RecommendationResponse.Assumption;
import com.kattok.server.recommendation.dto.RecommendationResponse.Calculation;
import com.kattok.server.recommendation.dto.RecommendationResponse.RemainingLimit;
import com.kattok.server.recommendation.dto.RecommendationResponse.RemainingLimitProjection;
import com.kattok.server.recommendation.entity.OverallStatus;
import com.kattok.server.recommendation.entity.Recommendation;
import com.kattok.server.recommendation.entity.RecommendationItem;
import com.kattok.server.recommendation.entity.RecommendationItemStatus;
import com.kattok.server.recommendation.repository.RecommendationItemRepository;
import com.kattok.server.recommendation.repository.RecommendationRepository;
import com.kattok.server.user.entity.User;
import com.kattok.server.user.service.CurrentUserProvider;

import com.kattok.server.user.service.UserMutationGuard;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제처(또는 업종)와 금액으로 보유 카드의 예상 혜택을 비교해 추천을 만들고 저장한다(명세 1-1).
 *
 * <p>계산은 {@link BenefitEngine}이 하고, 여기서는 입력을 모으고 결과를 화면용 근거와 함께 저장한다. 저장한 추천은 카드 상태가
 * 바뀌어도 다시 계산하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final BenefitEngine engine = new BenefitEngine();
	private final AssumptionAnalyzer analyzer = new AssumptionAnalyzer(engine);

	private final CurrentUserProvider currentUserProvider;
	private final UserMutationGuard mutationGuard;
	private final MerchantService merchantService;
	private final OwnedCardService ownedCardService;
	private final UserCardStateReader userCardStateReader;
	private final ProductRulesReader productRulesReader;
	private final RecommendationRepository recommendationRepository;
	private final RecommendationItemRepository recommendationItemRepository;
	private final PaymentRecordService paymentRecordService;
	private final RecommendationResponseMapper responseMapper;
	private final JsonMapper jsonMapper;

	@Transactional
	public RecommendationResponse create(CreateRecommendationRequest request) {
		if ((request.merchantId() == null) == (request.categoryId() == null)) {
			throw ApiException.invalidInput("결제처와 업종 중 하나만 골라 주세요.");
		}
		User user = currentUserProvider.get();
		mutationGuard.lock(user.getId());
		Merchant merchant = request.merchantId() == null ? null : merchantService.getMerchant(request.merchantId());
		MerchantCategory category = merchant != null
				? merchant.getCategory()
				: merchantService.getCategory(request.categoryId());
		if (merchant != null && !productRulesReader.isSupported(merchant.getId(), category.getId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MERCHANT_NOT_SUPPORTED", "카똑이 아직 계산하지 않는 결제처입니다.");
		}

		LocalDateTime now = LocalDateTime.now(KST);
		List<UserCardSnapshot> snapshots = userCardStateReader.findForRecommendation(user.getId(), now.toLocalDate());
		if (snapshots.isEmpty()) {
			throw ApiException.conflict("NO_OWNED_CARDS", "등록된 카드가 없습니다.");
		}
		Map<Long, ProductRules> products = productRulesReader.read(
				snapshots.stream().map(UserCardSnapshot::productId).collect(Collectors.toSet()), now.toLocalDate());
		List<CardInput> inputs = snapshots.stream()
				.map(snapshot -> new CardInput(snapshot, Objects.requireNonNull(products.get(snapshot.productId()))))
				.toList();

		long amount = request.amount();
		PaymentContext payment = merchant != null
				? PaymentContext.atMerchant(merchant.getId(), category.getId(), amount, now)
				: PaymentContext.inCategory(category.getId(), amount, now);
		RecommendationResult result = engine.recommend(inputs, payment);

		Map<Long, UserCard> userCards = ownedCardService.findActiveCards(user.getId()).stream()
				.collect(Collectors.toMap(UserCard::getId, Function.identity()));
		Long confirmedOnlyTop = confirmedOnlyTop(result, inputs, payment);

		Recommendation recommendation = recommendationRepository.save(new Recommendation(
				user,
				merchant,
				category,
				amount,
				inputSnapshot(request, category, now),
				OverallStatus.valueOf(result.overallStatus().name()),
				confirmedOnlyTop == null ? null : userCards.get(confirmedOnlyTop)));

		Map<Long, CardInput> inputsByUserCardId = inputs.stream()
				.collect(Collectors.toMap(input -> input.snapshot().userCardId(), Function.identity()));
		List<RecommendationItem> items = new ArrayList<>();
		int rank = 1;
		for (CardBenefitResult card : result.cards()) {
			UserCard userCard = userCards.get(card.userCardId());
			if (!card.calculable()) {
				items.add(excludedItem(recommendation, userCard));
				continue;
			}
			items.add(rankedItem(recommendation, userCard, card, rank++, result, inputs, payment,
					inputsByUserCardId.get(card.userCardId()), merchant == null));
		}
		recommendationItemRepository.saveAll(items);
		return responseMapper.toResponse(recommendation, items);
	}

	/** 저장한 추천을 그대로 돌려준다. 카드 상태가 바뀌어도 다시 계산하지 않는다(명세 1-2). */
	public RecommendationResponse get(Long recommendationId) {
		Recommendation recommendation = findOwned(recommendationId);
		return responseMapper.toResponse(
				recommendation, recommendationItemRepository.findByRecommendationIdOrderByIdAsc(recommendation.getId()));
	}

	/**
	 * [○○로 결제할게요]를 누르면 결제 확인 대기 기록을 만든다(명세 1-3). 결제 완료가 아니고, 같은 추천으로 여러 번 불러도 1건이다.
	 */
	@Transactional
	public void confirm(Long recommendationId) {
		Long userId = currentUserProvider.get().getId();
		mutationGuard.lock(userId);
		Recommendation recommendation = recommendationRepository.findWithLockByIdAndUserId(recommendationId, userId)
				.orElseThrow(RecommendationService::recommendationNotFound);
		if (!recommendation.hasRecommendedCard()) {
			throw ApiException.conflict("NO_RECOMMENDED_CARD", "추천 카드가 없는 결과라 결제 확인을 만들 수 없습니다.");
		}
		recommendation.confirm();
		paymentRecordService.createPendingIfAbsent(recommendation);
	}

	private Recommendation findOwned(Long recommendationId) {
		Long userId = currentUserProvider.get().getId();
		return recommendationRepository.findByIdAndUserId(recommendationId, userId)
				.orElseThrow(RecommendationService::recommendationNotFound);
	}

	private static ApiException recommendationNotFound() {
		return ApiException.notFound("RECOMMENDATION_NOT_FOUND", "존재하지 않는 추천입니다.");
	}

	/** 조건부 추천일 때만, 확인된 정보만으로 계산한 1위가 추천 카드와 다르면 그 카드. */
	private Long confirmedOnlyTop(RecommendationResult result, List<CardInput> inputs, PaymentContext payment) {
		if (result.overallStatus() != RecommendationResult.OverallStatus.CONDITIONAL) {
			return null;
		}
		Long top = analyzer.confirmedOnlyTop(inputs, payment);
		return Objects.equals(top, result.recommendedUserCardId()) ? null : top;
	}

	private RecommendationItem rankedItem(
			Recommendation recommendation,
			UserCard userCard,
			CardBenefitResult card,
			int rank,
			RecommendationResult result,
			List<CardInput> inputs,
			PaymentContext payment,
			CardInput input,
			boolean categoryBasis) {
		String productName = userCard.getProduct().getName();
		Rule rule = card.appliedRuleId() == null || card.expectedBenefit() == 0
				? null
				: input.product().rules().stream()
						.filter(candidate -> candidate.id().equals(card.appliedRuleId()))
						.findFirst()
						.orElseThrow();

		List<AppliedRule> appliedRules = List.of();
		Calculation calculation = null;
		RemainingLimit remainingLimitAfter = null;
		List<RemainingLimitProjection> remainingLimitsAfter = List.of();
		List<Assumption> assumptions = new ArrayList<>();
		if (rule != null) {
			BenefitDetails.Calculation detail = BenefitDetails.calculation(
					rule, payment.amount(), card.appliedBenefits().getFirst().benefitAmount());
			calculation = new Calculation(
					detail.baseAmount(), detail.ratePercent(), detail.fixedAmount(), detail.capApplied());
			appliedRules = card.appliedBenefits().stream().map(appliedBenefit -> {
				Rule appliedRule = BenefitDetails.appliedRule(input.product(), appliedBenefit.ruleId());
				var ruleCalculation = BenefitDetails.calculation(appliedRule, payment.amount(), appliedBenefit.benefitAmount());
				return new AppliedRule(appliedRule.id(), appliedRule.displayText(), appliedBenefit.benefitAmount(),
						new Calculation(ruleCalculation.baseAmount(), ruleCalculation.ratePercent(), ruleCalculation.fixedAmount(),
								ruleCalculation.capApplied()), BenefitDetails.capGroupIds(appliedRule, input.product()));
			}).toList();
			remainingLimitsAfter = BenefitDetails.remainingLimitsAfter(
					card, input.product(), input.snapshot()).stream()
					.map(limit -> new RemainingLimitProjection(
							limit.capGroupId(), limit.description(), limit.capPeriod(), limit.capAmount(), limit.capCount(),
							limit.remainingLimitBefore(), limit.remainingLimitAmount(), limit.remainingCountBefore(),
							limit.remainingCount(), limit.amountSource() == null ? null : limit.amountSource().name(),
							limit.countSource() == null ? null : limit.countSource().name()))
					.toList();
			BenefitDetails.RemainingLimit limit = BenefitDetails.remainingLimitAfter(
					card, input.product(), input.snapshot());
			if (limit != null) {
				remainingLimitAfter = new RemainingLimit(limit.amount(), limit.source().name());
			}
			for (AssumptionTarget target : BenefitDetails.assumptionTargets(card, input.product(), input.snapshot())) {
				boolean affectsRank = analyzer.affectsRank(inputs, payment, card.userCardId(),
						target.type(), target.capGroupId(), result.recommendedUserCardId());
				assumptions.add(new Assumption(target.type().name(), assumptionMessage(target.type(), productName),
						affectsRank, target.capGroupId()));
			}
		}

		Map<String, Object> applied = new LinkedHashMap<>();
		applied.put("rules", toJson(appliedRules));
		applied.put("calculation", toJson(calculation));
		applied.put("remainingLimitAfter", toJson(remainingLimitAfter));
		applied.put("remainingLimitsAfter", toJson(remainingLimitsAfter));
		// 응답 형식은 유지하고 무한도 그룹과 예전 스냅샷의 정보 누락을 결제 확인에서 구분한다.
		applied.put("uncappedCapGroupIds", card.appliedBenefits().stream()
				.map(appliedBenefit -> BenefitDetails.appliedRule(input.product(), appliedBenefit.ruleId()))
				.flatMap(appliedRule -> BenefitDetails.capGroupIds(appliedRule, input.product()).stream())
				.filter(id -> input.product().caps().get(id).capAmount() == null && input.product().caps().get(id).capCount() == null)
				.distinct().sorted().toList());
		applied.put("noBenefitReason", noBenefitMessage(card.noBenefitReason(), categoryBasis));

		Map<String, Object> unknown = new LinkedHashMap<>();
		unknown.put("assumptions", toJson(assumptions));

		return new RecommendationItem(
				recommendation,
				userCard,
				productName,
				userCard.getProduct().getIssuer().getName(),
				RecommendationItemStatus.valueOf(card.status().name()),
				card.userCardId().equals(result.recommendedUserCardId()),
				rank,
				card.expectedBenefit(),
				card.expectedBenefit(),
				applied,
				unknown,
				Map.of());
	}

	/** 혜택 미검증이 우선이다. 혜택은 검증됐지만 실적 구간 정보를 확인 중인 카드만 PERFORMANCE_NEEDS_CHECK다. */
	private RecommendationItem excludedItem(Recommendation recommendation, UserCard userCard) {
		Map<String, Object> reason = new LinkedHashMap<>();
		if (userCard.getProduct().getDataStatus() == DataStatus.UNVERIFIED) {
			reason.put("type", "UNVERIFIED_CARD");
			reason.put("message", "아직 계산 준비 중이라 비교에서 빠졌어요");
		} else {
			reason.put("type", "PERFORMANCE_NEEDS_CHECK");
			reason.put("message", "실적 구간 정보를 확인하고 있어 비교에서 빠졌어요");
		}
		return new RecommendationItem(
				recommendation,
				userCard,
				userCard.getProduct().getName(),
				userCard.getProduct().getIssuer().getName(),
				RecommendationItemStatus.NOT_CALCULABLE,
				false,
				null,
				null,
				null,
				Map.of(),
				Map.of(),
				reason);
	}

	private static String assumptionMessage(AssumptionType type, String productName) {
		return switch (type) {
			case PERFORMANCE_TIER -> productName + "의 지난달 실적을 아직 몰라요";
			case REMAINING_LIMIT -> productName + "의 이번 달 남은 한도를 아직 몰라요";
			case REMAINING_COUNT -> productName + "의 이번 달 남은 횟수를 아직 몰라요";
		};
	}

	private static String noBenefitMessage(NoBenefitReason reason, boolean categoryBasis) {
		if (reason == null) {
			return null;
		}
		return switch (reason) {
			case NO_MATCHING_RULE -> categoryBasis ? "이 업종은 혜택 대상이 아니에요" : "이 결제처는 혜택 대상이 아니에요";
			case CONDITION_NOT_MET -> "결제 금액이나 요일·시간 조건이 맞지 않아요";
			case PERFORMANCE_NOT_MET -> "지난달 실적이 혜택 기준에 못 미쳐요";
			case LIMIT_EXHAUSTED -> "이번 달 한도를 다 썼어요";
		};
	}

	private static Map<String, Object> inputSnapshot(
			CreateRecommendationRequest request, MerchantCategory category, LocalDateTime now) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("merchantId", request.merchantId());
		snapshot.put("categoryId", category.getId());
		snapshot.put("amount", request.amount());
		snapshot.put("calculatedAt", now.atZone(KST).toOffsetDateTime().toString());
		return snapshot;
	}

	/** jsonb 컬럼에는 Map·List·숫자·문자열만 넣는다. 레코드는 앱의 JSON 설정으로 먼저 바꾼다. */
	private Object toJson(Object value) {
		return value == null ? null : jsonMapper.convertValue(value, Object.class);
	}
}
