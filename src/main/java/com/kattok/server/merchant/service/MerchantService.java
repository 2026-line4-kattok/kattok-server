package com.kattok.server.merchant.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.global.ApiException;
import com.kattok.server.merchant.dto.MerchantResponse;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;
import com.kattok.server.merchant.repository.MerchantCategoryRepository;
import com.kattok.server.merchant.repository.MerchantRepository;
import com.kattok.server.recommendation.service.RecentMerchantReader;
import com.kattok.server.user.service.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

	/** 결제처 검색 화면의 "최근 결제한 곳" 칩 개수(기획 확정값). */
	private static final int RECENT_MERCHANT_LIMIT = 3;

	private final MerchantRepository merchantRepository;
	private final MerchantCategoryRepository merchantCategoryRepository;
	private final RecentMerchantReader recentMerchantReader;
	private final CurrentUserProvider currentUserProvider;

	public List<MerchantResponse> search(String keyword) {
		List<Merchant> merchants = keyword == null || keyword.isBlank()
				? merchantRepository.findAllByOrderByName()
				: merchantRepository.findByNameContainingIgnoreCaseOrderByName(keyword);
		return merchants.stream().map(MerchantResponse::from).toList();
	}

	/** 추천 입력 화면에서 다시 검색하지 않고 바로 고를 수 있게, 최근 결제한 곳을 최근 순으로 최대 3개 준다. */
	public List<MerchantResponse> recent() {
		Long userId = currentUserProvider.get().getId();
		List<Long> merchantIds = recentMerchantReader.recentMerchantIds(userId, RECENT_MERCHANT_LIMIT);
		if (merchantIds.isEmpty()) {
			return List.of();
		}

		Map<Long, Merchant> merchantsById = merchantRepository.findAllById(merchantIds).stream()
				.collect(Collectors.toMap(Merchant::getId, Function.identity()));
		return merchantIds.stream()
				.map(merchantsById::get)
				.filter(Objects::nonNull)
				.map(MerchantResponse::from)
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
