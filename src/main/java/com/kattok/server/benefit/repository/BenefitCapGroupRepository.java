package com.kattok.server.benefit.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kattok.server.benefit.entity.BenefitCapGroup;

public interface BenefitCapGroupRepository extends JpaRepository<BenefitCapGroup, Long> {

	List<BenefitCapGroup> findByProductIdIn(Collection<Long> productIds);
}
