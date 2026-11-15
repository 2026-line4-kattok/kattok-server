package com.kattok.server.merchant.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.benefit.service.ProductRulesReader;
import com.kattok.server.global.ApiException;
import com.kattok.server.merchant.dto.MerchantResponse;
import com.kattok.server.merchant.dto.MerchantCategoryResponse;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;
import com.kattok.server.merchant.repository.MerchantCategoryRepository;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.payment.service.RecentMerchantReader;
import com.kattok.server.merchant.dto.RecentMerchantResponse;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

	/** 결제처 검색 화면의 "최근 결제한 곳" 칩 개수(기획 확정값). */
	private static final int RECENT_MERCHANT_LIMIT = 3;

	/** 명세의 업종 칩 표시 순서. DB ID·이름순에 의존하지 않는다. */
	private static final List<String> CATEGORY_DISPLAY_ORDER = List.of(
			"CONVENIENCE_STORE", "CAFE", "RESTAURANT", "MART",
			"ONLINE_SHOPPING", "PHARMACY", "TAXI", "DEPARTMENT_STORE");

	private final MerchantRepository merchantRepository;
	private final MerchantCategoryRepository merchantCategoryRepository;
	private final RecentMerchantReader recentMerchantReader;
	private final CurrentUserProvider currentUserProvider;
	private final ProductRulesReader productRulesReader;

	public List<MerchantCategoryResponse> categories() {
		Map<String, MerchantCategory> categoriesByCode = merchantCategoryRepository
				.findByCodeIn(CATEGORY_DISPLAY_ORDER).stream()
				.collect(Collectors.toMap(MerchantCategory::getCode, Function.identity()));
		return CATEGORY_DISPLAY_ORDER.stream()
				.map(categoriesByCode::get)
				.filter(Objects::nonNull)
				.map(category -> new MerchantCategoryResponse(category.getId(),
						category.getCode().equals("CAFE") ? "카페" : category.getName()))
				.toList();
	}

	public List<MerchantResponse> search(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}
		String escaped = keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return merchantRepository.searchByNameOrAlias("%" + escaped + "%").stream()
				.map(merchant -> MerchantResponse.of(merchant, isSupported(merchant)))
				.toList();
	}

	private boolean isSupported(Merchant merchant) {
		return productRulesReader.isSupported(merchant.getId(), merchant.getCategory().getId());
	}

	/** 추천 입력 화면에서 다시 검색하지 않고 바로 고를 수 있게, 최근 결제한 곳을 최근 순으로 최대 3개 준다. */
	public List<RecentMerchantResponse> recent() {
		Long userId = currentUserProvider.get().getId();
		var recent = recentMerchantReader.recentMerchants(userId, RECENT_MERCHANT_LIMIT);
		if (recent.isEmpty()) {
			return List.of();
		}

		Map<Long, Merchant> merchantsById = merchantRepository.findAllById(recent.stream().map(row -> row.merchantId()).toList()).stream()
				.collect(Collectors.toMap(Merchant::getId, Function.identity()));
		return recent.stream()
				.filter(row -> merchantsById.containsKey(row.merchantId()))
				.map(row -> RecentMerchantResponse.from(merchantsById.get(row.merchantId()), row.lastRecommendedAt()))
				.toList();
	}

	/** 추천 등 다른 도메인에서 결제처를 참조할 때 쓴다. */
	public Merchant getMerchant(Long merchantId) {
		return merchantRepository.findById(merchantId)
				.orElseThrow(() -> ApiException.notFound("MERCHANT_NOT_FOUND", "존재하지 않는 결제처입니다."));
	}

	/** 추천 등 다른 도메인에서 업종을 참조할 때 쓴다. */
	public MerchantCategory getCategory(Long categoryId) {
		return merchantCategoryRepository.findById(categoryId)
				.orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "존재하지 않는 업종입니다."));
	}
}
