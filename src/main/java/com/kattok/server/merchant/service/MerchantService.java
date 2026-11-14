package com.kattok.server.merchant.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kattok.server.merchant.dto.MerchantResponse;
import com.kattok.server.merchant.entity.Merchant;
import com.kattok.server.merchant.repository.MerchantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

	private final MerchantRepository merchantRepository;

	public List<MerchantResponse> search(String keyword) {
		List<Merchant> merchants = keyword == null || keyword.isBlank()
				? merchantRepository.findAllByOrderByName()
				: merchantRepository.findByNameContainingIgnoreCaseOrderByName(keyword);
		return merchants.stream().map(MerchantResponse::from).toList();
	}
}
