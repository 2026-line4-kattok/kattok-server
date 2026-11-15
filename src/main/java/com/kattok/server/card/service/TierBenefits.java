package com.kattok.server.card.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.card.dto.CardProductDetailResponse.BenefitSummary;
import com.kattok.server.card.dto.CardProductDetailResponse.PerformanceTierDetail;
import com.kattok.server.card.dto.PerformanceStatus;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.PerformanceType;

/** 상품 상세(2-3)·보유 카드 상세(3-2)·카드 상태(3-3)가 같은 기준으로 실적 선택지와 적용 구간을 고르도록 모은 규칙. */
final class TierBenefits {

	private TierBenefits() {
	}

	static boolean tracksPerformance(PerformanceType type) {
		return type == PerformanceType.SINGLE || type == PerformanceType.MULTIPLE;
	}

	/** 실적 선택지. 실적 조건이 없거나(NONE) 구간 정보를 확인 중인(NEEDS_CHECK) 카드는 고를 구간이 없다. */
	static List<PerformanceTierDetail> tierOptions(
			PerformanceType type, List<CardPerformanceTier> tiers, ProductRules rules) {
		if (!tracksPerformance(type)) {
			return List.of();
		}
		List<Rule> allRules = rules == null ? List.of() : rules.rules();
		Map<Long, Cap> caps = rules == null ? Map.of() : rules.caps();
		return tiers.stream()
				.map(tier -> new PerformanceTierDetail(tier.getId(), tier.getLevel(), tier.getMinAmount(), tier.getLabel(),
						allRules.stream()
								.filter(rule -> tier.getId().equals(rule.requiredTierId()))
								.map(rule -> summary(rule, caps))
								.toList()))
				.toList();
	}

	static List<BenefitSummary> alwaysOnBenefits(ProductRules rules) {
		if (rules == null) {
			return List.of();
		}
		return rules.rules().stream()
				.filter(rule -> rule.requiredTierId() == null)
				.map(rule -> summary(rule, rules.caps()))
				.toList();
	}

	/**
	 * 실적 무관 혜택에 더해 적용할 실적 구간. 확인한 구간은 그대로, 실적을 모르면 추천 계산과 같은 "혜택이 있는 가장
	 * 낮은 구간"을 가정한다. 미달(BELOW)·실적 조건 없음(NONE)·구간 확인 필요(NEEDS_CHECK)는 구간 혜택을 적용하지 않는다.
	 */
	static Long effectiveTierId(
			PerformanceType type, PerformanceStatus status, Long confirmedTierId, ProductRules rules) {
		if (!tracksPerformance(type) || rules == null) {
			return null;
		}
		return switch (status) {
			case CONFIRMED -> confirmedTierId;
			case BELOW -> null;
			case UNKNOWN -> rules.rules().stream()
					.map(Rule::requiredTierId)
					.filter(tierId -> tierId != null && rules.tiers().containsKey(tierId))
					.min(Comparator.comparingInt(tierId -> rules.tiers().get(tierId).level()))
					.orElse(null);
		};
	}

	private static BenefitSummary summary(Rule rule, Map<Long, Cap> caps) {
		Cap cap = caps.get(rule.capGroupId());
		return new BenefitSummary(rule.displayText(), cap == null ? null : cap.capAmount(), cap == null ? null : cap.capCount());
	}
}
