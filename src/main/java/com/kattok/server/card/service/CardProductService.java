package com.kattok.server.card.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.engine.ProductRules.Cap;
import com.kattok.server.benefit.engine.ProductRules.Rule;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.dto.CardProductDetailResponse;
import com.kattok.server.card.dto.CardProductDetailResponse.BenefitSummary;
import com.kattok.server.card.dto.CardProductDetailResponse.PerformanceTierDetail;
import com.kattok.server.card.dto.CardProductResponse;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.repository.CardPerformanceTierRepository;
import com.kattok.server.card.repository.CardProductRepository;
import com.kattok.server.card.repository.UserCardRepository;
import com.kattok.server.global.ApiException;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardProductService {

	private final CardProductRepository cardProductRepository;
	private final CardPerformanceTierRepository cardPerformanceTierRepository;
	private final UserCardRepository userCardRepository;
	private final CurrentUserProvider currentUserProvider;
	private final ProductRulesReader productRulesReader;

	public List<CardProductResponse> search(Long issuerId, String keyword, CardType cardType) {
		Set<Long> ownedProductIds = ownedProductIds();

		List<CardProduct> products = cardProductRepository.search(
				issuerId, blankToNull(keyword), cardType == null ? null : cardType.name());

		return products.stream()
				.map(product -> CardProductResponse.of(product, ownedProductIds.contains(product.getId())))
				.toList();
	}

	/** 카드 등록 중 실적 구간을 고를 때 쓰는 상세 화면. 구간마다 그 구간에서 받는 혜택을 묶어서 보여준다. */
	public CardProductDetailResponse getDetail(Long productId) {
		CardProduct product = cardProductRepository.findById(productId)
				.orElseThrow(() -> ApiException.notFound("CARD_PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다."));

		List<CardPerformanceTier> tiers = cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(productId);
		ProductRules rules = productRulesReader.read(Set.of(productId), LocalDate.now()).get(productId);
		List<Rule> allRules = rules == null ? List.of() : rules.rules();
		Map<Long, Cap> caps = rules == null ? Map.of() : rules.caps();

		List<BenefitSummary> alwaysOnBenefits = allRules.stream()
				.filter(rule -> rule.requiredTierId() == null)
				.map(rule -> toBenefitSummary(rule, caps))
				.toList();

		List<PerformanceTierDetail> performanceTiers = tiers.stream()
				.map(tier -> new PerformanceTierDetail(
						tier.getId(),
						tier.getLevel(),
						tier.getMinAmount(),
						tier.getLabel(),
						allRules.stream()
								.filter(rule -> tier.getId().equals(rule.requiredTierId()))
								.map(rule -> toBenefitSummary(rule, caps))
								.toList()))
				.toList();

		return new CardProductDetailResponse(
				product.getId(),
				product.getName(),
				new CardProductResponse.IssuerSummary(product.getIssuer().getId(), product.getIssuer().getName()),
				product.getCardType(),
				product.getAnnualFee(),
				product.getDataStatus(),
				ownedProductIds().contains(productId),
				alwaysOnBenefits,
				performanceTiers);
	}

	private Set<Long> ownedProductIds() {
		return userCardRepository.findActiveProductIdsByUserId(currentUserProvider.get().getId());
	}

	private static BenefitSummary toBenefitSummary(Rule rule, Map<Long, Cap> caps) {
		Cap cap = caps.get(rule.capGroupId());
		return new BenefitSummary(
				rule.displayText(), cap == null ? null : cap.capAmount(), cap == null ? null : cap.capCount());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
