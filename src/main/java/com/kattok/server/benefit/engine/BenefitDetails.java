package com.kattok.server.benefit.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.kattok.server.benefit.engine.CardBenefitResult.LimitSource;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.entity.CapPeriod;
import com.kattok.server.card.dto.CapState;
import com.kattok.server.card.dto.UserCardSnapshot;
import com.kattok.server.card.entity.ConfirmationSource;

/**
 * 계산 결과 화면에 보여 줄 근거(계산식, 결제 후 남은 한도, 가정의 종류)를 만든다. Spring과 JPA에 의존하지 않는다.
 *
 * <p>계산 자체는 {@link BenefitCalculator}가 하고, 여기서는 적용된 규칙 하나와 그 한도 그룹을 다시 읽어 설명만 만든다.
 */
public final class BenefitDetails {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private BenefitDetails() {
	}

	/**
	 * 적용한 혜택의 계산식.
	 *
	 * @param baseAmount 혜택을 계산한 금액(원). 1회 결제 최대 금액이 있으면 그 금액까지. 정액 혜택이면 {@code null}
	 * @param ratePercent 할인·적립률(%). 정률 혜택이 아니면 {@code null}
	 * @param fixedAmount 정액 혜택 금액(원). 정액 혜택이 아니면 {@code null}
	 * @param capApplied 한도 때문에 혜택이 줄었는지
	 */
	public record Calculation(Long baseAmount, BigDecimal ratePercent, Long fixedAmount, boolean capApplied) {
	}

	/**
	 * 이 결제 후 남은 한도.
	 *
	 * @param amount 남은 한도(원). 상위 한도까지 본 가장 작은 값
	 * @param source 남은 한도를 정한 값의 출처. 하나라도 모르면 {@link LimitSource#ASSUMED}
	 */
	public record RemainingLimit(long amount, LimitSource source) {
	}

	/** 결과에 들어간 가정의 종류. 명세의 {@code assumptions.type}과 같다. */
	public enum AssumptionType {
		/** 지난달 실적을 몰라 가장 낮은 구간으로 가정 */
		PERFORMANCE_TIER,
		/** 남은 한도(원)를 몰라 전체 한도로 가정 */
		REMAINING_LIMIT,
		/** 남은 횟수를 몰라 전체 횟수로 가정 */
		REMAINING_COUNT
	}

	/** 실제 상태 변경 없이 한도 하나의 결제 전·후 예상값과 근거를 보관한다. */
	public record CapProjection(
			Long capGroupId, String description, CapPeriod capPeriod, Long capAmount, Integer capCount,
			Long remainingLimitBefore, Long remainingLimitAmount, Integer remainingCountBefore, Integer remainingCount,
			LimitSource amountSource, LimitSource countSource) {
	}

	/** 실적은 카드 단위, 한도·횟수는 한도 그룹 단위 질문이다. */
	public record AssumptionTarget(AssumptionType type, Long capGroupId) {
	}

	/** 적용 혜택의 한도부터 상위 한도까지. 순환·없는 참조는 안전하게 종료한다. */
	public static List<Long> capGroupIds(Rule rule, ProductRules product) {
		List<Long> ids = new ArrayList<>();
		Set<Long> visited = new HashSet<>();
		Long id = rule.capGroupId();
		while (id != null && visited.add(id)) {
			Cap cap = product.caps().get(id);
			if (cap == null) {
				break;
			}
			ids.add(id);
			id = cap.parentId();
		}
		return List.copyOf(ids);
	}

	/** 현재 적용된 단일 혜택을 기준으로 금액·횟수의 예상 잔여값을 각각 만든다. */
	public static List<CapProjection> remainingLimitsAfter(
			Rule rule, ProductRules product, UserCardSnapshot card, long expectedBenefit) {
		List<CapProjection> result = new ArrayList<>();
		for (Long id : capGroupIds(rule, product)) {
			Cap cap = product.caps().get(id);
			if (!cap.limitsAmount() && !cap.limitsCount()) {
				continue;
			}
			CapState state = card.caps().get(id);
			Long amount = state == null ? null : state.remainingAmount();
			Integer count = state == null ? null : state.remainingCount();
			Long beforeAmount = cap.limitsAmount() ? (amount == null ? cap.capAmount() : amount) : null;
			Integer beforeCount = cap.limitsCount() ? (count == null ? cap.capCount() : count) : null;
			LimitSource amountSource = !cap.limitsAmount() ? null : amount == null ? LimitSource.ASSUMED : sourceOf(state.amountSource());
			LimitSource countSource = !cap.limitsCount() ? null : count == null ? LimitSource.ASSUMED : sourceOf(state.countSource());
			result.add(new CapProjection(id, cap.description(), cap.capPeriod(), cap.capAmount(), cap.capCount(),
					beforeAmount, beforeAmount == null ? null : Math.max(0, beforeAmount - expectedBenefit),
					beforeCount, beforeCount == null ? null : Math.max(0, beforeCount - (expectedBenefit > 0 ? 1 : 0)),
					amountSource, countSource));
		}
		return List.copyOf(result);
	}

	/** 같은 한도에 연결된 혜택 금액만 합산하고, 같은 거래의 횟수는 한 번만 줄인다. */
	public static List<CapProjection> remainingLimitsAfter(
			CardBenefitResult result, ProductRules product, UserCardSnapshot card) {
		java.util.Map<Long, Long> deductions = new java.util.TreeMap<>();
		for (var applied : result.appliedBenefits()) {
			Rule rule = appliedRule(product, applied.ruleId());
			for (Long id : capGroupIds(rule, product)) {
				deductions.merge(id, applied.benefitAmount(), Math::addExact);
			}
		}
		List<CapProjection> projections = new ArrayList<>();
		for (var entry : deductions.entrySet()) {
			Cap cap = product.caps().get(entry.getKey());
			if (!cap.limitsAmount() && !cap.limitsCount()) continue;
			CapState state = card.caps().get(cap.id());
			Long amount = state == null ? null : state.remainingAmount();
			Integer count = state == null ? null : state.remainingCount();
			Long beforeAmount = cap.limitsAmount() ? (amount == null ? cap.capAmount() : amount) : null;
			Integer beforeCount = cap.limitsCount() ? (count == null ? cap.capCount() : count) : null;
			projections.add(new CapProjection(cap.id(), cap.description(), cap.capPeriod(), cap.capAmount(), cap.capCount(),
					beforeAmount, beforeAmount == null ? null : Math.max(0, beforeAmount - entry.getValue()),
					beforeCount, beforeCount == null ? null : Math.max(0, beforeCount - 1),
					!cap.limitsAmount() ? null : amount == null ? LimitSource.ASSUMED : sourceOf(state.amountSource()),
					!cap.limitsCount() ? null : count == null ? LimitSource.ASSUMED : sourceOf(state.countSource())));
		}
		return List.copyOf(projections);
	}

	public static List<AssumptionTarget> assumptionTargets(
			CardBenefitResult result, ProductRules product, UserCardSnapshot card) {
		Set<AssumptionTarget> targets = new HashSet<>();
		for (var applied : result.appliedBenefits()) {
			targets.addAll(assumptionTargets(result, appliedRule(product, applied.ruleId()), product, card));
		}
		return targets.stream().sorted(Comparator.comparing(AssumptionTarget::type)
				.thenComparing(AssumptionTarget::capGroupId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
	}

	public static Rule appliedRule(ProductRules product, Long ruleId) {
		return product.rules().stream().filter(rule -> rule.id().equals(ruleId)).findFirst().orElseThrow();
	}

	/** 호환 필드는 대표 혜택에 연결된 한도의 합산 후 잔여를 표시한다. */
	public static RemainingLimit remainingLimitAfter(
			CardBenefitResult result, ProductRules product, UserCardSnapshot card) {
		if (result.appliedRuleId() == null) return null;
		Set<Long> representativeCaps = new HashSet<>(capGroupIds(appliedRule(product, result.appliedRuleId()), product));
		var projections = remainingLimitsAfter(result, product, card).stream()
				.filter(p -> representativeCaps.contains(p.capGroupId()) && p.capAmount() != null).toList();
		if (projections.isEmpty()) return null;
		long amount = projections.stream().mapToLong(CapProjection::remainingLimitAmount).min().orElseThrow();
		LimitSource source = projections.stream().map(CapProjection::amountSource)
				.max(Comparator.naturalOrder()).orElse(LimitSource.NONE);
		return new RemainingLimit(amount, source);
	}

	public static List<AssumptionTarget> assumptionTargets(
			CardBenefitResult result, Rule rule, ProductRules product, UserCardSnapshot card) {
		Set<AssumptionType> types = assumptionTypes(result, rule, product, card);
		List<AssumptionTarget> targets = new ArrayList<>();
		if (types.contains(AssumptionType.PERFORMANCE_TIER)) {
			targets.add(new AssumptionTarget(AssumptionType.PERFORMANCE_TIER, null));
		}
		for (Long id : capGroupIds(rule, product)) {
			Cap cap = product.caps().get(id);
			CapState state = card.caps().get(id);
			if (types.contains(AssumptionType.REMAINING_LIMIT) && cap.limitsAmount()
					&& (state == null || state.remainingAmount() == null)) {
				targets.add(new AssumptionTarget(AssumptionType.REMAINING_LIMIT, id));
			}
			if (types.contains(AssumptionType.REMAINING_COUNT) && cap.limitsCount()
					&& (state == null || state.remainingCount() == null)) {
				targets.add(new AssumptionTarget(AssumptionType.REMAINING_COUNT, id));
			}
		}
		targets.sort(Comparator.comparing(AssumptionTarget::type)
				.thenComparing(AssumptionTarget::capGroupId, Comparator.nullsFirst(Comparator.naturalOrder())));
		return List.copyOf(targets);
	}

	private static LimitSource sourceOf(ConfirmationSource source) {
		return source == ConfirmationSource.USER_CONFIRMED ? LimitSource.USER_INPUT : LimitSource.ESTIMATED;
	}

	public static Calculation calculation(Rule rule, long amount, long expectedBenefit) {
		boolean capApplied = expectedBenefit < BenefitCalculator.rawBenefit(rule, amount);
		return switch (rule.type()) {
			case DISCOUNT_RATE, POINT_RATE -> new Calculation(
					rule.maxEligibleTxnAmount() == null ? amount : Math.min(amount, rule.maxEligibleTxnAmount()),
					rule.rate().multiply(HUNDRED).stripTrailingZeros(),
					null,
					capApplied);
			case DISCOUNT_FIXED, POINT_FIXED -> new Calculation(null, null, rule.fixedAmount(), capApplied);
			default -> new Calculation(null, null, null, capApplied);
		};
	}

	/**
	 * 적용한 규칙의 한도 그룹과 그 상위 한도 중 금액 한도가 있는 것의 결제 후 남은 한도. 금액 한도가 없으면 {@code null}.
	 * 남은 한도를 모르는 한도 그룹은 계산 엔진과 같이 전체 한도가 남은 것으로 본다.
	 */
	public static RemainingLimit remainingLimitAfter(
			Rule rule, ProductRules product, UserCardSnapshot card, long expectedBenefit) {
		Long smallest = null;
		LimitSource source = LimitSource.NONE;
		Set<Long> visited = new HashSet<>();
		Long capId = rule.capGroupId();
		while (capId != null && visited.add(capId)) {
			Cap cap = product.caps().get(capId);
			if (cap == null) {
				break;
			}
			if (cap.capAmount() != null) {
				CapState state = card.caps().get(capId);
				long remaining;
				LimitSource capSource;
				if (state == null || state.remainingAmount() == null) {
					remaining = cap.capAmount();
					capSource = LimitSource.ASSUMED;
				} else {
					remaining = state.remainingAmount();
					capSource = state.amountSource() == ConfirmationSource.USER_CONFIRMED
							? LimitSource.USER_INPUT
							: LimitSource.ESTIMATED;
				}
				smallest = smallest == null ? remaining : Math.min(smallest, remaining);
				source = capSource.ordinal() > source.ordinal() ? capSource : source;
			}
			capId = cap.parentId();
		}
		if (smallest == null) {
			return null;
		}
		return new RemainingLimit(Math.max(0, smallest - expectedBenefit), source);
	}

	/**
	 * 결과에 들어간 가정을 명세의 종류로 나눈다. 실적 가정은 계산 결과를 따르고, 한도 가정은 적용한 규칙의 한도 그룹(상위 포함)
	 * 중 모르는 값이 금액인지 횟수인지로 나눈다.
	 */
	public static Set<AssumptionType> assumptionTypes(
			CardBenefitResult result, Rule rule, ProductRules product, UserCardSnapshot card) {
		Set<AssumptionType> types = EnumSet.noneOf(AssumptionType.class);
		if (result.assumptions().contains(CardBenefitResult.Assumption.PERFORMANCE_ASSUMED_LOWEST_TIER)) {
			types.add(AssumptionType.PERFORMANCE_TIER);
		}
		if (!result.assumptions().contains(CardBenefitResult.Assumption.LIMIT_ASSUMED_FULL)) {
			return types;
		}
		Set<Long> visited = new HashSet<>();
		Long capId = rule.capGroupId();
		while (capId != null && visited.add(capId)) {
			Cap cap = product.caps().get(capId);
			if (cap == null) {
				break;
			}
			CapState state = card.caps().get(capId);
			if (cap.capAmount() != null && (state == null || state.remainingAmount() == null)) {
				types.add(AssumptionType.REMAINING_LIMIT);
			}
			if (cap.capCount() != null && (state == null || state.remainingCount() == null)) {
				types.add(AssumptionType.REMAINING_COUNT);
			}
			capId = cap.parentId();
		}
		return types;
	}
}
