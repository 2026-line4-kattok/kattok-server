package com.kattok.server.merchant.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.merchant.entity.MerchantCategory;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

	List<MerchantCategory> findByCodeIn(Collection<String> codes);
}
