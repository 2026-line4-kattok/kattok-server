package com.kattok.server.benefit.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kattok.server.benefit.engine.CardBenefitResult.Assumption;
import com.kattok.server.benefit.engine.CardBenefitResult.LimitSource;
import com.kattok.server.benefit.engine.CardBenefitResult.NoBenefitReason;
import com.kattok.server.benefit.engine.CardBenefitResult.Status;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.benefit.entity.BenefitType;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

/**
 * 보유 카드 한 장이 결제 한 건에서 받을 혜택을 계산한다. Spring과 JPA에 의존하지 않는다.
 *
 * <p>계산 순서
 * <ol>
 *   <li>결제처·업종·전 가맹점 대상이 맞는 규칙을 고른다. 업종만 고른 결제에는 결제처 한정 규칙을 적용하지 않는다.</li>
 *   <li>금액 구간, 요일, 시간대 조건이 맞는 규칙만 남긴다.</li>
 *   <li>실적 구간이 맞는 규칙만 남긴다. 실적을 모르면 혜택을 받는 가장 낮은 구간으로 가정한다.</li>
 *   <li>규칙마다 혜택을 계산하고 한도 그룹과 그 상위 한도를 모두 적용한다. 남은 한도를 모르면 전체 한도로 가정한다.</li>
 *   <li>혜택은 중복되지 않으므로 가장 큰 규칙 하나만 적용한다.</li>
 * </ol>
 */
public class BenefitCalculator {

	private static final Set<BenefitType> SUPPORTED_TYPES = EnumSet.of(
			BenefitType.DISCOUNT_RATE, BenefitType.DISCOUNT_FIXED, BenefitType.POINT_RATE, BenefitType.POINT_FIXED);

	/** 이 엔진이 계산하는 혜택 유형인지. 원화로 환산할 수 없는 마일리지는 계산하지 않는다. */
	public static boolean supports(BenefitType type) {
		return SUPPORTED_TYPES.contains(type);
	}

	public CardBenefitResult calculate(ProductRules product, UserCardSnapshot card, PaymentContext payment) {
		if (!product.calculable()) {
			return result(product, card, Status.NOT_CALCULABLE, null, null);
		}

		List<Rule> targeted = product.rules().stream()
				.filter(rule -> supports(rule.type()))
				.filter(rule -> targets(rule, payment))
				.toList();
		if (targeted.isEmpty()) {
			return result(product, card, Status.NO_BENEFIT, null, NoBenefitReason.NO_MATCHING_RULE);
		}

		List<Rule> conditionMet = targeted.stream()
				.filter(rule -> rule.minPaymentAmount() == null || payment.amount() >= rule.minPaymentAmount())
				.filter(rule -> rule.conditions().matches(payment))
				.toList();
		if (conditionMet.isEmpty()) {
			return result(product, card, Status.NO_BENEFIT, null, NoBenefitReason.CONDITION_NOT_MET);
		}

		boolean performanceUnknown = card.performance().status() == PerformanceStatus.UNKNOWN;
		List<Rule> performanceMet = filterByPerformance(conditionMet, product, card);
		if (performanceMet.isEmpty()) {
			return result(product, card, Status.NO_BENEFIT, null, NoBenefitReason.PERFORMANCE_NOT_MET);
		}

		Candidate best = performanceMet.stream()
				.map(rule -> evaluate(rule, product, card, payment, performanceUnknown))
				.max(Comparator.comparingLong(Candidate::benefit)
						.thenComparing(candidate -> -candidate.assumptions().size())
						.thenComparing(candidate -> -candidate.rule().id()))
				.orElseThrow();

		if (best.benefit() == 0) {
			NoBenefitReason reason = best.limited() ? NoBenefitReason.LIMIT_EXHAUSTED : NoBenefitReason.CONDITION_NOT_MET;
			return result(product, card, Status.NO_BENEFIT, null, reason);
		}
		Status status = best.assumptions().isEmpty() ? Status.CONFIRMED : Status.CONDITIONAL;
		return new CardBenefitResult(
				card.userCardId(),
				status,
				best.benefit(),
				best.rule().id(),
				best.rule().displayText(),
				List.copyOf(best.assumptions()),
				best.limitSource(),
				null,
				requiredPerformance(best.rule(), product.tiers()),
				product.annualFee(),
				card.registrationOrder());
	}

	private boolean targets(Rule rule, PaymentContext payment) {
		if (rule.conditions().excludes(payment)) {
			return false;
		}
		if (rule.merchantId() != null) {
			return rule.merchantId().equals(payment.merchantId());
		}
		if (rule.categoryId() != null) {
			return rule.categoryId().equals(payment.categoryId());
		}
		return rule.conditions().allMerchants();
	}

	private List<Rule> filterByPerformance(List<Rule> rules, ProductRules product, UserCardSnapshot card) {
		Long tierId = switch (card.performance().status()) {
			case CONFIRMED -> card.performance().tierId();
			case BELOW -> null;
			case UNKNOWN -> lowestRequiredTier(rules, product.tiers());
		};
		return rules.stream()
				.filter(rule -> rule.requiredTierId() == null || rule.requiredTierId().equals(tierId))
				.toList();
	}

	private Long lowestRequiredTier(List<Rule> rules, Map<Long, Tier> tiers) {
		return rules.stream()
				.map(Rule::requiredTierId)
				.filter(tierId -> tierId != null && tiers.containsKey(tierId))
				.min(Comparator.comparingInt(tierId -> tiers.get(tierId).level()))
				.orElse(null);
	}

	private Candidate evaluate(
			Rule rule, ProductRules product, UserCardSnapshot card, PaymentContext payment, boolean performanceUnknown) {
		Set<Assumption> assumptions = EnumSet.noneOf(Assumption.class);
		if (performanceUnknown && rule.requiredTierId() != null) {
			assumptions.add(Assumption.PERFORMANCE_ASSUMED_LOWEST_TIER);
		}

		long benefit = rawBenefit(rule, payment.amount());
		LimitSource limitSource = LimitSource.NONE;
		boolean limited = false;

		Set<Long> visited = new HashSet<>();
		Long capId = rule.capGroupId();
		while (capId != null && visited.add(capId)) {
			Cap cap = product.caps().get(capId);
			if (cap == null) {
				break;
			}
			CapState state = card.caps().get(capId);

			if (cap.limitsCount()) {
				Integer remainingCount = state == null ? null : state.remainingCount();
				if (remainingCount == null) {
					assumptions.add(Assumption.LIMIT_ASSUMED_FULL);
					limitSource = worse(limitSource, LimitSource.ASSUMED);
				} else {
					limitSource = worse(limitSource, sourceOf(state.countSource()));
					if (remainingCount <= 0) {
						benefit = 0;
						limited = true;
					}
				}
			}
			if (cap.limitsAmount()) {
				Long remaining = state == null ? null : state.remainingAmount();
				if (remaining == null) {
					remaining = cap.capAmount();
					assumptions.add(Assumption.LIMIT_ASSUMED_FULL);
					limitSource = worse(limitSource, LimitSource.ASSUMED);
				} else {
					limitSource = worse(limitSource, sourceOf(state.amountSource()));
				}
				if (remaining < benefit) {
					benefit = Math.max(0, remaining);
					limited = true;
				}
			}
			capId = cap.parentId();
		}
		return new Candidate(rule, benefit, assumptions, limitSource, limited);
	}

	/** 한도를 적용하기 전 혜택. 1원 미만은 버린다. */
	static long rawBenefit(Rule rule, long amount) {
		BigDecimal pointValue = rule.pointKrwRatio() == null ? BigDecimal.ONE : rule.pointKrwRatio();
		BigDecimal benefit = switch (rule.type()) {
			case DISCOUNT_RATE, POINT_RATE -> {
				long eligible = rule.maxEligibleTxnAmount() == null ? amount : Math.min(amount, rule.maxEligibleTxnAmount());
				yield BigDecimal.valueOf(eligible).multiply(rule.rate());
			}
			case DISCOUNT_FIXED, POINT_FIXED -> BigDecimal.valueOf(rule.fixedAmount());
			default -> BigDecimal.ZERO;
		};
		if (rule.type() == BenefitType.POINT_RATE || rule.type() == BenefitType.POINT_FIXED) {
			benefit = benefit.multiply(pointValue);
		}
		return benefit.setScale(0, RoundingMode.DOWN).longValueExact();
	}

	private static LimitSource sourceOf(ConfirmationSource source) {
		return source == ConfirmationSource.USER_CONFIRMED ? LimitSource.USER_INPUT : LimitSource.ESTIMATED;
	}

	private static LimitSource worse(LimitSource current, LimitSource next) {
		return next.ordinal() > current.ordinal() ? next : current;
	}

	private static long requiredPerformance(Rule rule, Map<Long, Tier> tiers) {
		if (rule.requiredTierId() == null || !tiers.containsKey(rule.requiredTierId())) {
			return 0;
		}
		return tiers.get(rule.requiredTierId()).minAmount();
	}

	private static CardBenefitResult result(
			ProductRules product, UserCardSnapshot card, Status status, Long ruleId, NoBenefitReason reason) {
		return new CardBenefitResult(
				card.userCardId(), status, 0, ruleId, null, new ArrayList<>(), LimitSource.NONE, reason, 0,
				product.annualFee(), card.registrationOrder());
	}

	private record Candidate(
			Rule rule, long benefit, Set<Assumption> assumptions, LimitSource limitSource, boolean limited) {
	}
}
