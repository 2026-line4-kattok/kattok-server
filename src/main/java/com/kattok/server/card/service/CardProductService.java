package com.kattok.server.card.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.engine.ProductRules;
import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.card.dto.CardProductDetailResponse;
import com.kattok.server.card.dto.CardProductDetailResponse.BenefitSummary;
import com.kattok.server.card.dto.CardProductDetailResponse.PerformanceTierDetail;
import com.kattok.server.card.dto.CardProductResponse;
import com.kattok.server.card.entity.CardPerformanceTier;
import com.kattok.server.card.entity.CardProduct;
import com.kattok.server.card.entity.CardType;
import com.kattok.server.card.entity.DataStatus;
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

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final CardProductRepository cardProductRepository;
	private final CardPerformanceTierRepository cardPerformanceTierRepository;
	private final UserCardRepository userCardRepository;
	private final CurrentUserProvider currentUserProvider;
	private final ProductRulesReader productRulesReader;

	/**
	 * 카드 등록 화면의 상품 검색. 혜택을 계산할 수 없는 카드(UNVERIFIED)는 등록해도 추천에서 빠지기만 하므로 목록에 보이지 않는다.
	 * 혜택이 모두 지원 업종 밖이거나, 간편결제 전용이거나, 기본값 없는 선택형이라 계산할 혜택이 없는 카드들이다.
	 */
	public List<CardProductResponse> search(Long issuerId, String keyword, CardType cardType) {
		Set<Long> ownedProductIds = ownedProductIds();

		List<CardProduct> products = cardProductRepository.search(
				issuerId, blankToNull(keyword), cardType == null ? null : cardType.name());

		return products.stream()
				.filter(product -> product.getDataStatus() != DataStatus.UNVERIFIED)
				.map(product -> CardProductResponse.of(product, ownedProductIds.contains(product.getId())))
				.toList();
	}

	/** 카드 등록 중 실적 구간을 고를 때 쓰는 상세 화면. 구간마다 그 구간에서 받는 혜택을 묶어서 보여준다. */
	public CardProductDetailResponse getDetail(Long productId) {
		CardProduct product = cardProductRepository.findById(productId)
				.orElseThrow(() -> ApiException.notFound("CARD_PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다."));

		List<CardPerformanceTier> tiers = cardPerformanceTierRepository.findByProductIdOrderByLevelAsc(productId);
		ProductRules rules = productRulesReader.read(Set.of(productId), LocalDate.now(KST)).get(productId);
		List<BenefitSummary> alwaysOnBenefits = TierBenefits.alwaysOnBenefits(rules);
		List<PerformanceTierDetail> performanceTiers =
				TierBenefits.tierOptions(product.getPerformanceType(), tiers, rules);

		return new CardProductDetailResponse(
				product.getId(),
				product.getName(),
				new CardProductResponse.IssuerSummary(product.getIssuer().getId(), product.getIssuer().getName()),
				product.getCardType(),
				product.getAnnualFee(),
				product.getDataStatus(),
				product.getPerformanceType(),
				ownedProductIds().contains(productId),
				alwaysOnBenefits,
				performanceTiers);
	}

	private Set<Long> ownedProductIds() {
		return userCardRepository.findActiveProductIdsByUserId(currentUserProvider.get().getId());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
