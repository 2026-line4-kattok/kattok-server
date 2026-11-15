package com.kattok.server.benefit.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.engine.ProductRules.Tier;
import com.kattok.server.benefit.engine.RuleConditions;
import com.kattok.server.benefit.entity.BenefitCapGroup;
import com.kattok.server.benefit.entity.BenefitRule;
import com.kattok.server.benefit.entity.VerificationStatus;
import com.kattok.server.benefit.repository.BenefitCapGroupRepository;
import com.kattok.server.benefit.repository.BenefitRuleRepository;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.DataStatus;
import com.kattok.server.card.entity.PerformanceType;
import com.kattok.server.card.repository.CardProductRepository;

import lombok.RequiredArgsConstructor;

/** 계산 엔진이 쓰는 카드 상품 정보(혜택 규칙, 실적 구간, 한도 그룹)를 DB에서 읽어 엔진 모델로 바꾼다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductRulesReader {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final CardProductRepository cardProductRepository;
	private final BenefitRuleRepository benefitRuleRepository;
	private final BenefitCapGroupRepository benefitCapGroupRepository;

	/** 결제처 또는 업종을 대상으로 하는, 오늘(KST) 유효한 검증된 혜택 규칙이 하나라도 있는지. merchant 도메인의 지원 여부 판단에 쓴다. */
	public boolean isSupported(Long merchantId, Long categoryId) {
		return benefitRuleRepository.existsSupportedRule(merchantId, categoryId, LocalDate.now(KST));
	}

	/**
	 * @param productIds 카드 상품 id
	 * @param date 규칙 유효 기간을 판단할 날짜(한국 시간)
	 * @return 상품 id별 계산용 정보. 없는 상품 id는 결과에 없다.
	 */
	public Map<Long, ProductRules> read(Collection<Long> productIds, LocalDate date) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<BenefitRule>> rulesByProduct = benefitRuleRepository.findCalculableRules(productIds, date)
				.stream()
				.collect(Collectors.groupingBy(rule -> rule.getProduct().getId()));
		Map<Long, List<BenefitCapGroup>> capsByProduct = benefitCapGroupRepository.findByProductIdIn(productIds)
				.stream()
				.collect(Collectors.groupingBy(cap -> cap.getProduct().getId()));

		Map<Long, ProductRules> result = new HashMap<>();
		for (CardProduct product : cardProductRepository.findAllById(productIds)) {
			List<BenefitRule> rules = rulesByProduct.getOrDefault(product.getId(), List.of());
			List<BenefitCapGroup> caps = capsByProduct.getOrDefault(product.getId(), List.of());
			result.put(product.getId(), new ProductRules(
					product.getId(),
					isCalculable(product),
					product.getAnnualFee(),
					rules.stream().map(ProductRulesReader::toRule).toList(),
					tiersOf(rules),
					caps.stream().collect(Collectors.toMap(BenefitCapGroup::getId, ProductRulesReader::toCap))));
		}
		return result;
	}

	/**
	 * 상품의 오늘 유효한 혜택 규칙 전체(미검증 포함)와 각 규칙이 추천 계산에 실제로 쓰이는지. 카드 상세(3-2) 혜택 목록에 쓴다.
	 * 추천 계산은 비교 대상 상품의 검증된 규칙만 쓰므로, 그 조건을 그대로 따른다.
	 */
	public List<RuleUsage> ruleUsages(Long productId, LocalDate date) {
		CardProduct product = cardProductRepository.findById(productId).orElse(null);
		if (product == null) {
			return List.of();
		}
		boolean comparable = isCalculable(product);
		return benefitRuleRepository.findValidRules(productId, date).stream()
				.map(rule -> new RuleUsage(rule.getId(), rule.getDisplayText(), rule.getCapGroup().getId(),
						comparable && rule.getVerificationStatus() == VerificationStatus.VERIFIED))
				.toList();
	}

	public record RuleUsage(Long ruleId, String displayText, Long capGroupId, boolean usedInRecommendation) {
	}

	/**
	 * 추천 비교 대상인지. 일부 혜택만 검증된(PARTIAL) 카드도 검증된 규칙으로 비교한다. 혜택 미검증(UNVERIFIED) 카드와 실적 구간
	 * 정보를 확인 중인(NEEDS_CHECK) 카드는 비교에서 뺀다.
	 */
	private static boolean isCalculable(CardProduct product) {
		DataStatus status = product.getDataStatus();
		return (status == DataStatus.CALCULABLE || status == DataStatus.PARTIAL)
				&& product.getPerformanceType() != PerformanceType.NEEDS_CHECK;
	}

	private static Rule toRule(BenefitRule rule) {
		return new Rule(
				rule.getId(),
				rule.getMerchant() == null ? null : rule.getMerchant().getId(),
				rule.getCategory() == null ? null : rule.getCategory().getId(),
				rule.getRequiredTier() == null ? null : rule.getRequiredTier().getId(),
				rule.getCapGroup().getId(),
				rule.getBenefitType(),
				rule.getRate(),
				rule.getAmount(),
				rule.getPointKrwRatio(),
				rule.getMinPaymentAmount(),
				rule.getMaxEligibleTxnAmount(),
				RuleConditions.from(rule.getConditions()),
				rule.getDisplayText());
	}

	private static Map<Long, Tier> tiersOf(List<BenefitRule> rules) {
		Map<Long, Tier> tiers = new HashMap<>();
		for (BenefitRule rule : rules) {
			CardPerformanceTier tier = rule.getRequiredTier();
			if (tier != null) {
				tiers.putIfAbsent(tier.getId(), new Tier(tier.getId(), tier.getLevel(), tier.getMinAmount()));
			}
		}
		return tiers;
	}

	private static Cap toCap(BenefitCapGroup cap) {
		return new Cap(
				cap.getId(),
				cap.getCapAmount(),
				cap.getCapCount(),
				cap.getParent() == null ? null : cap.getParent().getId(),
				cap.getDescription(),
				cap.getCapPeriod());
	}
}
