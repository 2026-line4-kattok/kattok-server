package com.kattok.server.merchant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.merchant.entity.MerchantCategory;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {
}
