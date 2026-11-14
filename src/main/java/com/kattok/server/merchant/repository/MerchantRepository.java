package com.kattok.server.merchant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.merchant.entity.Merchant;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

	List<Merchant> findAllByOrderByName();

	List<Merchant> findByNameContainingIgnoreCaseOrderByName(String keyword);
}
