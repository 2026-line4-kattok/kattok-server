package com.kattok.server.merchant.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.global.ApiException;
import com.kattok.server.merchant.dto.MerchantResponse;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.entity.MerchantCategory;
import com.kattok.server.merchant.repository.MerchantCategoryRepository;
import com.kattok.server.merchant.repository.MerchantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

	private final MerchantRepository merchantRepository;
	private final MerchantCategoryRepository merchantCategoryRepository;

	public List<MerchantResponse> search(String keyword) {
		List<Merchant> merchants = keyword == null || keyword.isBlank()
				? merchantRepository.findAllByOrderByName()
				: merchantRepository.findByNameContainingIgnoreCaseOrderByName(keyword);
		return merchants.stream().map(MerchantResponse::from).toList();
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
